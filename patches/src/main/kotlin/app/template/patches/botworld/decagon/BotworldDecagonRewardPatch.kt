package app.template.patches.botworld.decagon

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.template.patches.shared.Constants.BOTWORLD_COMPATIBILITY
import app.template.patches.shared.replaceBody
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * Completes Botworld 1.36.2's decagon AdMob rewarded flow locally.
 *
 * Second Unity bridge (verified from the APK DEX, classes3.dex):
 * Lcom/google/unity/ads/decagon/UnityRewardedAd.show()V null-checks the
 * `ad` field, then posts lambda$show$0 to the UI thread, which calls the
 * decagon SDK RewardedAd.show(activity, UnityRewardedAd$3 listener).
 * The $3 listener forwards to the game via
 * decagon/UnityRewardedAdCallback.onUserEarnedReward(String, float).
 * Same show-then-callback shape as the classic bridge, different package
 * and SDK types. Patching only the classic bridge leaves decagon
 * placements (e.g. chests served after the SDK flip) untouched.
 *
 * The patch replaces show() with a direct callback invocation and is
 * null-safe: the callback field lives on the UnityAdBase superclass as
 * Object, so read it from there and check-cast to the callback iface.
 */
private const val DECAGON_BRIDGE = "Lcom/google/unity/ads/decagon/UnityRewardedAd;"
private const val DECAGON_BASE = "Lcom/google/unity/ads/decagon/UnityAdBase;"
private const val DECAGON_CALLBACK = "Lcom/google/unity/ads/decagon/UnityRewardedAdCallback;"
private const val SHOW_GUARD = "Tried to show rewarded ad before it was ready. Please call load first and wait for a successful onAdLoaded callback."

private object DecagonShowFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = emptyList(),
    definingClass = DECAGON_BRIDGE,
    name = "show",
    filters = listOf(string(SHOW_GUARD)),
)

// Load entry mirrors the classic bridge: without a local load completion
// the game never reaches show(). Fires onRewardedAdLoaded() on the
// decagon game callback (field lives on UnityAdBase as Object).
private object DecagonLoadFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = listOf(
        "Lcom/google/android/libraries/ads/mobile/sdk/common/AdRequest;",
    ),
    definingClass = DECAGON_BRIDGE,
    name = "load",
)

private fun Method.calls(owner: String, name: String): Boolean =
    implementation?.instructions?.any {
        val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
        ref?.definingClass == owner && ref.name == name
    } == true

private fun Method.hasString(value: String): Boolean =
    implementation?.instructions?.any {
        ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == value
    } == true

@Suppress("unused")
val botworldDecagonRewardPatch = bytecodePatch(
    name = "Skip decagon rewarded ads",
    description = "Completes the decagon AdMob rewarded flow locally so the requested " +
        "reward is granted without loading or playing an ad. Pairs with Skip " +
        "AdMob rewarded ads; IAP purchases still require real payment.",
    default = true,
) {
    compatibleWith(BOTWORLD_COMPATIBILITY)

    execute {
        val show = DecagonShowFingerprint.method
        val load = DecagonLoadFingerprint.method

        if (!load.calls("Landroid/app/Activity;", "runOnUiThread")) {
            throw PatchException(
                "Botworld: decagon load path changed; use a clean Botworld 1.36.2 (171310).",
            )
        }
        if (!show.calls("Landroid/app/Activity;", "runOnUiThread")) {
            throw PatchException(
                "Botworld: decagon show path changed; use a clean Botworld 1.36.2 (171310).",
            )
        }
        if (!show.hasString(SHOW_GUARD)) {
            throw PatchException(
                "Botworld: decagon show guard string gone; use a clean Botworld 1.36.2 (171310).",
            )
        }
        val callbackReward = try {
            mutableClassDefBy(DECAGON_CALLBACK).methods.singleOrNull {
                it.name == "onUserEarnedReward" &&
                    it.parameterTypes == listOf("Ljava/lang/String;", "F")
            }
        } catch (e: Exception) {
            null
        } ?: throw PatchException(
            "Botworld: decagon reward callback gone; use a clean Botworld 1.36.2 (171310).",
        )

        // Local load completion: fire onRewardedAdLoaded() on the game
        // callback so the game's own flag flips and it proceeds to show().
        // NOTE: descriptors below are literals to avoid Kotlin
        // $-interpolation compile failures.
        load.replaceBody(
            """
                iget-object v0, p0, Lcom/google/unity/ads/decagon/UnityAdBase;->callback:Ljava/lang/Object;
                if-eqz v0, :done
                check-cast v0, Lcom/google/unity/ads/decagon/UnityRewardedAdCallback;
                invoke-interface {v0}, Lcom/google/unity/ads/decagon/UnityRewardedAdCallback;->onRewardedAdLoaded()V
                :done
                return-void
            """.trimIndent(),
        )
        // Local completion: fire the game callback directly. The callback
        // field is declared on the UnityAdBase superclass as Object.
        // NOTE: descriptors below are literals to avoid Kotlin
        // $-interpolation compile failures.
        show.replaceBody(
            """
                iget-object v0, p0, Lcom/google/unity/ads/decagon/UnityAdBase;->callback:Ljava/lang/Object;
                if-eqz v0, :done
                check-cast v0, Lcom/google/unity/ads/decagon/UnityRewardedAdCallback;
                const-string v1, ""
                const/high16 v2, 0x3f800000
                invoke-interface {v0, v1, v2}, Lcom/google/unity/ads/decagon/UnityRewardedAdCallback;->onUserEarnedReward(Ljava/lang/String;F)V
                :done
                return-void
            """.trimIndent(),
        )
        if (show.implementation?.instructions?.none {
            (it.opcode == Opcode.INVOKE_INTERFACE) &&
                ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == "onUserEarnedReward"
        } == true) {
            throw PatchException("Botworld: decagon reward body did not apply.")
        }
        if (load.implementation?.instructions?.none {
            (it.opcode == Opcode.INVOKE_INTERFACE) &&
                ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == "onRewardedAdLoaded"
        } == true) {
            throw PatchException("Botworld: decagon load body did not apply.")
        }
    }
}

package app.template.patches.botworld.admob

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
 * Completes Botworld 1.36.2's AdMob rewarded flow locally: the Unity
 * bridge callback fires without loading or showing an ad.
 *
 * Chain (verified from the APK DEX, classes3.dex):
 * - Lcom/google/unity/ads/UnityRewardedAd.show()V checks the
 *   rewardedAd field, then posts lambda$show$0 to the UI thread, which
 *   calls GMS RewardedAd.show(activity, UnityRewardedAd$4 listener).
 * - The $4 listener forwards onUserEarnedReward to the game via
 *   UnityRewardedAdCallback.onUserEarnedReward(String, float) on a worker
 *   thread; the game grants its fixed per-placement reward in il2cpp.
 * - When AdMob has no fill (offline / blocked WebView / JavascriptEngine
 *   failure), rewardedAd stays null and the game shows its own
 *   "Ad Unavailable" dialog instead of calling show().
 *
 * The patch replaces show() with a direct callback invocation and forces
 * isAdAvailable(String)Z to true, so the game always takes the reward
 * path. No ad-network impression, revenue, click or postback is
 * synthesized; IAP purchases still require real payment.
 *
 * Guards assert the null-check + UI-thread dispatch + static availability
 * delegation shapes are still present so a reshaped bridge fails loudly
 * instead of mis-patching.
 */
private const val BRIDGE = "Lcom/google/unity/ads/UnityRewardedAd;"
private const val CALLBACK = "Lcom/google/unity/ads/UnityRewardedAdCallback;"
private const val GMS_REWARDED = "Lcom/google/android/gms/ads/rewarded/RewardedAd;"
private const val STRING = "Ljava/lang/String;"

private object AdMobShowFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = emptyList(),
    definingClass = BRIDGE,
    name = "show",
    filters = listOf(
        string("Tried to show rewarded ad before it was ready. This should in theory never happen. If it does, please contact the plugin owners."),
    ),
)

private object AdMobAvailableFingerprint : Fingerprint(
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = listOf(STRING),
    definingClass = BRIDGE,
    name = "isAdAvailable",
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
val botworldAdMobRewardPatch = bytecodePatch(
    name = "Skip AdMob rewarded ads",
    description = "Completes the AdMob rewarded flow locally so the requested " +
        "reward is granted without loading or playing an ad. Pairs with Skip " +
        "rewarded ads (AppLovin MAX); IAP purchases still require real payment.",
    default = true,
) {
    compatibleWith(BOTWORLD_COMPATIBILITY)

    execute {
        val show = AdMobShowFingerprint.method
        val available = AdMobAvailableFingerprint.method

        // show() must still null-check the GMS rewarded ad and dispatch to
        // the UI thread; a body that no longer does means the bridge changed.
        if (!show.calls("Landroid/app/Activity;", "runOnUiThread")) {
            throw PatchException(
                "Botworld: AdMob show path changed; use a clean Botworld 1.36.2 (171310).",
            )
        }
        if (!show.hasString("Tried to show rewarded ad before it was ready. This should in theory never happen. If it does, please contact the plugin owners.")) {
            throw PatchException(
                "Botworld: AdMob show guard string gone; use a clean Botworld 1.36.2 (171310).",
            )
        }
        // Availability probe must still delegate to the static GMS check.
        if (!available.calls(GMS_REWARDED, "isAdAvailable")) {
            throw PatchException(
                "Botworld: AdMob availability probe changed; use a clean Botworld 1.36.2 (171310).",
            )
        }
        // The game callback shape must still be (String, float).
        val callbackReward = try {
            mutableClassDefBy(CALLBACK).methods.singleOrNull {
                it.name == "onUserEarnedReward" &&
                    it.parameterTypes == listOf(STRING, "F")
            }
        } catch (e: Exception) {
            null
        } ?: throw PatchException(
            "Botworld: reward callback gone; use a clean Botworld 1.36.2 (171310).",
        )

        // Game always sees an available AdMob rewarded slot.
        available.replaceBody("const/4 v0, 0x1\nreturn v0")
        // Local completion: fire the game callback directly. Null-safe: if
        // the game never set a callback, return without crashing.
        // NOTE: the reward descriptor below is a literal
        // (Ljava/lang/String;F)V to avoid Kotlin $-interpolation issues.
        show.replaceBody(
            """
                iget-object v0, p0, Lcom/google/unity/ads/UnityRewardedAd;->callback:Lcom/google/unity/ads/UnityRewardedAdCallback;
                if-eqz v0, :done
                const-string v1, ""
                const/high16 v2, 0x3f800000
                invoke-interface {v0, v1, v2}, Lcom/google/unity/ads/UnityRewardedAdCallback;->onUserEarnedReward(Ljava/lang/String;F)V
                :done
                return-void
            """.trimIndent(),
        )
        if (show.implementation?.instructions?.none {
            (it.opcode == Opcode.INVOKE_INTERFACE) &&
                ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == "onUserEarnedReward"
        } == true) {
            throw PatchException("Botworld: AdMob reward body did not apply.")
        }
    }
}

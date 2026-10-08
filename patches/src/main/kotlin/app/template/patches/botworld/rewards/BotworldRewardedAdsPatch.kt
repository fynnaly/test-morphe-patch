package app.template.patches.botworld.rewards

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.BOTWORLD_COMPATIBILITY
import app.template.patches.shared.replaceBody
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

/**
 * Completes Botworld 1.36.2's AppLovin MAX rewarded-video flow locally:
 * the requested reward callback fires without playing an ad.
 *
 * Chain (verified from the APK DEX, classes.dex):
 * - Lcom/applovin/mediation/ads/MaxRewardedAd.showAd(String placement,
 *   String customData)V delegates to MaxFullscreenAdImpl.showAd, which
 *   queues the ad via a(state, Runnable).
 * - On completion the SDK calls MaxRewardedAdImpl$b.onUserRewarded(ad,
 *   reward), which forwards to the game listener via q2.a (the game grants
 *   its fixed per-placement reward in il2cpp; the MaxReward amount is not
 *   the grant itself).
 * - The patch replaces the public showAd body with a local completion:
 *   synthesize a u3 MaxAd + MaxRewardImpl.createDefault(), invoke the
 *   impl's own wrapper b.onUserRewarded + b.onAdHidden. No ad-network
 *   impression, revenue, click or postback is synthesized.
 *
 * Guards assert the delegation + forward shapes are still present so a
 * reshaped SDK fails loudly instead of mis-patching.
 */
private const val PUBLIC = "Lcom/applovin/mediation/ads/MaxRewardedAd;"
private const val IMPL = "Lcom/applovin/impl/mediation/ads/MaxFullscreenAdImpl;"
private const val REWARDED_IMPL = "Lcom/applovin/impl/mediation/ads/MaxRewardedAdImpl;"
private const val WRAPPER = "Lcom/applovin/impl/mediation/ads/MaxRewardedAdImpl\$b;"
private const val BASE_WRAPPER = "Lcom/applovin/impl/mediation/ads/MaxFullscreenAdImpl\$b;"
private const val SYNTH_AD = "Lcom/applovin/impl/u3;"
private const val REWARD_FACTORY = "Lcom/applovin/impl/mediation/MaxRewardImpl;"
private const val REWARDED_FORMAT = "Lcom/applovin/mediation/MaxAdFormat;"
private const val STRING = "Ljava/lang/String;"

private object ShowAdFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = listOf(STRING, STRING),
    definingClass = PUBLIC,
    name = "showAd",
)

private object IsReadyFingerprint : Fingerprint(
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = emptyList(),
    definingClass = PUBLIC,
    name = "isReady",
)

private object RewardForwardFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = listOf(
        "Lcom/applovin/mediation/MaxAd;",
        "Lcom/applovin/mediation/MaxReward;",
    ),
    definingClass = WRAPPER,
    name = "onUserRewarded",
)

// Convergence point: every public MaxRewardedAd.showAd overload delegates
// into one of these two MaxFullscreenAdImpl.showAd methods. Patching here
// catches all call paths (including the Activity/Context variants Unity
// calls via JNI), instead of only the (placement, customData) overload.
private object ImplShowAdFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        STRING,
        STRING,
        "Landroid/app/Activity;",
    ),
    definingClass = IMPL,
    name = "showAd",
)

private object ImplShowAdContainerFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        STRING,
        STRING,
        "Landroid/view/ViewGroup;",
        "Landroidx/lifecycle/Lifecycle;",
        "Landroid/app/Activity;",
    ),
    definingClass = IMPL,
    name = "showAd",
)

private fun Method.calls(owner: String, name: String): Boolean =
    implementation?.instructions?.any {
        val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
        ref?.definingClass == owner && ref.name == name
    } == true

private fun Method.hasType(type: String): Boolean =
    implementation?.instructions?.any {
        (it as? ReferenceInstruction)?.reference.let { ref ->
            (ref as? TypeReference)?.type == type
        }
    } == true

@Suppress("unused")
val botworldRewardedAdsPatch = bytecodePatch(
    name = "Skip rewarded ads",
    description = "Completes the rewarded-video flow locally so the requested " +
        "reward is granted without playing an ad. No ad-network impression " +
        "or revenue is synthesized; IAP purchases still require real payment.",
    default = true,
) {
    compatibleWith(BOTWORLD_COMPATIBILITY)

    execute {
        val ready = IsReadyFingerprint.method
        val forward = RewardForwardFingerprint.method
        val implShow = ImplShowAdFingerprint.method
        val implShowContainer = ImplShowAdContainerFingerprint.method

        // Both impl show entry points must still queue through the state
        // machine (a(state, Runnable)); bodies that no longer do mean the
        // SDK shape changed.
        for (entry in listOf(implShow, implShowContainer)) {
            if (!entry.calls(IMPL, "a")) {
                throw PatchException(
                    "Botworld: impl show path changed; use a clean Botworld 1.36.2 (171310).",
                )
            }
        }
        // The wrapper forward must still reach the game listener via q2.
        if (!forward.calls("Lcom/applovin/impl/q2;", "a")) {
            throw PatchException(
                "Botworld: reward forward changed; use a clean Botworld 1.36.2 (171310).",
            )
        }
        // Ready probe must still consult the impl (not a constant already).
        if (!ready.calls(IMPL, "isReady")) {
            throw PatchException(
                "Botworld: readiness probe changed; use a clean Botworld 1.36.2 (171310).",
            )
        }
        // Synthetic-ad + reward building blocks must still be public.
        val synthInit = try {
            mutableClassDefBy(SYNTH_AD).methods.singleOrNull {
                it.name == "<init>" &&
                    it.parameterTypes == listOf(STRING, REWARDED_FORMAT, STRING)
            }
        } catch (e: Exception) {
            null
        } ?: throw PatchException(
            "Botworld: synthetic ad constructor gone; use a clean Botworld 1.36.2 (171310).",
        )
        if (!AccessFlags.PUBLIC.isSet(synthInit.accessFlags)) {
            throw PatchException("Botworld: synthetic ad constructor no longer public.")
        }
        val rewardDefault = try {
            mutableClassDefBy(REWARD_FACTORY).methods.singleOrNull {
                it.name == "createDefault" && it.parameterTypes.isEmpty()
            }
        } catch (e: Exception) {
            null
        } ?: throw PatchException(
            "Botworld: default reward factory gone; use a clean Botworld 1.36.2 (171310).",
        )
        if (!AccessFlags.PUBLIC.isSet(rewardDefault.accessFlags) ||
            !AccessFlags.STATIC.isSet(rewardDefault.accessFlags)
        ) {
            throw PatchException("Botworld: default reward factory shape changed.")
        }

        // Game always sees a ready rewarded slot. v0 (not p0): reusing the
        // this-reference register for an int trips the ART verifier.
        ready.replaceBody("const/4 v0, 0x1\nreturn v0")
        // Local completion on the IMPL methods (not the public overloads):
        // every public showAd variant converges here, including the
        // Activity/Context ones Unity calls via JNI. Synthesize ad + default
        // reward, fire the impl's own wrapper callbacks (reward then hide).
        // The wrapper return is typed as the base class, so check-cast before
        // the subclass reward call (ART verifier rejects the invoke without it).
        // p1 = placement, p2 = customData on both impl signatures.
        // The wrapper is obtained via the virtual createAdListenerWrapper()
        // (overridden in MaxRewardedAdImpl to build the $b reward wrapper),
        // never by reading field c directly (declared as the base $b type).
        val localCompleteOrdered = """
                sget-object v0, $REWARDED_FORMAT->REWARDED:$REWARDED_FORMAT
                new-instance v1, $SYNTH_AD
                const-string v2, "botworld_reward"
                move-object v3, p1
                invoke-direct {v1, v2, v0, v3}, $SYNTH_AD-><init>($STRING$REWARDED_FORMAT$STRING)V
                invoke-static {}, $REWARD_FACTORY->createDefault()Lcom/applovin/mediation/MaxReward;
                move-result-object v2
                invoke-virtual {p0}, $IMPL->createAdListenerWrapper()$BASE_WRAPPER
                move-result-object v0
                check-cast v0, $WRAPPER
                invoke-virtual {v0, v1, v2}, $WRAPPER->onUserRewarded(Lcom/applovin/mediation/MaxAd; Lcom/applovin/mediation/MaxReward;)V
                invoke-virtual {v0, v1}, $BASE_WRAPPER->onAdHidden(Lcom/applovin/mediation/MaxAd;)V
                return-void
            """.trimIndent()
        implShow.replaceBody(localCompleteOrdered)
        implShowContainer.replaceBody(localCompleteOrdered)
        // Post-check: both bodies must now invoke onUserRewarded.
        for (entry in listOf(implShow, implShowContainer)) {
            if (entry.implementation?.instructions?.none {
                (it.opcode == Opcode.INVOKE_VIRTUAL) &&
                    ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == "onUserRewarded"
            } == true) {
                throw PatchException("Botworld: reward body did not apply.")
            }
        }
    }
}

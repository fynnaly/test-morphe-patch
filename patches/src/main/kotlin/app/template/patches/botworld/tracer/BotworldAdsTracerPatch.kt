package app.template.patches.botworld.tracer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.BOTWORLD_COMPATIBILITY
import com.android.tools.smali.dexlib2.AccessFlags

// Opt-in tracer for the Botworld 1.36.2 rewarded-ads path. Injects
// Log.e("BOTW", ...) at the entry of every Java ads method the Skip
// patches target, so one Watch Ad tap proves definitively whether the
// game ever reaches the Java layer or stops in native il2cpp.
//
// Reading the logcat output (tap Watch Ad once, capture unfiltered):
// - No BOTW lines at all  -> the game never calls the Java bridges;
//   the gate is native (QueueAd in libil2cpp.so). Stop patching DEX.
// - "BOTW loadAd/show ..." -> Java IS reached; the Skip patch body
//   should have fired. If the dialog persists anyway, the game ignores
//   the callback (native-side decision) — also stop patching DEX.
//
// Disabled by default; enable only for diagnosis builds.
private object TracerAdMobShow : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = emptyList(),
    definingClass = "Lcom/google/unity/ads/UnityRewardedAd;",
    name = "show",
)

private object TracerAdMobLoad : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = listOf(
        "Ljava/lang/String;",
        "Lcom/google/android/gms/ads/AdRequest;",
    ),
    definingClass = "Lcom/google/unity/ads/UnityRewardedAd;",
    name = "loadAd",
)

private object TracerAdMobAvailable : Fingerprint(
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = listOf("Ljava/lang/String;"),
    definingClass = "Lcom/google/unity/ads/UnityRewardedAd;",
    name = "isAdAvailable",
)

private object TracerDecagonShow : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = emptyList(),
    definingClass = "Lcom/google/unity/ads/decagon/UnityRewardedAd;",
    name = "show",
)

private object TracerDecagonLoad : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = listOf(
        "Lcom/google/android/libraries/ads/mobile/sdk/common/AdRequest;",
    ),
    definingClass = "Lcom/google/unity/ads/decagon/UnityRewardedAd;",
    name = "load",
)

private object TracerGmsAvailable : Fingerprint(
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
    ),
    definingClass = "Lcom/google/android/gms/ads/rewarded/RewardedAd;",
    name = "isAdAvailable",
)

private object TracerMaxShow : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    definingClass = "Lcom/applovin/mediation/ads/MaxRewardedAd;",
    name = "showAd",
)

private object TracerMaxReady : Fingerprint(
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = emptyList(),
    definingClass = "Lcom/applovin/mediation/ads/MaxRewardedAd;",
    name = "isReady",
)

private fun tag(msg: String) = """
    const-string v0, "BOTW"
    const-string v1, "$msg"
    invoke-static {v0, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
    move-result v0
""".trimIndent()

@Suppress("unused")
val botworldAdsTracerPatch = bytecodePatch(
    name = "Ads path tracer",
    description = "Logs every Java ads-bridge entry via logcat (tag BOTW) " +
        "for diagnosis. Proves whether the game reaches the Java layer. " +
        "Disable for release builds.",
    default = false,
) {
    compatibleWith(BOTWORLD_COMPATIBILITY)

    execute {
        TracerAdMobShow.methodOrNull?.addInstructions(0, tag("admob show enter"))
        TracerAdMobLoad.methodOrNull?.addInstructions(0, tag("admob loadAd enter"))
        TracerAdMobAvailable.methodOrNull?.addInstructions(0, tag("admob isAdAvailable enter"))
        TracerDecagonShow.methodOrNull?.addInstructions(0, tag("decagon show enter"))
        TracerDecagonLoad.methodOrNull?.addInstructions(0, tag("decagon load enter"))
        TracerGmsAvailable.methodOrNull?.addInstructions(0, tag("gms static isAdAvailable enter"))
        TracerMaxShow.methodOrNull?.addInstructions(0, tag("max showAd enter"))
        TracerMaxReady.methodOrNull?.addInstructions(0, tag("max isReady enter"))
    }
}

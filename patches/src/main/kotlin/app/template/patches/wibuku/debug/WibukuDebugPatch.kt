package app.template.patches.wibuku.debug

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.WIBUKU_COMPATIBILITY

// Opt-in tracer for the post-login splash gate. Injects Log.e("WIBUKU", ...)
// at method entries via addInstructions; the patcher remaps registers, so no
// hand-edited smali is involved. Smali dumps are only read to locate targets.
//
// Reading the logcat output (adb logcat | grep WIBUKU):
// - "H0 enter" missing  -> F0() returned null, the backend rejected the
//   user/register call (server auth code / device token / integrity).
// - "H0 enter" present but no navigation -> G0() update check failed or the
//   C0/D0 animation flags never completed; check "C0 enter" presence.
// - "isPremium called" -> the premium gate is exercised on this screen.
//
// Disabled by default; enable only for diagnosis builds.
private object SplashGateFingerprint : Fingerprint(
    returnType = "V",
    definingClass = "Lwibuku/app/wibuku/ui/splash/SplashFragment;",
    name = "C0",
    parameters = emptyList(),
)

private object SplashLoginAppliedFingerprint : Fingerprint(
    returnType = "V",
    definingClass = "Lwibuku/app/wibuku/ui/splash/SplashFragment;",
    name = "H0",
    parameters = listOf("Lwibuku/app/wibuku/model/app/LoginResponse;"),
)

private object PremiumCallFingerprint : Fingerprint(
    returnType = "Z",
    definingClass = "Lwibuku/app/wibuku/model/user/AppUser;",
    name = "isPremium",
    parameters = emptyList(),
)

private fun logTag(tag: String) = """
    const-string v0, "WIBUKU"
    const-string v1, "$tag"
    invoke-static {v0, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
    move-result v0
""".trimIndent()

@Suppress("unused")
val wibukuDebugPatch = bytecodePatch(
    name = "Login tracer",
    description = "Logs splash/login gate progress via logcat for diagnosis. Disable for release builds.",
    default = false,
) {
    compatibleWith(WIBUKU_COMPATIBILITY)

    execute {
        SplashGateFingerprint.methodOrNull?.addInstructions(0, logTag("C0 enter"))
        SplashLoginAppliedFingerprint.methodOrNull?.addInstructions(0, logTag("H0 enter LoginResponse non-null"))
        PremiumCallFingerprint.methodOrNull?.addInstructions(0, logTag("isPremium called"))
    }
}

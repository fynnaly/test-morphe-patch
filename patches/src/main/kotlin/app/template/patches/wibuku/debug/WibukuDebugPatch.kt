package app.template.patches.wibuku.debug

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.template.patches.shared.Constants.WIBUKU_COMPATIBILITY

// Opt-in tracer for the post-login splash gate. Injects Log.e("WIBUKU", ...)
// at method entries via addInstructions; the patcher remaps registers, so no
// hand-edited smali is involved. Smali dumps are only read to locate targets.
//
// Reading the logcat output (adb logcat -s WIBUKU:V):
// - "F0 enter" missing          -> splash worker never ran; check "I0 enter".
// - "F0 enter" without "H0"     -> F0() returned null, the backend rejected
//   the user/register call (server auth code / device token / integrity).
// - "H0 enter" present, stuck   -> G0() update check failed ("G0 enter" then
//   "WIBUKU-DLG Wajib Update") or the C0/D0 animation flags never completed.
// - "WIBUKU-DLG <title>"        -> exact dialog shown: Ooops! / Maintenance /
//   Gagal Terhubung / Wajib Update (p0 of h5a.D is the dialog title).
// - "WIBUKU-DLG-MSG <msg>"      -> the dialog message body (p1 of h5a.D):
//   this is the server/network reason, e.g. session or auth failure text.
// - "WIBUKU-ERR <error>"        -> ResourceResponse.getError() at the
//   central response handler (nc1.a); empty/null means the failure never
//   produced a server string (timeout / exception before response parse).
// - "isPremium called"          -> the premium gate is exercised on screen.
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

private object SplashFetchFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    definingClass = "Lwibuku/app/wibuku/ui/splash/SplashFragment;",
    name = "F0",
    parameters = listOf("Lom0;"),
)

private object SplashUpdateCheckFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    definingClass = "Lwibuku/app/wibuku/ui/splash/SplashFragment;",
    name = "G0",
    parameters = listOf("Lxo1;", "Lom0;"),
)

private object SplashStartFingerprint : Fingerprint(
    returnType = "V",
    definingClass = "Lwibuku/app/wibuku/ui/splash/SplashFragment;",
    name = "I0",
    parameters = emptyList(),
)

// fu.l is the worker coroutine holding the dialog branches; located by its
// unique "Tidak bisa masuk saat ini." string instead of the obfuscated owner.
private object SplashWorkerFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(string("Tidak bisa masuk saat ini.")),
)

// h5a.D(String title, String msg, String button, boolean, Activity, cb) shows
// every splash dialog; p0 carries the title.
private object SplashDialogFingerprint : Fingerprint(
    returnType = "V",
    definingClass = "Lh5a;",
    name = "D",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Z",
        "Landroid/app/Activity;",
        "Lzw1;",
    ),
)

private object PremiumCallFingerprint : Fingerprint(
    returnType = "Z",
    definingClass = "Lwibuku/app/wibuku/model/user/AppUser;",
    name = "isPremium",
    parameters = emptyList(),
)

// nc1.a(ResourceResponse) is the central response handler; getError()
// carries the server/network reason string (static method, p0 = response).
private object ResponseHandlerFingerprint : Fingerprint(
    returnType = "V",
    definingClass = "Lnc1;",
    name = "a",
    parameters = listOf("Lwibuku/app/wibuku/model/network/ResourceResponse;"),
)

private fun logTag(tag: String) = """
    const-string v0, "WIBUKU"
    const-string v1, "$tag"
    invoke-static {v0, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
    move-result v0
""".trimIndent()

private fun logDialogTitle() = """
    const-string v0, "WIBUKU-DLG"
    move-object v1, p0
    invoke-static {v0, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
    move-result v0
""".trimIndent()

private fun logDialogMessage() = """
    const-string v0, "WIBUKU-DLG-MSG"
    move-object v1, p1
    invoke-static {v0, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
    move-result v0
""".trimIndent()

private fun logResponseError() = """
    const-string v0, "WIBUKU-ERR"
    invoke-virtual {p0}, Lwibuku/app/wibuku/model/network/ResourceResponse;->getError()Ljava/lang/String;
    move-result-object v1
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
        SplashStartFingerprint.methodOrNull?.addInstructions(0, logTag("I0 enter"))
        SplashWorkerFingerprint.methodOrNull?.addInstructions(0, logTag("fu.l enter"))
        SplashFetchFingerprint.methodOrNull?.addInstructions(0, logTag("F0 enter"))
        SplashLoginAppliedFingerprint.methodOrNull?.addInstructions(0, logTag("H0 enter LoginResponse non-null"))
        SplashUpdateCheckFingerprint.methodOrNull?.addInstructions(0, logTag("G0 enter"))
        SplashGateFingerprint.methodOrNull?.addInstructions(0, logTag("C0 enter"))
        SplashDialogFingerprint.methodOrNull?.addInstructions(0, logDialogTitle())
        SplashDialogFingerprint.methodOrNull?.addInstructions(0, logDialogMessage())
        ResponseHandlerFingerprint.methodOrNull?.addInstructions(0, logResponseError())
        PremiumCallFingerprint.methodOrNull?.addInstructions(0, logTag("isPremium called"))
    }
}

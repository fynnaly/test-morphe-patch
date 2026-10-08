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
// Covers Wibuku 1.4.5 (78) and 1.4.1 (74): the splash gate (C0/H0/I0),
// the worker string, and the ResourceResponse handler exist in both, but
// the obfuscated owner classes and coroutine parameter types shifted
// between versions (nc1->ic1, h5a.D->mu1.B, Lom0->Lrm0, Lxo1->Lqo1,
// Lzw1->Llw1). Fingerprints that pin those shifting names resolve against
// one version only; every target below therefore uses methodOrNull so the
// tracer degrades gracefully instead of failing the build on the other
// version. Response-shape fingerprints anchor on the stable app model
// (ResourceResponse parameter) rather than the obfuscated owner.
//
// Reading the logcat output (adb logcat -s WIBUKU:V):
// - "F0 enter" missing          -> splash worker never ran; check "I0 enter".
// - "F0 enter" without "H0"     -> F0() returned null, the backend rejected
//   the user/register call (server auth code / device token / integrity).
// - "H0 enter" present, stuck   -> G0() update check failed ("G0 enter" then
//   "WIBUKU-DLG Wajib Update") or the C0/D0 animation flags never completed.
// - "WIBUKU-DLG <title>"        -> exact dialog shown: Ooops! / Maintenance /
//   Gagal Terhubung / Wajib Update (p0 of the dialog helper is the title).
// - "WIBUKU-DLG-MSG <msg>"      -> the dialog message body (p1 of the helper):
//   this is the server/network reason, e.g. session or auth failure text.
// - "WIBUKU-ERR <error>"        -> ResourceResponse.getError() at the
//   central response handler; empty/null means the failure never
//   produced a server string (timeout / exception before response parse).
// - "WIBUKU-STATUS <status>"     -> ResourceResponse.getStatus() enum name
//   (SUCCESS / FAILED / UNAUTHORIZED / UNSESSION / RETRY / WAIT / ...):
//   tells whether the server rejected, the session lapsed, or a retry was asked.
// - "WIBUKU-CODE <code>"         -> ResourceResponse.getCode() server code string
//   (1.4.5 only; 1.4.1 has no getCode and logs nothing here).
// - "WIBUKU-DATA <data>"         -> String.valueOf(ResourceResponse.getData());
//   null means no payload came back with the failure.
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

// F0/G0 take obfuscated coroutine types that shifted between versions
// (1.4.5: Lom0/Lxo1, 1.4.1: Lrm0/Lqo1). Match by name + arity-agnostic
// parameter count instead of the exact owner.
private object SplashFetch145Fingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    definingClass = "Lwibuku/app/wibuku/ui/splash/SplashFragment;",
    name = "F0",
    parameters = listOf("Lom0;"),
)

private object SplashFetch141Fingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    definingClass = "Lwibuku/app/wibuku/ui/splash/SplashFragment;",
    name = "F0",
    parameters = listOf("Lrm0;"),
)

private object SplashUpdateCheck145Fingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    definingClass = "Lwibuku/app/wibuku/ui/splash/SplashFragment;",
    name = "G0",
    parameters = listOf("Lxo1;", "Lom0;"),
)

private object SplashUpdateCheck141Fingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    definingClass = "Lwibuku/app/wibuku/ui/splash/SplashFragment;",
    name = "G0",
    parameters = listOf("Lqo1;", "Lrm0;"),
)

private object SplashStartFingerprint : Fingerprint(
    returnType = "V",
    definingClass = "Lwibuku/app/wibuku/ui/splash/SplashFragment;",
    name = "I0",
    parameters = emptyList(),
)

// The worker coroutine holds the dialog branches; located by its unique
// "Tidak bisa masuk saat ini." string instead of the obfuscated owner
// (Lfu;.l in 1.4.5, Lmu;.l in 1.4.1).
private object SplashWorkerFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(string("Tidak bisa masuk saat ini.")),
)

// Dialog helper: 1.4.5 Lh5a;.D(..., Lzw1;), 1.4.1 Lmu1;.B(..., Llw1;).
// Same arity and first five parameters; only the name/owner/callback differ.
private object SplashDialog145Fingerprint : Fingerprint(
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

private object SplashDialog141Fingerprint : Fingerprint(
    returnType = "V",
    definingClass = "Lmu1;",
    name = "B",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Z",
        "Landroid/app/Activity;",
        "Llw1;",
    ),
)

private object PremiumCallFingerprint : Fingerprint(
    returnType = "Z",
    definingClass = "Lwibuku/app/wibuku/model/user/AppUser;",
    name = "isPremium",
    parameters = emptyList(),
)

// Central response handler: 1.4.5 Lnc1;.a, 1.4.1 Lic1;.a. Same signature;
// anchor on the stable ResourceResponse parameter via two owner variants.
private object ResponseHandler145Fingerprint : Fingerprint(
    returnType = "V",
    definingClass = "Lnc1;",
    name = "a",
    parameters = listOf("Lwibuku/app/wibuku/model/network/ResourceResponse;"),
)

private object ResponseHandler141Fingerprint : Fingerprint(
    returnType = "V",
    definingClass = "Lic1;",
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

private fun logResponseStatus() = """
    const-string v0, "WIBUKU-STATUS"
    invoke-virtual {p0}, Lwibuku/app/wibuku/model/network/ResourceResponse;->getStatus()Lwibuku/app/wibuku/model/network/ResourceResponse${'$'}ResponseStatus;
    move-result-object v1
    invoke-static {v1}, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;
    move-result-object v1
    invoke-static {v0, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
    move-result v0
""".trimIndent()

// getCode() exists only on 1.4.5; on 1.4.1 this fingerprint never resolves
// and the methodOrNull call below skips it.
private fun logResponseCode() = """
    const-string v0, "WIBUKU-CODE"
    invoke-virtual {p0}, Lwibuku/app/wibuku/model/network/ResourceResponse;->getCode()Ljava/lang/String;
    move-result-object v1
    invoke-static {v1}, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;
    move-result-object v1
    invoke-static {v0, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
    move-result v0
""".trimIndent()

private fun logResponseData() = """
    const-string v0, "WIBUKU-DATA"
    invoke-virtual {p0}, Lwibuku/app/wibuku/model/network/ResourceResponse;->getData()Ljava/lang/Object;
    move-result-object v1
    invoke-static {v1}, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;
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
        SplashFetch145Fingerprint.methodOrNull?.addInstructions(0, logTag("F0 enter"))
        SplashFetch141Fingerprint.methodOrNull?.addInstructions(0, logTag("F0 enter"))
        SplashLoginAppliedFingerprint.methodOrNull?.addInstructions(0, logTag("H0 enter LoginResponse non-null"))
        SplashUpdateCheck145Fingerprint.methodOrNull?.addInstructions(0, logTag("G0 enter"))
        SplashUpdateCheck141Fingerprint.methodOrNull?.addInstructions(0, logTag("G0 enter"))
        SplashGateFingerprint.methodOrNull?.addInstructions(0, logTag("C0 enter"))
        SplashDialog145Fingerprint.methodOrNull?.addInstructions(0, logDialogTitle())
        SplashDialog145Fingerprint.methodOrNull?.addInstructions(0, logDialogMessage())
        SplashDialog141Fingerprint.methodOrNull?.addInstructions(0, logDialogTitle())
        SplashDialog141Fingerprint.methodOrNull?.addInstructions(0, logDialogMessage())
        ResponseHandler145Fingerprint.methodOrNull?.addInstructions(0, logResponseError())
        ResponseHandler145Fingerprint.methodOrNull?.addInstructions(0, logResponseStatus())
        ResponseHandler145Fingerprint.methodOrNull?.addInstructions(0, logResponseCode())
        ResponseHandler145Fingerprint.methodOrNull?.addInstructions(0, logResponseData())
        ResponseHandler141Fingerprint.methodOrNull?.addInstructions(0, logResponseError())
        ResponseHandler141Fingerprint.methodOrNull?.addInstructions(0, logResponseStatus())
        // No logResponseCode here: 1.4.1 ResourceResponse has no getCode();
        // invoking it would throw NoSuchMethodError at runtime.
        ResponseHandler141Fingerprint.methodOrNull?.addInstructions(0, logResponseData())
        PremiumCallFingerprint.methodOrNull?.addInstructions(0, logTag("isPremium called"))
    }
}

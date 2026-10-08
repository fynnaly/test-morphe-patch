package app.template.patches.botworld.antitamper

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.BOTWORLD_COMPATIBILITY
import app.template.patches.shared.replaceBody
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * Neutralizes Botworld 1.36.2's LuckyPatcher guard
 * (Lcom/featherweightgames/plugins/LuckyPatcher;).
 *
 * Shape (verified from the APK DEX, classes.dex): three static methods,
 * each ending in Process.killProcess(myPid()) on the tamper path:
 * - checkIntegrity(Context)V (public): clears FLAG_DEBUGGABLE, then calls
 *   checkSignature + checkPackages when debuggable.
 * - checkPackages(Context)V (private): kills when com.dimonvideo.luckypatcher,
 *   com.chelpus.lackypatch, or the LACK billing service is installed.
 * - checkSignature(Context)V (private): kills when the APK Signature
 *   differs from the hardcoded original.
 *
 * All three are wiped to return-void after asserting the kill + anchor
 * strings are still present, so a reshaped guard fails loudly instead of
 * shipping a half-patched game that kills itself on launch.
 */
private object CheckIntegrityFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf("Landroid/content/Context;"),
    definingClass = "Lcom/featherweightgames/plugins/LuckyPatcher;",
    name = "checkIntegrity",
)

private object CheckPackagesFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.STATIC),
    parameters = listOf("Landroid/content/Context;"),
    definingClass = "Lcom/featherweightgames/plugins/LuckyPatcher;",
    name = "checkPackages",
)

private object CheckSignatureFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.STATIC),
    parameters = listOf("Landroid/content/Context;"),
    definingClass = "Lcom/featherweightgames/plugins/LuckyPatcher;",
    name = "checkSignature",
)

private fun hasKillProcess(method: com.android.tools.smali.dexlib2.iface.Method): Boolean =
    method.implementation?.instructions?.any {
        val ref = (it as? ReferenceInstruction)?.reference
        ref is com.android.tools.smali.dexlib2.iface.reference.MethodReference &&
            ref.definingClass == "Landroid/os/Process;" && ref.name == "killProcess"
    } == true

private fun hasString(method: com.android.tools.smali.dexlib2.iface.Method, value: String): Boolean =
    method.implementation?.instructions?.any {
        ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == value
    } == true

@Suppress("unused")
val botworldAntiTamperPatch = bytecodePatch(
    name = "Disable tamper guard",
    description = "Neutralizes the LuckyPatcher/signature guard so the re-signed app " +
        "does not kill itself on launch. Does not grant items or currency.",
    default = true,
) {
    compatibleWith(BOTWORLD_COMPATIBILITY)

    execute {
        val integrity = CheckIntegrityFingerprint.method
        val packages = CheckPackagesFingerprint.method
        val signature = CheckSignatureFingerprint.method

        if (!hasKillProcess(packages) ||
            !hasString(packages, "com.dimonvideo.luckypatcher")
        ) {
            throw PatchException(
                "Botworld: checkPackages guard shape changed; " +
                    "use a clean Botworld 1.36.2 (171310).",
            )
        }
        if (!hasKillProcess(signature)) {
            throw PatchException(
                "Botworld: checkSignature guard shape changed; " +
                    "use a clean Botworld 1.36.2 (171310).",
            )
        }
        if (!hasKillProcess(integrity) && integrity.implementation?.instructions?.count() == 1) {
            throw PatchException(
                "Botworld: checkIntegrity guard shape changed; " +
                    "use a clean Botworld 1.36.2 (171310).",
            )
        }

        CheckIntegrityFingerprint.method.replaceBody("return-void")
        CheckPackagesFingerprint.method.replaceBody("return-void")
        CheckSignatureFingerprint.method.replaceBody("return-void")
    }
}

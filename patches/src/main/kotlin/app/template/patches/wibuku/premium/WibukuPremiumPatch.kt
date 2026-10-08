package app.template.patches.wibuku.premium

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.WIBUKU_COMPATIBILITY
import app.template.patches.shared.replaceBody
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

// Force the local expiry check to always report Premium.
// The AppUser model, session handling and server responses stay untouched:
// only the boolean emitted by isPremium() changes.
@Suppress("unused")
val wibukuPremiumPatch = bytecodePatch(
    name = "Premium",
    description = "Forces the local Premium check to always pass. " +
        "Does not activate a subscription on your account; " +
        "server-gated features may still require a real purchase.",
    default = true,
) {
    compatibleWith(WIBUKU_COMPATIBILITY)

    execute {
        val method = WibukuIsPremiumFingerprint.method
        val classDef = WibukuIsPremiumFingerprint.classDef

        if (classDef.type != "Lwibuku/app/wibuku/model/user/AppUser;") {
            throw PatchException(
                "Wibuku: isPremium resolved in an unexpected class " +
                    "${classDef.type}; refusing to patch.",
            )
        }

        // Guard: the method must still read the premium timestamp field.
        // This keeps the patch from silently applying to a reshaped method
        // in a future app version.
        val readsPremium = method.implementation?.instructions?.any { instruction ->
            val field = ((instruction as? ReferenceInstruction)?.reference as? FieldReference)
            field?.definingClass == classDef.type &&
                field.name == "premium" && field.type == "J"
        } == true
        if (!readsPremium) {
            throw PatchException(
                "Wibuku: isPremium no longer reads the premium timestamp; " +
                    "method shape changed, use a clean Wibuku 1.4.5 (78) or 1.4.1 (74).",
            )
        }

        if (method.returnType != "Z" || method.parameterTypes.isNotEmpty()) {
            throw PatchException("Wibuku: isPremium signature changed.")
        }

        method.replaceBody(
            """
                const/4 v0, 0x1
                return v0
            """.trimIndent(),
        )
    }
}

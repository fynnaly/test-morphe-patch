package app.template.patches.wibuku.microg

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Pins the Wibuku 1.4.5 login shape the MicroG patch is built against.
 *
 * Target: IntroFragment's view-setup method, which builds the Google
 * Identity AuthorizationRequest with the "email" scope and the
 * userinfo.profile scope (verified from the APK DEX: 61 instructions,
 * Scope.<init> x2 + AuthorizationRequest.<init>).
 *
 * Guards:
 *   1. both scope strings present in the same method
 *   2. the method actually constructs AuthorizationRequest
 */
object WibukuAuthShapeFingerprint : Fingerprint(
    returnType = "V",
    definingClass = "Lwibuku/app/wibuku/ui/intro/IntroFragment;",
    filters = listOf(
        string("email"),
        string("https://www.googleapis.com/auth/userinfo.profile"),
    ),
    custom = { method: Method, _ ->
        method.implementation?.instructions?.any {
            val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
            ref?.definingClass == "Lcom/google/android/gms/auth/api/identity/AuthorizationRequest;" &&
                ref.name == "<init>"
        } == true
    },
)

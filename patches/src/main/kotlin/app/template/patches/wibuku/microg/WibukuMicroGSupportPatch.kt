package app.template.patches.wibuku.microg

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.template.patches.shared.Constants.WIBUKU_COMPATIBILITY
import app.template.patches.shared.replaceBody
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val GMS_PACKAGE = "com.google.android.gms"
private const val CHIMERA_AUTHORITY = "com.google.android.gms.chimera"
private const val MICROG_CHIMERA = "app.revanced.android.gms.chimera"

/**
 * GMS service actions bundled in Wibuku 1.4.5, each served by MicroG-RE
 * under its own package prefix (verified against the MicroG-RE manifest:
 * identity authorization/signin, classic signin, credentials and the base
 * signin service are all declared there).
 *
 * Binder interface descriptors are intentionally untouched: MicroG
 * implements the same AIDL interfaces, only the owning package changes.
 */
private val serviceActions = mapOf(
    "com.google.android.gms.auth.api.identity.service.authorization.START" to
        "app.revanced.android.gms.auth.api.identity.service.authorization.START",
    "com.google.android.gms.auth.api.identity.service.signin.START" to
        "app.revanced.android.gms.auth.api.identity.service.signin.START",
    "com.google.android.gms.auth.api.signin.service.START" to
        "app.revanced.android.gms.auth.api.signin.service.START",
    "com.google.android.gms.auth.api.credentials.service.START" to
        "app.revanced.android.gms.auth.api.credentials.service.START",
    "com.google.android.gms.signin.service.START" to
        "app.revanced.android.gms.signin.service.START",
)

private fun constStringOf(instruction: Any?): String? =
    (((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string)

private val wibukuMicroGResources = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { WibukuMicroGManifest.install(it.documentElement) }
    }
}

/**
 * Route Google Identity Services sign-in through MicroG-RE.
 *
 * Wibuku 1.4.5 logs in with Google Identity Authorization: the app builds an
 * AuthorizationRequest (scopes: email + userinfo.profile), Google returns a
 * server auth code, and the backend exchanges it for a session
 * (RegisterRequest -> LoginResponse).
 *
 * The bundled GMS client binds the identity service with an implicit intent
 * (service action + `com.google.android.gms` package) resolved through the
 * GMS Chimera provider. On a device without Play Services nothing answers
 * that bind, so the login tap silently dies. This patch redirects all three
 * layers to MicroG-RE:
 *
 * 1. the service-action getters return MicroG-RE's action strings,
 * 2. the Chimera provider authority and the static bind fallback package
 *    point at MicroG-RE,
 * 3. the Play Services availability probe reports SUCCESS so the app never
 *    diverts into the missing-GMS fallback (same approach as the official
 *    GmsCore support patch).
 *
 * The AuthorizationRequest scopes, the auth-code exchange and the session
 * handling stay untouched: authentication still comes from the remote
 * service, nothing is faked locally.
 *
 * Signer caveat: MicroG presents the declared original signature to Google
 * when requesting the auth code. If logcat shows UNREGISTERED_ON_API_CONSOLE,
 * the patched APK's signer differs from the Play-original one registered in
 * Google's API console, and the patch needs the Play-original certificate
 * SHA-1 instead of the current repack one.
 */
@Suppress("unused")
val wibukuMicroGSupportPatch = bytecodePatch(
    name = "Google sign-in via MicroG-RE",
    description = "Routes Google sign-in through MicroG-RE 7.1.1+. " +
        "Requires app.revanced.android.gms with the same Google account.",
    default = true,
) {
    compatibleWith(WIBUKU_COMPATIBILITY)
    dependsOn(wibukuMicroGResources)

    execute {
        // Pin the login shape first: a future Wibuku with a different auth
        // flow must fail here instead of shipping a half-redirected login.
        WibukuAuthShapeFingerprint.method

        // ---- Pass A: validate everything before mutating anything. ----

        // 1. Service-action getters: tiny no-arg String methods whose only
        // constant is one of the known GMS actions.
        val actionOwners = mutableMapOf<String, String>()
        classDefForEach { cls ->
            cls.methods.forEach { method ->
                if (method.name != "o" || method.returnType != "Ljava/lang/String;" ||
                    method.parameterTypes.isNotEmpty()
                ) return@forEach
                val code = method.implementation?.instructions?.toList() ?: return@forEach
                if (code.size > 6 || code.lastOrNull()?.opcode != Opcode.RETURN_OBJECT) return@forEach
                val constants = code.filter { it.opcode == Opcode.CONST_STRING }
                if (constants.size != 1) return@forEach
                val value = constStringOf(constants.single()) ?: return@forEach
                if (value !in serviceActions) return@forEach
                val prev = actionOwners.put(value, cls.type)
                if (prev != null && prev != cls.type) {
                    throw PatchException(
                        "Google sign-in via MicroG-RE: action $value served by two classes.",
                    )
                }
            }
        }
        val missingActions = serviceActions.keys - actionOwners.keys
        if (missingActions.isNotEmpty()) {
            throw PatchException(
                "Google sign-in via MicroG-RE: missing service bindings $missingActions; " +
                    "use a clean Wibuku 1.4.5 (78).",
            )
        }

        // 2. Bind-intent resolver: the class whose <clinit> builds the
        // Chimera provider URI from the GMS authority.
        var resolverType: String? = null
        var chimeraIndex = -1
        var chimeraRegister = -1
        classDefForEach { cls ->
            cls.methods.forEach { method ->
                if (method.name != "<clinit>") return@forEach
                val code = method.implementation?.instructions?.toList() ?: return@forEach
                val matches = code.indices.filter { constStringOf(code[it]) == CHIMERA_AUTHORITY }
                if (matches.isEmpty()) return@forEach
                if (matches.size != 1 || resolverType != null) {
                    throw PatchException(
                        "Google sign-in via MicroG-RE: Chimera authority shape changed.",
                    )
                }
                resolverType = cls.type
                chimeraIndex = matches.single()
                chimeraRegister = (code[chimeraIndex] as OneRegisterInstruction).registerA
            }
        }
        val resolver = resolverType
            ?: throw PatchException(
                "Google sign-in via MicroG-RE: bind resolver not found; " +
                    "use a clean Wibuku 1.4.5 (78).",
            )

        // 3. Static bind fallback: the Intent method in the resolver class
        // whose GMS package string feeds directly into setPackage().
        val mutableResolver = mutableClassDefBy(resolver)
        var bindMethodName: String? = null
        var bindIndex = -1
        var bindRegister = -1
        mutableResolver.methods.forEach { method ->
            if (method.returnType != "Landroid/content/Intent;" ||
                method.parameterTypes.size != 2 ||
                method.parameterTypes.first() != "Landroid/content/Context;"
            ) return@forEach
            val code = method.implementation?.instructions?.toList() ?: return@forEach
            val matches = code.indices.filter { constStringOf(code[it]) == GMS_PACKAGE }
            if (matches.isEmpty()) return@forEach
            if (matches.size != 1 || bindMethodName != null) {
                throw PatchException(
                    "Google sign-in via MicroG-RE: bind fallback shape changed.",
                )
            }
            val next = code.getOrNull(matches.single() + 1)
            val ref = ((next as? ReferenceInstruction)?.reference as? MethodReference)
            if (next?.opcode != Opcode.INVOKE_VIRTUAL ||
                ref?.definingClass != "Landroid/content/Intent;" || ref.name != "setPackage"
            ) {
                throw PatchException(
                    "Google sign-in via MicroG-RE: bind fallback no longer targets setPackage.",
                )
            }
            bindMethodName = method.name
            bindIndex = matches.single()
            bindRegister = (code[bindIndex] as OneRegisterInstruction).registerA
        }
        val bindName = bindMethodName
            ?: throw PatchException(
                "Google sign-in via MicroG-RE: bind fallback not found; " +
                    "use a clean Wibuku 1.4.5 (78).",
            )

        // 4. Availability probe: the (Context, int)->int method that stats
        // the GMS package. It reports SUCCESS afterwards so the app never
        // diverts into the missing-GMS fallback.
        var availabilityType: String? = null
        var availabilityName: String? = null
        classDefForEach { cls ->
            cls.methods.forEach { method ->
                if (method.returnType != "I" || method.parameterTypes !=
                    listOf("Landroid/content/Context;", "I")
                ) return@forEach
                val code = method.implementation?.instructions?.toList() ?: return@forEach
                val statsGms = code.any { instruction ->
                    val ref = ((instruction as? ReferenceInstruction)?.reference as? MethodReference)
                    instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                        ref?.definingClass == "Landroid/content/pm/PackageManager;" &&
                        ref.name == "getPackageInfo"
                } && code.any { constStringOf(it) == GMS_PACKAGE }
                if (!statsGms) return@forEach
                if (availabilityType != null) {
                    throw PatchException(
                        "Google sign-in via MicroG-RE: ambiguous availability probe.",
                    )
                }
                availabilityType = cls.type
                availabilityName = method.name
            }
        }
        val availabilityOwner = availabilityType
            ?: throw PatchException(
                "Google sign-in via MicroG-RE: availability probe not found; " +
                    "use a clean Wibuku 1.4.5 (78).",
            )
        val availabilityMethod = availabilityName!!

        // ---- Pass B: apply the redirect. ----

        actionOwners.forEach { (action, owner) ->
            mutableClassDefBy(owner).methods.single {
                it.name == "o" && it.returnType == "Ljava/lang/String;" &&
                    it.parameterTypes.isEmpty()
            }.replaceBody(
                "const-string v0, \"${serviceActions[action]}\"\nreturn-object v0",
            )
        }
        mutableResolver.methods.single { it.name == "<clinit>" }
            .replaceInstruction(chimeraIndex, "const-string v$chimeraRegister, \"$MICROG_CHIMERA\"")
        mutableResolver.methods.single { it.name == bindName }
            .replaceInstruction(
                bindIndex,
                "const-string v$bindRegister, \"${WibukuMicroGManifest.MICROG}\"",
            )
        mutableClassDefBy(availabilityOwner).methods.single {
            it.name == availabilityMethod && it.returnType == "I" &&
                it.parameterTypes == listOf("Landroid/content/Context;", "I")
        }.replaceBody("const/4 v0, 0x0\nreturn v0")
    }
}

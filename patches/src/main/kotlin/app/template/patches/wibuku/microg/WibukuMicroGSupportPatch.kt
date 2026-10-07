package app.template.patches.wibuku.microg

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.template.patches.shared.Constants.WIBUKU_COMPATIBILITY

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
 * (RegisterRequest -> LoginResponse). The auth-code transport talks to the
 * GMS-core identity service, which is exactly what MicroG-RE implements.
 *
 * This patch declares the spoofed package + signer metadata and the MicroG
 * package query so the re-signed app can bind MicroG-RE's service instead of
 * the missing Play Services. It keeps the real AuthorizationRequest scopes,
 * the token exchange and the session handling untouched: authentication
 * still comes from the remote service, nothing is faked locally.
 *
 * EXPERIMENTAL: the in-app GMS transport shape for the identity service has
 * not been device-verified yet. If sign-in fails, capture logcat around the
 * login attempt and check which service binding is rejected.
 */
@Suppress("unused")
val wibukuMicroGSupportPatch = bytecodePatch(
    name = "Google sign-in via MicroG-RE",
    description = "Routes Google sign-in through MicroG-RE 7.1.1+. " +
        "Requires app.revanced.android.gms with the same Google account. " +
        "Experimental; device testing needed.",
    default = true,
) {
    compatibleWith(WIBUKU_COMPATIBILITY)
    dependsOn(wibukuMicroGResources)

    execute {
        // No in-app GMS client override exists for the identity service:
        // Wibuku bundles only the AuthorizationRequest/Scope parcelables and
        // binds the system GMS core directly, which MicroG-RE replaces.
        // The resource patch above is the whole transport fix; this execute
        // block pins the expected app shape so future versions fail loudly
        // instead of shipping a silently broken login.
        WibukuAuthShapeFingerprint.method
    }
}

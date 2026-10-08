# Wibuku 1.4.5 / 1.4.1 patch notes

Target: `wibuku.app.wibuku` **1.4.5 (78)** and **1.4.1 (74)**, XAPK
(APKPure repacks, generic AOSP signer `ec131df0...`, verified identical
SHA-1 on both APKs). DEX is not heavily obfuscated; app classes keep
readable names (`wibuku.app.wibuku.*`, 3 DEX files, ~11k classes total).

Version differences (verified from both APK DEXes):
- Splash gate `C0/H0/I0`, worker string `Tidak bisa masuk saat ini.`,
  `AppUser.isPremium()Z` + `premium J`, auth scopes and all 5 GMS
  service-action getters are identical in both versions.
- Obfuscated owners shifted: response handler `Lnc1;.a` (1.4.5) vs
  `Lic1;.a` (1.4.1); dialog helper `Lh5a;.D(..., Lzw1;)` vs
  `Lmu1;.B(..., Llw1;)`; coroutine params `Lom0;/Lxo1;` vs `Lrm0;/Lqo1;`.
  The tracer ships both fingerprint variants (best-effort via
  `methodOrNull`); functional patches match by shape, not owner names.
- `ResourceResponse` on 1.4.1 has **no `getCode()`** (4 fields instead
  of 5); the tracer skips the CODE tag there to avoid
  `NoSuchMethodError`.

## Login flow (verified from the APK DEX)

1. `IntroFragment` builds a Google Identity `AuthorizationRequest` with
   scopes `email` + `https://www.googleapis.com/auth/userinfo.profile`.
2. Google returns a server auth code; the app sends
   `RegisterRequest(server_auth_code, device_uuid)` to `user/register`.
3. The backend answers with `LoginResponse(user, config, ...)` carrying the
   `session` token and the `AppUser` model.

The GMS transport for the identity service lives in GMS core (bound at
runtime), not in an in-app client class, so there is no in-app
`getStartServicePackage` override point like the Pizza/Calendar patches
have. The MicroG patch therefore ships the spoofed package + signer
metadata and the MicroG package query, letting the re-signed app bind
MicroG-RE's identity service.

## Premium model (verified from the APK DEX)

- `AppUser.premium : J` is a server-issued **expiry timestamp**.
- `isPremium()Z` is 9 instructions: `iget-wide premium`, compare against
  the clock helper, return 1 when `premium > now`.
- The **Premium** patch replaces that body with `const/4 v0, 0x1; return v0`
  after asserting the method still reads the `premium` field, so a future
  app version with a reshaped gate fails loudly instead of mis-patching.
- Client-side gates (premium screens, `premium_required`, 1080P labels)
  follow the forced value. Anything the server re-checks on its own
  endpoints (gift consume, recharge token) still needs a real purchase.

## Testing checklist

- [ ] Patch a clean 1.4.5 XAPK with Premium only; premium screens unlock.
- [ ] Patch with MicroG patch; Google sign-in completes with MicroG-RE
      7.1.1+ installed and the same account in Android + MicroG.
- [ ] Repeated starts, force-stop/relaunch, session persistence.
- [ ] If login fails: capture logcat around the attempt, note which
      service binding is rejected, and update the MicroG patch.

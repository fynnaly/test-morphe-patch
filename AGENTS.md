# Project instructions

## Wibuku working agreement

- Target app is Wibuku (`wibuku.app.wibuku`) **1.4.5 (78)**, XAPK.
- Login is Google Identity Authorization: `IntroFragment` builds an
  `AuthorizationRequest` (scopes `email` + `userinfo.profile`), Google
  returns a server auth code, the backend exchanges it for a session
  (`RegisterRequest` -> `LoginResponse`). Never fake authentication.
- Premium is a server-issued expiry timestamp (`AppUser.premium : J`);
  the patch only forces the local boolean. Server-gated features may
  still require a real purchase; state that accurately.
- Follow the full cycle: change -> build -> patch clean input -> install
  -> launch -> test -> diagnosis -> correction -> full retest.
- Keep full logs locally; present only relevant exceptions with secrets
  redacted. Never commit APKs, keys or raw logs.

# Patches

> Generated from `patches-list.json` - **v1.7.0** (`master`) - **6 patches** across **2 apps** - back to [README](README.md)

---

## Botworld (com.featherweightgames.fx)

**Supported versions:** `1.36.2`

| Patch | Details |
|---|---|
| **Disable tamper guard** | Neutralizes the LuckyPatcher/signature guard so the re-signed app does not kill itself on launch. Does not grant items or currency. |
| **Play offline (guest)** | Disables Play Games sign-in so the game runs as guest without a Google login. Does not grant items or currency. |
| **Skip rewarded ads** | Completes the rewarded-video flow locally so the requested reward is granted without playing an ad. No ad-network impression or revenue is synthesized; IAP purchases still require real payment. |

---

## Wibuku (wibuku.app.wibuku)

**Supported versions:** `1.4.5` `1.4.1`

| Patch | Details |
|---|---|
| **Google sign-in via MicroG-RE** | Routes Google sign-in through MicroG-RE 7.1.1+. Requires app.revanced.android.gms with the same Google account. |
| **Login tracer** | Logs splash/login gate progress via logcat for diagnosis. Disable for release builds. |
| **Premium** | Forces the local Premium check to always pass. Does not activate a subscription on your account; server-gated features may still require a real purchase. |

---

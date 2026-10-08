# Patches

> Generated from `patches-list.json` - **v1.6.0** (`master`) - **4 patches** across **2 apps** - back to [README](README.md)

---

## Botworld (com.featherweightgames.fx)

**Supported versions:** `1.36.2`

| Patch | Details |
|---|---|
| **Disable tamper guard** | Neutralizes the LuckyPatcher/signature guard so the re-signed app does not kill itself on launch. Does not grant items or currency. |

---

## Wibuku (wibuku.app.wibuku)

**Supported versions:** `1.4.5` `1.4.1`

| Patch | Details |
|---|---|
| **Google sign-in via MicroG-RE** | Routes Google sign-in through MicroG-RE 7.1.1+. Requires app.revanced.android.gms with the same Google account. |
| **Login tracer** | Logs splash/login gate progress via logcat for diagnosis. Disable for release builds. |
| **Premium** | Forces the local Premium check to always pass. Does not activate a subscription on your account; server-gated features may still require a real purchase. |

---

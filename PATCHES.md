# Patches

> Generated from `patches-list.json` - **v1.11.0** (`master`) - **10 patches** across **2 apps** - back to [README](README.md)

---

## Botworld (com.featherweightgames.fx)

**Supported versions:** `1.36.2`

| Patch | Details |
|---|---|
| **Ads path tracer** | Logs every Java ads-bridge entry via logcat (tag BOTW) for diagnosis. Proves whether the game reaches the Java layer. Disable for release builds. |
| **Disable tamper guard** | Neutralizes the LuckyPatcher/signature guard so the re-signed app does not kill itself on launch. Does not grant items or currency. |
| **Force GMS rewarded available** | Forces the GMS static RewardedAd.isAdAvailable() probe to true so any direct static callers (bypassing the Unity bridge) also take the reward path. Pairs with the Skip patches; IAP purchases still require real payment. |
| **Play offline (guest)** | Disables Play Games sign-in so the game runs as guest without a Google login. Leaves the ads SDKs initializable so the local Skip patches can complete the reward flow. Does not grant items or currency. |
| **Skip AdMob rewarded ads** | Completes the AdMob rewarded flow locally so the requested reward is granted without loading or playing an ad. Pairs with Skip rewarded ads (AppLovin MAX); IAP purchases still require real payment. |
| **Skip decagon rewarded ads** | Completes the decagon AdMob rewarded flow locally so the requested reward is granted without loading or playing an ad. Pairs with Skip AdMob rewarded ads; IAP purchases still require real payment. |
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

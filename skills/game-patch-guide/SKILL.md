---
name: game-patch-guide
description: "Use when patching Android games or apps."
version: 0.1.0
author: Hermes Agent
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [android, patching, smali, morphe, il2cpp]
    category: software-development
    related_skills: [morphe-patches, android-app-analysis]
---

# Game Patch Guide

Patch Android games/apps without thrashing: triage the gate layer FIRST,
then pick exactly one route (DEX/smali, native .so, or stop). Covers both
Morphe bundles and PC smali (apktool/jadx) workflows.

## When to Use

- User wants premium unlock, DLC unlock, no-ads, free purchase, or reward skip.
- User works on PC with Android Studio / apktool / jadx / smali, or Morphe Manager.
- Don't use for: malware verdicts (android-app-analysis), server-side account recovery.

## The Iron Law

- Classify the gate layer BEFORE writing any patch: DEX, native il2cpp, or server.
- Zero runtime reachability (tracer shows nothing) means wrong layer: stop, don't add hooks.
- After 3 failed fixes on one layer, question the layer, never attempt fix #4 blind.

## Procedure

1. Pin the target. Done when package, version/versionCode, bundle type, signer recorded.
   - APKPure XAPK: read manifest.json; APKM: info.json. Record SHA-1 signer; Play-original vs repack decides purchase promises.
   - Unity il2cpp = libil2cpp.so + global-metadata.dat present.
2. Triage the gate layer. Done when DEX / native / server named with evidence.
   - Grep the gate strings (IsPremium, ad_unavailable, QueueAd, PurchaseSuccess) in ALL DEX files AND in global-metadata.dat.
   - Strings only in metadata + zero DEX hits = native gate. Stop promising DEX patches.
   - Find callers: launchBillingFlow / consumeAsync / acknowledgePurchase / isAdAvailable / showAd / loadAd outside SDK packages. No callers outside SDK = game calls from native via JNI.
   - Check validators: GooglePlayValidator / CrossPlatformValidator / Tangle / receipt classes. Validator in native + repack signer = fake purchase will fail Play (responseCode 5).
   - Check ads SDK presence: ironsource / applovin / unity ads / gms ads bridges. No SDK classes = nothing to strip; "no ads" needs no patch.
3. Prove reachability with a tracer BEFORE functional patches. Done when one tap shows which layer fires.
   - Morphe: opt-in bytecodePatch (default=false) with Log.e at each bridge entry (show/load/isAvailable on every bridge + static GMS + MAX). Use methodOrNull so it degrades gracefully.
   - PC smali: inject Log.e at same entries via apktool, rebuild, tap once.
   - Read: tracer lines present = Java reached, patch can fire. Zero tracer lines + healthy SDK = gate is native, stop DEX permanently.
4. Pick ONE route:
   - DEX/smali route only when tracer fires or callers exist in DEX. Keep patches tiny (force boolean true, fire callback directly), guard on still-present shape, fail loudly on reshape.
   - Native route only with dump tooling (Il2CppDumper + NDK + crash-iteration loop). Find method offset via metadata token, patch branch (tbz->nop) or force return, ship as companion .so + loadLibrary trigger in onCreate. Expect offsets to break every game update.
   - Server route = stop. Session/token/entitlement rejections (RETRY, Invalid token, installer checks) cannot be fixed client-side; say so.
5. Billing fake purchase needs ALL FOUR layers together, never purchase alone.
   - Purchase entry -> fabricated success JSON + fake signature, PLUS Consume->ConsumeSuccess, Acknowledge->AcknowledgeSuccess, Verify->true. Play rejects fake tokens (code 5) without the companions.
   - Works only when grant path lives in Java/C# without strict server receipt check. RSA validators (Tangle/PlayValidator/AppsFlyer) or native-only purchase = stop.
6. Rewarded ads: patch load + availability + show together on EVERY bridge.
   - When every GMS load fails, game shows native dialog before show(); complete loadAd/load locally by firing onRewardedAdLoaded so game proceeds to patched show().
   - Cover classic + decagon + MAX + static GMS. Confirm exact Show class from Unity stack before hooking; patching the uncalled bridge shows succeeded yet never fires.
   - Keep ads SDK init providers ENABLED when Skip patches exist: C# gate checks SDK init before calling Java bridge; killing init short-circuits to false.
7. Verify before claiming. Done when Manager log shows succeeded per patch on exact version AND runtime log shows the callback fired (not just no-crash).
   - Patched == installed is not proof. Require logcat evidence: onUserEarnedReward / onRewardedAdLoaded / purchase success, or tracer lines.
   - Ship failures with: input/output hashes, patch versions, build log lines, tap-count + stack variants, tracer result.

## PC Smali Route (Android Studio / apktool)

- Decompile: apktool d app.apk; read smali; cross-check with jadx for logic, edit smali only.
- Register budget: never introduce a brand-new local after clearing body; reuse type-compatible registers only. New locals or param-type reuse = VerifyError at runtime.
- Rebuild: apktool b, zipalign, apksigner with own key. A repack signature breaks Play billing and signature-pinned logins; warn upfront.
- Same triage + tracer rules apply; smali does not bypass the layer problem.

## Pitfalls

- Patching show() while game stops at QueueAd/load never fires; check load path first.
- Disabling ads init providers kills the bridge the Skip patches need; disable login provider only.
- Unescaped $ in Kotlin descriptors holding inner-class names fails CI compile; write descriptors as literals.
- A standalone resourcePatch never appears in Manager metadata; expose via named bytecodePatch + dependsOn.
- MOD APK sections lie; diff ori vs mod by CRC/md5 (all entries + signer) before copying its recipe.
- MicroG lacks ads dynamite module; ads-dynamite:0 + JavascriptEngine failures on MicroG devices are environment, not patch bugs.

## Verification

- Contract test passes (pins, anchors, guards, brace balance).
- Tracer result recorded: Java reached (lines present) or native-confirmed (zero lines).
- Release built via CI with published .mpp; patched clean input installs and launches.
- No claim of success from build/static check alone on Unity/native targets.

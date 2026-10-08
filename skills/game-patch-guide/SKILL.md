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

## Case Studies (proven verdicts, do not re-litigate)

- Wibuku 1.4.5/1.4.1 (wibuku.app.wibuku, APKPure repack SHA-1 ec13...): login session is server-enforced at panel.wibuku.app (user/register -> user/session). Tracer showed STATUS RETRY + CODE empty + DATA null + ERR Refresh Token 19x; clear-data + ori APK failed identically = server-side block. Local isPremium true never unlocks a server-rejected session. Installer-origin (getInstallerPackageName / Playstore) + non-Play signer also rejected. MicroG route needs full transport rewrite (5 GMS actions + chimera + availability force), never manifest-only; repack signer risks UNREGISTERED_ON_API_CONSOLE. Porting 1.4.5->1.4.1 needs adaptive fingerprints (F0 Lom0 vs Lrm0, getCode only on 1.4.5).
- Botworld 1.36.2 (Unity il2cpp, 621MB XAPK): chest reward gate is native QueueAd returning false before any Java bridge; tracer tag BOTW zero lines across 4 logs (81+119+58+1 taps) with healthy SDK (jsLoaded, no failed loads) = confirmed native. 5 DEX versions (1.6.0->1.10.1) all succeeded yet never fired. Same for purchase: Play responseCode 5 DEVELOPER_ERROR on repack signer. Stop DEX permanently once tracer is zero.
- SFS 1.6.00.22 (Unity il2cpp, 103MB): get_IsPremium/get_IsOwned/expansion_bundle_* only in global-metadata.dat, zero DEX callers, zero validators in DEX, zero ads SDKs. MOD reference diffed binary-identical (1302/1302 files, same DEX/.so/metadata md5, same dev signer Stefo Mai Morojna) = fake mod, no recipe to copy. Same native verdict as Botworld.
- ITD2 1.87.1 (positive control): IronSource LevelPlay bridge IS called by game, so capture-listener + fire displayed/rewarded/closed works; PikPok AndroidStore single entry allows 4-layer fake purchase. Copy this shape only when triage shows Java callers.

## Environment Constraints (this server)

- Termux + PRoot Debian, ~3.7GB RAM / ~1.9GB free, no NDK, no Android SDK, no Il2CppDumper, no ADB iteration. Androguard full AnalyzeAPK times out on 10k-class DEX: loop DEX files directly with DEX(open(rb).read()), LOGURU_LEVEL=ERROR + logging.disable, never AnalyzeAPK first.
- Native .so companion (okish pattern: rawResourcePatch + loadLibrary in onCreate, tbz->nop) needs NDK r27 + dump tooling + crash-iteration on device; cannot be done blind from here. State this cost instead of attempting.
- Large downloads (>20MB Telegram cap, slow mirrors like modyolo ~10-30KB/s): prefer parallel range curl, keep ori APK + libil2cpp.so + metadata locally for triage without re-download.

## Solutions Per Layer

- DEX-gated (Pizza/Coffee/ITD2-LevelPlay shape): force boolean, fire callback directly, cover every bridge, keep init providers on, verify via logcat callback.
- Native-gated: move to PC (Android Studio + apktool/jadx + Il2CppDumper + NDK), find method offset via metadata token, patch branch/return, companion .so, crash-loop test. Offsets break every update; pin version tight.
- Server-gated: stop client patches; vary version/account/network/patched-vs-ori as 4-way control to prove server-side, then say so.
- Environment-gated (Private DNS AdGuard, MicroG missing ads dynamite, stale WebView): fix device first (disable Private DNS / allowlist googlesyndication + doubleclick, update WebView, freeze MicroG for test) before touching patches.

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

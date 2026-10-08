# Botworld 1.36.2 patch notes

Target: `com.featherweightgames.fx` **1.36.2 (171310)**, XAPK
(APKPure repack, signer SHA-1 `380031d3...`). Unity il2cpp game:
`libil2cpp.so` 49MB + `libunity.so` 26MB + `global-metadata.dat` 13.8MB.
Game logic (coins, scrap, gems, premium) lives in native code, not DEX.

## Patch scope (option A)

Patches stay on DEX-side surfaces only (ads/login/guard). The economy is
native + usually server-validated, so unlimited-currency promises fail;
none is made here.

1. **Disable tamper guard** (bytecode): wipes
   `Lcom/featherweightgames/plugins/LuckyPatcher;`
   `checkIntegrity`/`checkPackages`/`checkSignature` to `return-void`
   after asserting the `killProcess` + `com.dimonvideo.luckypatcher`
   anchors are still present. Without this the re-signed app kills
   itself on launch when Lucky Patcher packages or a foreign signature
   are detected.
2. **Play offline (guest)** (resource): disables `PlayGamesInitProvider`
   only. The ads init providers stay enabled on purpose: the game's C#
   gate (`GoogleAdService.QueueAd`) needs the ads SDK initialized before
   it calls the Java bridge, so killing init would short-circuit the gate
   to false before the Skip patches ever fire.
3. **Force GMS rewarded available** (bytecode, 2 instructions): forces
   the GMS static `RewardedAd.isAdAvailable(Context, String)` to true,
   covering direct static callers that bypass the Unity bridge.
3. **Skip rewarded ads** (bytecode, pure smali, no extension): replaces
   `MaxRewardedAd.showAd(placement, customData)` with a local completion
   that synthesizes a `u3` MaxAd + `MaxRewardImpl.createDefault()` and
   fires the SDK's own `MaxRewardedAdImpl$b.onUserRewarded` +
   `onAdHidden`. `isReady()` is forced true. No ad-network impression,
   revenue, click or postback is synthesized; the game grants its fixed
   per-placement reward in il2cpp on the callback.
4. **Skip AdMob rewarded ads** (bytecode, pure smali): forces
   `UnityRewardedAd.isAdAvailable()` true, completes `loadAd()` locally
   by firing `onRewardedAdLoaded()` (this flips the game's own flag so
   it proceeds to `show()`), and replaces `show()` with a direct
   `UnityRewardedAdCallback.onUserEarnedReward("", 1.0)` call.
5. **Skip decagon rewarded ads** (bytecode, pure smali): same shape on
   the decagon bridge (`load()` → `onRewardedAdLoaded()`,
   `show()` → `onUserEarnedReward("", 1.0)`).

## Not covered (option B remainder)

- **Rewarded ads via AppLovin MAX** (`MaxRewardedAdImpl`, listener held
  across the JNI bridge): needs a deeper dump of `MaxFullscreenAdImpl`
  show path + who holds `MaxRewardedAdListener` before the DEX-side
  reward synthesis can be written. Tracked separately.
- **IAP for free**: Unity Purchasing + Play receipt validation; not
  patchable from DEX. No patch claims this.

## Testing checklist

- [ ] Patch a clean 1.36.2 XAPK with both patches; app launches, no
      killProcess on start (with and without Lucky Patcher installed).
- [ ] No Play Games sign-in popup; game runs as guest offline.
- [ ] Repeated starts, force-stop/relaunch, save persistence.

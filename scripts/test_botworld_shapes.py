#!/usr/bin/env python3
"""Static contract checks for the Botworld patches (no APK needed).

Verifies the source-level invariants the patches rely on:
- Constants pin com.featherweightgames.fx / 1.36.2 / versionCode 171310.
- Anti-tamper patch targets LuckyPatcher.checkIntegrity/checkPackages/
  checkSignature and wipes them only after asserting the killProcess +
  anchor strings are still present.
- Offline-login patch disables the PlayGamesInitProvider in the manifest
  with a clean-APK guard.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "patches/src/main/kotlin/app/template/patches/botworld"

CHECKS = []


def check(name):
    def deco(fn):
        CHECKS.append((name, fn))
        return fn
    return deco


def read(rel):
    return (SRC / rel).read_text(encoding="utf-8")


@check("compatibility pins botworld 1.36.2/171310")
def _():
    src = (ROOT / "patches/src/main/kotlin/app/template/patches/shared/Constants.kt").read_text()
    assert 'packageName = "com.featherweightgames.fx"' in src, "package mismatch"
    assert 'version = "1.36.2"' in src, "version mismatch"
    assert "versionCode = 171310" in src, "versionCode mismatch"


@check("anti-tamper targets LuckyPatcher guards with kill anchors")
def _():
    src = read("antitamper/BotworldAntiTamperPatch.kt")
    assert "Lcom/featherweightgames/plugins/LuckyPatcher;" in src
    assert '"checkIntegrity"' in src and '"checkPackages"' in src and '"checkSignature"' in src
    assert "killProcess" in src, "missing killProcess guard"
    assert "com.dimonvideo.luckypatcher" in src, "missing LP package anchor"
    assert "return-void" in src and "replaceBody" in src


@check("offline-login disables PlayGames provider with guard")
def _():
    src = read("offline/BotworldOfflineLoginPatch.kt")
    assert "PlayGamesInitProvider" in src, "missing provider anchor"
    assert "MobileAdsInitProvider" not in src.replace(
        "MobileAdsInitProvider and AppLovinInitProvider are intentionally", ""
    ).replace("MobileAdsInitProvider`, `AppLovinInitProvider`) alongside", ""), \
        "ads init providers must stay enabled so the skip patches can fire"
    assert "android:enabled" in src and '"false"' in src
    assert "bytecodePatch" in src, "must be a named bytecodePatch to appear in metadata"
    assert 'name = "Play offline (guest)"' in src, "patch name mismatch"
    assert "dependsOn(botworldOfflineResources)" in src, "missing resource dependency"
    assert "171310" in src, "missing version guard"


@check("rewarded-ads completes locally with synthetic ad+reward")
def _():
    src = read("rewards/BotworldRewardedAdsPatch.kt")
    assert "Lcom/applovin/mediation/ads/MaxRewardedAd;" in src
    assert '"showAd"' in src and '"isReady"' in src
    assert '"onUserRewarded"' in src, "missing reward forward"
    assert "MaxRewardImpl" in src and "createDefault" in src, "missing reward factory"
    assert "Lcom/applovin/impl/u3;" in src, "missing synthetic ad"
    assert "check-cast" in src, "missing verifier check-cast"
    assert "onAdHidden" in src, "missing hide completion"
    assert "171310" in src, "missing version guard"


@check("admob rewarded completes locally via unity bridge")
def _():
    src = read("admob/BotworldAdMobRewardPatch.kt")
    assert "Lcom/google/unity/ads/UnityRewardedAd;" in src
    assert '"show"' in src and '"isAdAvailable"' in src and '"loadAd"' in src
    assert '"onUserEarnedReward"' in src, "missing reward callback"
    assert '"onRewardedAdLoaded"' in src, "missing load completion"
    assert "UnityRewardedAdCallback" in src
    assert "runOnUiThread" in src, "missing show guard"
    assert "171310" in src, "missing version guard"


@check("decagon rewarded completes locally via decagon bridge")
def _():
    src = read("decagon/BotworldDecagonRewardPatch.kt")
    assert "Lcom/google/unity/ads/decagon/UnityRewardedAd;" in src
    assert '"show"' in src and '"load"' in src
    assert '"onUserEarnedReward"' in src, "missing reward callback"
    assert '"onRewardedAdLoaded"' in src, "missing load completion"
    assert "decagon/UnityRewardedAdCallback" in src
    assert "UnityAdBase" in src, "missing base-class callback field"
    assert "check-cast" in src, "missing verifier check-cast"
    assert "runOnUiThread" in src, "missing show guard"
    assert "171310" in src, "missing version guard"


@check("gms static availability forced true")
def _():
    src = read("gms/BotworldGmsStaticPatch.kt")
    assert "Lcom/google/android/gms/ads/rewarded/RewardedAd;" in src
    assert '"isAdAvailable"' in src
    assert "STATIC" in src, "must target the static probe"
    assert "android/content/Context" in src
    assert "const/4 v0, 0x1" in src, "must force true"
    assert "171310" in src, "missing version guard"


@check("ads path tracer logs bridge entries")
def _():
    src = read("tracer/BotworldAdsTracerPatch.kt")
    assert '"BOTW"' in src, "missing log tag"
    assert "methodOrNull" in src, "tracer must degrade gracefully"
    assert "UnityRewardedAd" in src and "decagon" in src
    assert "MaxRewardedAd" in src
    assert "default = false" in src, "tracer must be opt-in"


@check("kotlin files balance braces/parens")
def _():
    for path in sorted(SRC.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        for a, b in (("{", "}"), ("(", ")"), ("[", "]")):
            assert text.count(a) == text.count(b), f"{path.name}: {a}{b} imbalance"


def main():
    failed = 0
    for name, fn in CHECKS:
        try:
            fn()
            print(f"PASS {name}")
        except AssertionError as exc:
            failed += 1
            print(f"FAIL {name}: {exc}")
    print(f"{len(CHECKS) - failed}/{len(CHECKS)} checks passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())

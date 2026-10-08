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
    assert "android:enabled" in src and '"false"' in src
    assert "resourcePatch" in src
    assert "171310" in src, "missing version guard"


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

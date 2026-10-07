#!/usr/bin/env python3
"""Static contract checks for the Wibuku patches (no APK needed).

Verifies the source-level invariants the patches rely on:
- Constants pin wibuku.app.wibuku / 1.4.5 / versionCode 78.
- Premium fingerprint targets AppUser.isPremium()Z and the patch guards
  on the `premium` long field before replacing the body.
- MicroG manifest pins the APKPure-repack signer and adds the spoofed
  metadata + package query; the auth-shape fingerprint pins IntroFragment
  with both login scope strings and the AuthorizationRequest construction.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "patches/src/main/kotlin/app/template/patches/wibuku"

CHECKS = []


def check(name):
    def deco(fn):
        CHECKS.append((name, fn))
        return fn
    return deco


def read(rel):
    return (SRC / rel).read_text(encoding="utf-8")


@check("compatibility pins wibuku 1.4.5/78")
def _():
    src = (ROOT / "patches/src/main/kotlin/app/template/patches/shared/Constants.kt").read_text()
    assert 'packageName = "wibuku.app.wibuku"' in src, "package mismatch"
    assert 'version = "1.4.5"' in src, "version mismatch"
    assert "versionCode = 78" in src, "versionCode mismatch"


@check("premium fingerprint targets AppUser.isPremium")
def _():
    src = read("premium/Fingerprints.kt")
    assert "Lwibuku/app/wibuku/model/user/AppUser;" in src
    assert '"isPremium"' in src and 'returnType = "Z"' in src


@check("premium patch guards on premium:J field")
def _():
    src = read("premium/WibukuPremiumPatch.kt")
    assert 'field.name == "premium"' in src and 'field.type == "J"' in src
    assert "const/4 v0, 0x1" in src and "return v0" in src
    assert "replaceBody" in src


@check("microg manifest pins repack signer + spoof metadata")
def _():
    src = read("microg/WibukuMicroGManifest.kt")
    assert "ec131df0ce4e569a0fef40a1f5e6ef74d4d1e8e7" in src, "signer mismatch"
    assert "SPOOFED_PACKAGE_NAME" in src and "SPOOFED_PACKAGE_SIGNATURE" in src
    assert "MICROG_PACKAGE_NAME" in src


@check("auth-shape fingerprint pins IntroFragment scopes")
def _():
    src = read("microg/Fingerprints.kt")
    assert "Lwibuku/app/wibuku/ui/intro/IntroFragment;" in src
    assert '"email"' in src
    assert "https://www.googleapis.com/auth/userinfo.profile" in src
    assert "AuthorizationRequest" in src


@check("microg patch redirects transport, not just metadata")
def _():
    src = read("microg/WibukuMicroGSupportPatch.kt")
    assert "authorization.START" in src and "identity.service.signin.START" in src
    assert "app.revanced.android.gms" in src
    assert "CHIMERA_AUTHORITY" in src and "setPackage" in src
    assert "classDefForEach" in src
    # availability probe forced to SUCCESS
    assert "const/4 v0, 0x0" in src


@check("debug tracer logs at gate entries, opt-in only")
def _():
    src = read("debug/WibukuDebugPatch.kt")
    assert 'name = "Login tracer"' in src, "patch name mismatch"
    assert "default = False".replace("False", "false") in src, "debug patch must default to false"
    assert 'Landroid/util/Log;->e' in src, "no Log.e injection"
    assert "addInstructions" in src, "no addInstructions call"
    for cls, m in (
        ("Lwibuku/app/wibuku/ui/splash/SplashFragment;", '"C0"'),
        ("Lwibuku/app/wibuku/ui/splash/SplashFragment;", '"H0"'),
        ("Lwibuku/app/wibuku/model/user/AppUser;", '"isPremium"'),
    ):
        assert cls in src and m in src, f"missing fingerprint {cls}{m}"
    assert "WIBUKU-DLG-MSG" in src, "missing dialog message log"
    assert "WIBUKU-ERR" in src, "missing response error log"
    assert "Lnc1;" in src and "ResourceResponse;" in src, "missing response handler fingerprint"


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

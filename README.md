# Wibuku Morphe Patches

[![Release](https://img.shields.io/github/v/release/fynnaly/test-morphe-patch)](https://github.com/fynnaly/test-morphe-patch/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/fynnaly/test-morphe-patch/release.yml?label=build)](https://github.com/fynnaly/test-morphe-patch/actions/workflows/release.yml)
[![License](https://img.shields.io/github/license/fynnaly/test-morphe-patch)](LICENSE)

My collection of Wibuku patches for [Morphe](https://morphe.software). Each patch can be selected separately; supported versions are listed below.

## Installation

[Add this source to Morphe](https://morphe.software/add-source?github=fynnaly/test-morphe-patch), or add the repository manually:

```text
https://github.com/fynnaly/test-morphe-patch
```

Choose a supported app version, select the patches you want and patch a clean APK or XAPK. After a source update, rebuild the app to apply the changes.

## Patches

<!-- PATCHES_START -->
> **[v1.0.1](https://github.com/fynnaly/test-morphe-patch/releases/tag/v1.0.1)**&nbsp;&nbsp;&middot;&nbsp;&nbsp;`master`&nbsp;&nbsp;&middot;&nbsp;&nbsp;**2 patches** across **1 app**&nbsp;&nbsp;&middot;&nbsp;&nbsp;[Full details](PATCHES.md)

| # | App | Patches | Version | Package |
|---|---|---|---|---|
| 1 | [**Wibuku**](PATCHES.md#wibuku-wibukuappwibuku) | 2 | `1.4.5` | [`wibuku.app.wibuku`](https://play.google.com/store/apps/details?id=wibuku.app.wibuku) |
<!-- PATCHES_END -->

### Wibuku

Version **1.4.5 (78)**, XAPK. **Premium** forces the local expiry check
(`AppUser.isPremium()`) to always pass. It does not activate a subscription
on your account; server-gated features may still require a real purchase.

**Google sign-in via MicroG-RE** routes Google Identity sign-in through
MicroG-RE 7.1.1+ (`app.revanced.android.gms`). Install MicroG-RE, add the
same Google account in Android and in MicroG-RE, then patch a clean XAPK.
Experimental; device testing needed. See [notes and testing](docs/wibuku.md).

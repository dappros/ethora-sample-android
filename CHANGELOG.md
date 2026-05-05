# Changelog

All notable changes to this package are documented here. For cross-SDK release notes, see [ethora/RELEASE-NOTES.md](https://github.com/dappros/ethora/blob/main/RELEASE-NOTES.md).

---

## [26.04.21]

Major sample app refresh to align with Android SDK 26.04.21:

- **Refactored:** Package namespace moved from `com.ethora.sample` → `com.ethora.samplechatapp` ([`74d0521`](https://github.com/dappros/ethora-sample-android/commit/74d0521))
- **New:** Firebase push notifications wired in — new `EthoraApplication.kt`, `EthoraFirebaseMessagingService.kt`, AndroidManifest entries
- **New:** `MainActivity.kt` rewritten as full playground-style sample (881 lines) exercising SDK features
- **API:** `buildConfigField` schema updated — added `ETHORA_USER_JWT` and `ETHORA_ROOM_JID` (for JWT-login and single-room mode), removed `ETHORA_APP_TOKEN`

## [26.03.21]

- **New:** Monthly release workflow publishing `vYY.MM` tags ([`e993c24`](https://github.com/dappros/ethora-sample-android/commit/e993c24))

## [26.03.20]

- **New:** Initial sample app — Ethora Android SDK quickstart ([`b6773fc`](https://github.com/dappros/ethora-sample-android/commit/b6773fc))

# Audited R6 source backup

This branch backs up the complete source used for NewTube Test R6, including the R4 player/upload-alert reconstruction, R5 video context menus, and R6 collapsed/expanded queue retention fixes.

- Original tested commit: `32488c6f92c138152b0987c3516d1712c1b74f04`.
- Tested source tree: `94304de0542e8cf8d459591826846289d3239708`.
- Validation: 154 targeted checks passed (88 phone/UI and 66 shared player/queue); physical-device acceptance remains necessary.
- Test APK version: `1.17.1-test-r6`, versionCode `11706`, ARM64, minSdk 24, targetSdk 37.
- APK SHA-256: `2285709b85ffd66ed56bfc47a4772985c70d2d93c7c0f1d47854a919f264f0b7`.

The source files in this backup match the tested tree exactly. The only additions are this README and the original-history bundle under `backups/r6/`.

## Original history

`NewTube-R6-source.bundle` preserves the original reconstruction and fix commits, including their no-reply author identities. It requires queue base `d75ea5c27e0c44d35eb7245f6b2a70d282a60be8`, which is an ancestor of this backup branch. To import those original commits into a separate local branch:

```sh
git fetch ./backups/r6/NewTube-R6-source.bundle recovery/audited-r6:backup/original-r6
```

This branch is a source backup in the personal fork. It does not update the existing queue submission branch or a maintainer PR.

## Build and tests

Use JDK 21, Android SDK 37 and the checked-in Gradle wrapper. Initialize the submodules first. Normal upstream debug builds do not need private test signing credentials.

```sh
git submodule update --init --recursive
./gradlew :smarttubetv:testStmobileDebugUnitTest --tests '*RelatedVideoMenuTest' --tests '*Queue*Test' --tests '*CardMenuMigrationTest' --tests '*PressIntentDetectorTest'
./gradlew :common:testDebugUnitTest --tests '*VideoStateController*Test' --tests '*VideoLoaderController*Test' --tests '*Playlist*Test' --tests '*QueuePlaybackModeTest'
```

To reproduce the separately signed NewTube Test package, use the existing `tools/local-phone-test.init.gradle` and provide your private signing-credentials file outside the clone. Never generate a replacement key for an in-place update. No signing credentials, APKs, account tokens or local build caches are included here.

## Known limits

The queue remains in memory and may be lost if Android kills the process. The R6 visibility fixes do not add process-death queue persistence. Full-project lint has existing findings; targeted checks are not a complete device, battery, codec or security certification.

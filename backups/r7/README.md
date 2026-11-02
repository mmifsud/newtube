# Audited R7 source backup

R7 persists the local playback queue across process restarts and future in-place updates. It retains R5 video menus and R6 queue visibility fixes.

- Original tested commit: `6e6d59f2c562e4fa7c4394dcaf753a28ce1cc7b9`.
- Exact tested source tree: `6dc845a140efc5a8ea00e426cbe221bd59487cbd`.
- Validation: 221 targeted checks passed (154 phone/UI, 67 shared player/queue).
- APK: `1.17.1-test-r7`, code `11707`, ARM64, minSdk 24, targetSdk 37.
- APK SHA-256: `0e88176c43006f41b9e41592bb8f95785053c09545d33f2a456e01f5c021f205`.

See [the persistence audit](../../docs/reviews/r7-persistence.md) for storage bounds, privacy, tests and limitations. The first update from R6 cannot transfer its unsaved in-memory queue; persistence applies after R7 is installed. Restoration never starts playback automatically.

The source matches the tested tree. Only this README, the audit and `backups/r7/NewTube-R7-source.bundle` are extra. The bundle preserves original commit identities and requires queue base `d75ea5c27e0c44d35eb7245f6b2a70d282a60be8`, reachable in this branch's ancestry. Import original history into a separate local branch with:

```sh
git fetch ./backups/r7/NewTube-R7-source.bundle recovery/audited-r7:backup/original-r7
```

Use JDK 21, Android SDK 37 and initialized submodules. To reproduce the targeted checks:

```sh
git submodule update --init --recursive
./gradlew :smarttubetv:testStmobileDebugUnitTest --tests '*RelatedVideoMenuTest' --tests '*Queue*Test' --tests '*CardMenuMigrationTest' --tests '*PressIntentDetectorTest' --tests '*PlaybackRecoveryTest' --tests '*MobilePlaybackLaunchTest'
./gradlew :common:testDebugUnitTest --tests '*VideoStateController*Test' --tests '*VideoLoaderController*Test' --tests '*Playlist*Test' --tests '*QueuePlaybackModeTest'
```

Normal debug builds require no private signing credentials. Personal acceptance packaging uses `tools/local-phone-test.init.gradle` with the existing private signing backup outside the clone. No signing credentials, runtime queue files, account tokens, APKs or build caches are included. No maintainer submission branch is changed.

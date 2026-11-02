# Private R4 reconstruction and audit

## Provenance and scope

Base: queue PR #124 head `d75ea5c27e0c44d35eb7245f6b2a70d282a60be8`, including the original #102 commit and attribution to PimpinPumpkin. No changes to that submission branch are required for this private test build.

The original local R3 source objects were lost. R4 reconstructs their missing behaviour from the exact R3 APK (SHA-256 `a35e19e54a767325d50800b4f0e536deb1315d56fa4811460a4dae9f36156f8d`), recorded implementation details, and the existing source. It is reviewed reconstructed source, not a byte-identical recovery of R3. Decompiled output was not accepted without checking it: control flow, inlined constants, checked exceptions, callbacks and response cleanup required repair.

Commits separate player recovery, upload alerts, and private test packaging. New commits use Martin Mifsud's GitHub no-reply identity. Signing material is external to the repository.

## Player recovery

- A session owns its Surface and SurfaceTexture; per-view leases distinguish borrowed session textures from disposable view textures even when the bridge deactivates. The decoder switches to a replacement before the old output is released. Failed replacements release their temporary Surface. The player is destroyed before the session's native resources; release is idempotent.
- Saved Activity state stores only a validated normal video ID, bounded position, paused/playing flag and hashed account scope. No stream URLs, OAuth credentials or raw account identifiers are saved. Incognito, cast, remote, local, live and upcoming sessions are excluded.
- Restoration occurs only when there is no live selection or pending user open. New user selections take precedence. A missing recoverable selection uses the existing browsing fallback.
- Controller-driven reloads preserve the play flag. Recreated engines apply that flag. This does not force every buffering video to reload or add a retry timer.
- The temporary queue is still in memory; process-death queue persistence remains outside this patch.

## Local upload alerts

- Explicit per-channel selections are device-local, separate from YouTube bell settings and shared between accounts in this test installation. Automatic checks default off; Sync now also works with automatic checks off.
- Uses existing WorkManager infrastructure. Automatic checks default to every three hours on unmetered networks with a non-low battery. Manual checks also respect the selected network restriction. Android may delay work. No continuous timer, external relay, Firebase, new service or new permission is introduced.
- Sequential feed requests have an eight-second call deadline, no HTTP redirects, no connection-failure retry, and HTTP/2 ping interval zero. Responses close on all paths; cancellation cancels the active call. A process-local lock prevents concurrent automatic/manual batches; contention retries are bounded.
- Only fixed HTTPS public YouTube feed URLs with validated canonical channel IDs are requested. The media client starts without account cookies or InnerTube authorization interceptors. Conditional cache headers are bounded and reject control characters. This does not anonymize the device's network connection or configured proxy.
- Feed size, entry count, title length, channel selections and seen-video history are bounded. DTDs, malformed/truncated input and mismatched channel IDs are rejected. Returned links and images are never fetched.
- The first successful feed check establishes a baseline. Fetch/parse/notification failures do not advance it. Selection generations invalidate results after unsubscribe/re-add. HTTP 429 stops the batch. Stable per-channel notification tags avoid creating unbounded notifications; tap intents are explicit and immutable.
- Channel selection uses the presenter's channel ID, not a possibly absent source card, and refreshes when the screen resumes. Settings observe work through the view lifecycle and release list adapters and view references on destruction.
- Public feeds have limited history. Delayed older uploads may be missed, and checks do not provide instant delivery or the account notification inbox.

## Verification

- ARM64 debug test APK assembled. Same package `io.github.aleixrodriala.arc.resumetest`, label NewTube Test, orange icon, R3 signer, minSdk 24 and targetSdk 37. Version is `1.17.1-test-r4`, code 11704. Automatic in-app updating remains disabled.
- 196 passing targeted tests, no failures/errors/skips: 132 phone cases and 64 shared player/queue cases. New recovery/feed/settings suites cover simulated API 24, 28 and 35. Per-suite results are recorded in `r4-verification.json`.
- APK signing verified. Permissions match R3. All four native libraries match R3 byte-for-byte. No keystore or signing credentials are packaged. ZIP integrity verified.
- Full lint is not a clean gate: the repository has existing API/constant/translation findings, and the alert strings still lack translations beyond English and Spanish. No lint baseline or suppression was added. Reconstruction-specific numeric Android constants were replaced with named constants.
- Compilation first required restoring the toolchain and clearing stale generated DEX output from the older checkout. No source or user data was deleted for those build repairs.
- No usable emulator or physical device test was performed. Native GL/codec behaviour, long-idle recovery, heap retention, battery use, and background delivery under OEM restrictions remain unverified.

## Security status

Play Protect flagged R3. Local checks have not identified the reason or established that it is a false positive. R4 is not a fix for that classification and may receive the same warning. Tests and checksum consistency are not a malware safety certification. No APK has been submitted to Google or another scanner, and no maintainer PR, comment or release has been created or updated.

## Reproduction

Use JDK 21, Android SDK 37 and the checked-in Gradle wrapper. Keep the existing private R3 signing backup outside the clone. Never generate a replacement key silently.

```bash
./gradlew -I tools/local-phone-test.init.gradle -PphoneTestCredentials=/absolute/private/path/signing-credentials.json :smarttubetv:assembleStmobileDebug
./gradlew :smarttubetv:testStmobileDebugUnitTest --tests '*UploadAlertRecoveryTest' --tests '*UploadAlertSettingsRecoveryTest' --tests '*PlaybackRecoveryTest' --tests '*MobilePlaybackLaunchTest' --tests '*Queue*Test' --tests '*CardMenuMigrationTest'
./gradlew :common:testDebugUnitTest --tests '*VideoStateController*Test' --tests '*VideoLoaderController*Test' --tests '*Playlist*Test' --tests '*QueuePlaybackModeTest'
./gradlew :smarttubetv:lintStmobileDebug
```

The default upstream debug command is not the separately signed test installation. Use the packaging init script for an acceptance APK.

# NewTube Test R7 — durable local queue

R7 adds local queue persistence while retaining the R5 menu and R6 visibility fixes. Video IDs, bounded titles/authors/badges, live/upcoming display flags, queue order and the selected current index are saved. Restoration never starts playback automatically. The restored current item remains selectable in the queue sheet and is included in the collapsed bar's remaining count.

## Storage and reliability

- One versioned JSON file, `mobile-queue-v1.json`, in Android's private no-backup directory. It survives in-place app updates with the same package/signing identity. It is not cloud-synced, backed up by Android, or exported to shared storage. Uninstalling or clearing app storage deletes it.
- Structural/current-item changes trigger saves. A main-thread post coalesces bursts; a background worker writes with Android AtomicFile. There is at most one in-flight and one pending immutable snapshot, so slow storage cannot build an unbounded task queue. No periodic timer, progress-position polling, new permission, service, dependency or network request is added.
- Clearing the queue writes an empty snapshot; queued older writes cannot resurrect it. Interrupted atomic writes recover the previous complete file. Writes are asynchronous: a kill before a pending write finishes can recover the previous complete snapshot rather than the very latest edit.
- Load happens once, before the first Activity, using a bounded read. An already selected in-memory queue takes precedence. Unsupported versions, malformed/truncated files, invalid IDs/indexes/types, overlong metadata, more than 300 entries, and files over 512 KiB are ignored safely. The existing per-device Playlist capacity still applies.
- The file stores no stream URLs, OAuth/account credentials, raw thumbnail URLs, local file paths or playback engine objects. Thumbnails are derived from validated video IDs when the queue is displayed. Local-file and remote/cast entries are excluded. Valid queued live/upcoming video IDs can be restored for later selection.
- Incognito playback does not overwrite the saved normal queue. IDs observed in private playback remain excluded from later snapshots while present in memory, even after transient incognito flags are consumed. A mixed queue can therefore restore only its eligible non-private entries.
- Queue scope remains shared within this installation, matching the existing in-memory queue. This change does not add an account-switching policy or cross-device sync. Playback seek position remains handled by the existing player state mechanism; queue persistence saves the queue cursor, not a new per-second playback checkpoint.

## Verification

**221 targeted checks passed**, with zero failures, errors or skips: 154 phone/UI checks and 67 shared player/queue checks. New persistence/codec coverage includes 39 cases across simulated API 24, 28 and 35: cold/update-style state recreation, Unicode titles, order/cursor preservation, clear without resurrection, slow-writer coalescing, incognito exclusion, interrupted AtomicFile writes, corrupt/future/oversized files, strict field validation and preserving an existing in-memory selection. UI/model regressions also verify the restored current item is selectable without falsely claiming it is playing, the bar does not skip it, and duplicate-ID selection keeps the intended object/index.

Signed ARM64 debug APK assembly succeeded. Actual manifest: `io.github.aleixrodriala.arc.resumetest`, version `1.17.1-test-r7`, versionCode `11707`, label NewTube Test, minSdk 24, targetSdk 37. Signature verified with the R3–R6 certificate `945ffbd54b034ffbbf349e17d72130b18e3e4163d3d0991287b736a45a7488c3`. Permissions match R6 and all four native libraries are byte-identical to R6. ZIP integrity passed; signing credentials are absent. The new persistence code/file name and restored-queue state are present in DEX. In-app updating remains disabled.

No physical-device APK upgrade, OEM background-kill, battery or codec test was performed. Full-project lint was not rerun; existing project findings remain. These tests validate the storage and UI paths under simulation, not every device's filesystem/kill timing.

## First update and acceptance

**R6 has no saved queue to migrate.** Note queued titles before installing R7 over R6, without uninstalling or clearing data. Once R7 is installed, build the queue again. Subsequent in-place updates can retain it. Restoring the queue will not start audio/video until you select an item.

Check add/reorder/remove/clear, normal background/foreground use, and restoration after a later update or a stopped process. Verify the selected entry and order, and that Clear does not bring old entries back on reopening. Private-mode queue entries should not appear in the restored normal queue.

## Source

Original tested source commit: `6e6d59f2c562e4fa7c4394dcaf753a28ce1cc7b9`, branch `recovery/audited-r7`. `NewTube-R7-source.bundle` preserves the original commits and requires queue base `d75ea5c27e0c44d35eb7245f6b2a70d282a60be8`. It contains no signing material or APK. The personal GitHub backup uses a separate snapshot commit, with this original-history bundle included. No maintainer submission is changed.

APK SHA-256: `0e88176c43006f41b9e41592bb8f95785053c09545d33f2a456e01f5c021f205`.

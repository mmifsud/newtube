# Queue pre-submission review

Reviewed `6f96a1b42bb9272935360d7e5aec34d35f2753f0...5009ac58` at the owner's request. This covers the original queue sheet, queue improvements and R6/R7 backup commits. The initial review changed no runtime code; the verified follow-up fixes are recorded below. Package identity and signing configuration remain unchanged. No maintainer issue, branch or PR was changed.

## Verified follow-up fixes

After the owner requested continuation, all three queue findings below were fixed and their regression tests made green:

- `Playlist.add()` and `next()` now remove an existing occurrence internally and publish only the completed state. Persistence therefore retains private exclusions through requeue operations, without a new timer or an unbounded memory set.
- Removal resolves the actual object first, retaining equality fallback for callers using equivalent clones. Sheet edits remove the selected duplicate rather than an earlier equal occurrence.
- User-facing removal goes through shared `canRemoveQueuedItem`/`removeQueuedItem` checks. The menu checks both when offering the action and when executing it. A restored cursor remains removable because restoration does not start playback; internal model replacement/removal remains available.
- **237 targeted cases passed: 167 phone/UI and 70 common/player/queue; zero failures, errors or skips.** This includes all original 221 cases, the 10 cases previously demonstrated red, and six compatibility/completed-state checks.
- Follow-up Standards review: zero actionable findings; no hard violations or introduced correctness issues identified. Follow-up Spec review: zero actionable findings; the three intended behaviours and compatibility cases are covered.

The findings and red-stage evidence below describe the reviewed R7 state before these fixes. The separate saved-playback restoration finding remains outside this queue fix. No signed acceptance APK was built or installed, and physical-device battery/wake-up measurements remain outstanding. The existing private signing key was not accessed or replaced.

The two independent review passes assessed documented standards and intended behaviour separately. Behaviour sources: `queue-validation.md`, `r7-persistence.md` and, for the separate recovery changes, `r4-reconstruction.md`. Standards sources: the workspace's ancestor `AGENTS.md`, root `CLAUDE.md` and the relevant mobile handoff rules. No issue-tracker access was necessary; these local documents supplied the requirements.

## Standards

No hard coding-standard violations or actionable smell-only findings were established.

The Standards pass independently identified the duplicate-removal defect described below. It also found the uncalled playback-restoration method noted under separate scope. These are correctness findings rather than style objections.

## Spec

### 1. Private queue exclusion is lost during Play next — P1

Requirement: `r7-persistence.md` says, "IDs observed in private playback remain excluded from later snapshots while present in memory."

`QueuePersistence.onChanged()` immediately prunes `privateIds` against the current list (`QueuePersistence.java:73`). Both `Playlist.next()` and `Playlist.add()` first call `remove(video)`, which emits an intermediate change notification, then reinsert the video and emit another notification. The persistence listener therefore observes a temporary removal during a single user operation.

Reproduction:

1. Start with a normal queue and enter private playback.
2. Add/select a private video; its one-use `incognito` flag is consumed normally.
3. Finish private playback, keeping the queue in memory, and select a different item.
4. Use Play next to requeue the private video.
5. The temporary removal forgets its exclusion; reinsertion sees normal mode and a consumed flag. The next snapshot includes the private video ID.

The isolated harness reproduced exclusion before Play next and inclusion afterwards using unchanged production `Playlist`, `QueuePersistence` and `QueueSnapshot` sources. Follow-up Robolectric regressions in `QueuePersistenceTest` confirm both Play next and append failures on simulated API 24, 28 and 35.

Fix direction: keep compound queue operations from exposing incomplete list states, or reconcile private-ID retention against the completed state before capturing. Add coverage for both Play next and append of an already excluded item after private playback ends. Preserve the bounded exclusion policy and explicit-clear behaviour.

### 2. The queue-card menu bypasses protection of the playing item — P2

Requirement: `queue-validation.md` says, "removal and Clear preserve the playing item."

The new queue-card long-press callback (`MobilePlaybackActivity.java:1058`, `onRelatedLongClicked`) reaches `VideoMenuPresenter.appendRemoveFromPlaybackQueueButton()`. That menu checks only membership, then calls `playlist.remove(mVideo)` (`VideoMenuPresenter.java:998`); it does not exclude the playing item or revalidate that condition when clicked.

With `[A playing, B, C]`, removing A through that menu leaves the player on A while the queue's current index now identifies B. Next returns C and skips B. Clear then preserves B rather than the actual playing item.

The model mutation and resulting Next/Clear behaviour reproduced in the isolated harness. Follow-up `QueueMenuRemovalTest` exercises the actual presenter action: the current item incorrectly offers removal, and an action opened for B still removes B after playback advances to it. Both Robolectric cases fail on API 28 without replacing the production menu/model implementations. Physical-device menu interaction remains unchecked.

Fix direction: apply the same current-item protection to every user-facing removal route, with a live-state check at execution time. Do not globally prohibit the model's internal replacement/removal operations without auditing their callers. Test a current-item change while a menu remains open.

### 3. Removing a restored duplicate removes the wrong occurrence — P2

Requirements: the playing-item preservation rule above, and the documented snapshot checks that prevent stale row actions.

`QueueSheet.Adapter.remove()` removes a precise row from its snapshot, then passes that object to `Playlist.remove(Video)` (`QueueSheet.java:346–348`). The model uses equality-based `indexOf`/`remove` (`Playlist.java:180–184`), which can identify an earlier occurrence. R7 restoration permits distinct objects with the same video ID, and existing duplicate-selection coverage does not exercise removal.

Restore `[X₁, M, X₂]` and remove the last X: the adapter removes X₂ while the model removes X₁. Restore `[X₁, X₂, M]`, select X₁, and remove X₂: the model removes the playing occurrence and silently changes its current object.

Both model outcomes reproduced in the isolated harness. Follow-up `QueueSheetConsistencyTest` cases invoke the real adapter's removal method and confirm both failures on simulated API 28. They do not drive physical touchscreen or RecyclerView gesture input.

Fix direction: use a validated index or object identity for row-specific removal, preserving compatibility where existing callers intentionally use logical equality. Cover duplicate removal before/after the current item, a restored unselected queue, and stale snapshots.

## Separate recovery and submission scope

`MobilePlaybackActivity.restorePlaybackIfMissing(Bundle)` is defined at line 571 but has no caller. `onSaveInstanceState()` writes recoverable playback state, yet `onCreate()` reaches the missing-video fallback without reading it. The fallback's decision ignores saved-state presence. Thus the documented R4 activity-state recovery path is disconnected when a process restart leaves no live presenter selection. This is a static finding in the recovery additions, separate from R7's intentional rule that loading a saved queue must not start playback.

The full baseline diff also contains upload alerts, recovery changes, private test packaging, backup bundles and audit artifacts. It is not a queue-only maintainer submission. Prepare and review a deliberately scoped submission diff after fixing the queue findings; keep the private acceptance package and existing signing key intact. No PR preparation or publication was performed in this review.

## Verification and limits

- Two independent read-only review passes completed.
- An isolated Java harness compiled byte-identical copies of production `Playlist`, `QueuePersistence` and `QueueSnapshot`. Five assertions exposed the three queue defects above; two control assertions passed (private exclusion before requeuing and no extra writes from 10,000 getter rounds).
- The harness used minimal Android dispatch, storage, presenter and Video metadata stubs, a JSON library with a checked-exception compatibility shim, and synchronous writes. It does not establish Android lifecycle, AtomicFile, decoder, concurrency or device behaviour. UI actions were represented by their source-verified model calls.
- Source search confirmed that the recovery method's sole reference is its declaration.
- The initial full Android unit-test attempt stopped because the toolchain/dependencies were missing. With the owner's authorization, a temporary Temurin JDK 21.0.13, Android SDK platform 37.0, build tools 36.0.0 and platform tools were installed. Gradle 9.6.1 and project/test dependencies were downloaded. No project SDK, package or signing settings were changed.
- **Baseline rerun: 221 passed, zero failures/errors/skips** (154 phone/UI, 67 common/player/queue), matching the original R7 audit. This run compiled and tested the original cases before the new regression sources were compiled.
- **Expanded phone/UI rerun: 164 cases, 10 assertion failures, zero errors/skips.** All 154 existing cases still pass. Six new methods produce 10 cases: two privacy methods across API 24/28/35, two menu methods on API 28, and two duplicate-removal methods on API 28. The failures expose the three reviewed defects rather than a toolchain setup error. Gradle stops at the failing phone task; the common tests retain their successful baseline result and were not rerun during the failing expanded invocation.
- Red-stage regression sources: `QueuePersistenceTest.java`, `QueueSheetConsistencyTest.java` and `QueueMenuRemovalTest.java` under the app's existing unit-test tree. Production implementations were unchanged during that failing run; no checkpoint or remote backup of the known-failing state was created. The subsequent fix/green-stage result is recorded above, with additional shared-model coverage in `PlaylistListenerTest.java`.
- The queue additions were checked for periodic callbacks, services, alarms, jobs and wake locks. No queue-specific repeating scheduler was identified. The getter control verifies only that getters do not schedule persistence writes; it is not a battery or wake-up measurement. Existing app networking/background features remain outside that conclusion.

To reproduce using the installed JDK/SDK and the repository wrapper (set `JAVA_HOME`, `ANDROID_HOME` and optionally `GRADLE_USER_HOME` for your local toolchain):

```sh
./gradlew --no-daemon :smarttubetv:testStmobileDebugUnitTest --tests '*Queue*Test' --tests '*RelatedVideoMenuTest' --tests '*CardMenuMigrationTest' --tests '*PressIntentDetectorTest' --tests '*PlaybackRecoveryTest' --tests '*MobilePlaybackLaunchTest' :common:testDebugUnitTest --tests '*Playlist*Test' --tests '*QueuePlaybackModeTest' --tests '*VideoStateController*Test' --tests '*VideoLoaderController*Test'
```

The three queue findings are fixed and their regression tests pass. A maintainer submission still needs a scoped diff and device acceptance of the updated code. The review does not certify that no other bugs exist.

# Stable personal test packaging

Use `tools/local-phone-test.init.gradle` only for personal acceptance builds, separate from feature PRs. It preserves the `.resumetest` package, NewTube Test label, orange icon, disabled updater and the R3 signing certificate. R4 increases the version code to 11704.

Keep the saved `NewTube-Test-PRIVATE-signing-backup-v1.zip` private. It contains `newtube-test.p12` and `signing-credentials.json`; extract them to one private directory outside the repository. Pass that JSON file using `-PphoneTestCredentials=/absolute/private/path/signing-credentials.json`. The script verifies the signer and refuses a different identity. Never create another replacement key merely because the workspace was reset. Never commit/upload signing credentials or the backup.

Back up source checkpoints and validation notes after successful verification. A new workspace must recover the source bundle or personal GitHub branch rather than relying on temporary local commit objects.

Play Protect classification is unresolved. The packaging script is not intended to bypass detection. See `docs/reviews/r4-reconstruction.md`.

Added an explicit progress dispatcher option for multiplatform history backup restore.

  - CLI and other consumers without a UI main dispatcher can use `multiPlatformBackup.restore(progressDispatcher)` to dispatch progress callbacks on a supplied coroutine dispatcher.
  - The existing `multiPlatformBackup.restore` property continues to deliver progress on the main dispatcher, preserving Android and other UI consumers' behavior.
  - Persistence, cancellation checkpoints, failure handling, and restore work-directory cleanup are unchanged.

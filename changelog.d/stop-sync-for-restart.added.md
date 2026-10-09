Added `CoreLogicCommon.stopSyncForRestart()` to stop sync across all accounts before an application restart, and `SyncExecutor.stopForRestart()` to stop an individual executor.

  - ABI: adds public suspend methods, including an abstract method on `SyncExecutor`; existing custom subclasses must implement it before callers can safely invoke it.
  - Source: custom `SyncExecutor` subclasses must implement `stopForRestart()`.
  - Behavior: shutdown cancels sync and waits for in-flight processing and cleanup. Global shutdown also prevents sync from starting in newly created account scopes. Shutdown is permanent for the affected instance.
  - Migration: await `stopSyncForRestart()` before restarting the app. Custom `SyncExecutor` implementations must cancel and await their sync work in `stopForRestart()`.

Added `CellsScope.uploadCoordinator` for direct uploads to Drive folders. Observe per-file state/progress and active work, cancel or retry uploads, and dismiss finished items. The shared queue admits up to three uploads, preserves FIFO order, and runs files above 100 MiB alone. Transfers complete only after publishing their drafts.

  - ABI: additive coordinator, request, item and state types plus a `CellsScope` getter; existing APIs are unchanged.
  - Source: additive. Use the account's existing `CellsScope` to retain the queue across screen changes.
  - Behavior: duplicate completion reports publish once; cancellation during draft creation also stops the transfer.
  - Migration: no change required for existing callers. New callers must enqueue temporary file copies owned by the queue: success and dismissal delete those files, while failure/cancellation retain them for retry. Native hosts continue to own background execution and account/resource lifetimes; this in-memory queue does not survive process termination.

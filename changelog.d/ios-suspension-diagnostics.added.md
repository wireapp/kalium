Added Apple suspension diagnostics for account/log-file locks and user/global database operations.

  - ABI/source: additive `AppleSuspensionDiagnostics` API in `core-logger` for host lifecycle snapshots.
  - Behavior: bounded, sanitized metadata goes directly to OSLog, independently of diagnostic file logging. Locking, database admission and execution ownership are unchanged.
  - Migration: no action is required. Hosts can mark foreground/background verbosity and request snapshots; SQL operation counts do not establish SQLite VFS lock state.

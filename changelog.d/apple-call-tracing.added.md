Added opt-in structured Apple calling traces and per-stream diagnostic coverage checkpoints in log snapshots.

- ABI: additive Apple trace and binding APIs.
- Source: additive; existing logging APIs remain available.
- Behavior: Apple snapshots can include `wire_logs*.coverage.json` alongside log files. Calling policy is unchanged.
- Migration: main Apple hosts configure `AppleCallTrace` with their existing logger config and APP role. NSE configuration does this automatically. Bind native attempts to enable attempt-level correlation; handle snapshot metadata as additional files.

Keep Apple accounts paused during background handoff so ordinary session lookups and database reads cannot reopen resources before the host resumes execution.

- ABI: adds `CoreLogic.resumeSessionScope` and `CoreLogic.resumeSuspendedSessionScopes` on Apple platforms.
- Source: existing `CoreLogic` calls remain available.
- Behavior: `openSessionScope` now waits while an Apple account is paused; database reads pause alongside writes.
- Migration: hosts using `suspendSessionScope` must call `resumeSuspendedSessionScopes` before restarting foreground observers, or `resumeSessionScope` for an individual account with system-authorized background execution time. Do not rely on ordinary lookups to resume paused accounts.

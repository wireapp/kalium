# Apple calling diagnostics

Reviewed 2026-10-06. The Apple trace contract covers engine events and native host lifecycle events.
The host supplies native instrumentation. Tracing does not change calling policy or recover failed calls.

## Configuration and record contract

The Apple host calls `AppleCallTrace.configure` with its existing `KaliumLogger.Config` and process role
`APP` or `NSE`. No additional writer is installed. Configuration and opt-in still determine which sinks
receive the record. The NSE configures this alongside `CoreLogger`; a main host must do the same.

Structured records start with `CALL_TRACE v=1`. Fields are space-separated, with no quoted/free-form
values. The allowlist is defined by `CallTraceEvent`, `CallTraceResult` and `CallTraceContext` in
[AppleCallTrace.kt](../../core/logger/src/appleMain/kotlin/com/wire/kalium/logger/AppleCallTrace.kt).

| Field | Meaning |
| --- | --- |
| `boot`, `role`, `seq` | Random process UUID, host role and monotonically increasing observation sequence. Sort within one boot by sequence; concurrent writer arrival may differ. |
| `event`, `result` | Closed event/result vocabulary. Missing result denotes an observation rather than a command outcome. |
| `attempt` | Native participation UUID, when explicitly bound. Not a conversation ID. |
| `signal` | Durable journal record/presentation UUID, when known. Incoming presentations reuse a selected setup signal UUID. |
| `engine` | Full-engine generation. Useful even before native binding exists. |
| `operation` | One command, callback, UI admission request or CallKit action UUID. Connects phases of that operation; a CallKit action and the resulting Kalium command have different operation IDs. Join by attempt and event ordering, not by equating those IDs. |
| `origin` | Fixed `APP`, `SYSTEM`, `INITIAL_SYNC`, `REPLACEMENT` or `RESET`. An untracked provider action is system-origin, not proof of a human gesture. |
| `code` | Numeric return/close code, signaling response status or documented shared state code. Interpret according to event. |
| `flag` | For incoming events: effective ring eligibility in NSE, or native `shouldRing` in full-engine callbacks. |

Invalid UUID fields are omitted. Do not pass names, raw account/conversation IDs, tokens, signaling
payloads, SDP, addresses or exception text. Existing raw logs have their existing privacy behavior;
this allowlist does not certify the entire archive.

Native owners bind the qualified account/conversation to their attempt before shared commands and
release the returned token after cleanup. Raw lookup keys remain process memory. Releasing an old token
cannot remove a replacement binding. The binding API is Apple-only and does not own call behavior.
Asynchronous shared owners capture `context` at construction. They must not look up the current binding
after suspension, because it may then describe a replacement attempt.

Callbacks capture the current binding before queued state effects run. AVS supplies conversation
identity, not a participation generation: this is current-binding attribution, not proof that the
callback originated from the newest attempt. In particular, an old incoming presentation UUID returned
during Join is a `signal` link, not an unconditional replacement for the current `attempt`.

## Interpreting events

`START`/`ANSWER`/`END`/`REJECT` record command entry and completion, failure or cancellation.
`AVS_START`/`AVS_ANSWER` include native return codes. Successful command completion means submission
completed; it does not mean audio was established or that the peer processed End.

`NATIVE_ENQUEUE` links durable signals to the engine. A stale signal is acknowledged without submission.
`AVS_ANSWERED` and `AVS_ESTABLISHED` record callback receipt, before shared state effects.
`AVS_INCOMING` records receipt and, after processing, its journal link. `JOURNAL_CLOSED` records the
incoming journal's terminal link, which may be absent for outgoing attempts. A callback-effect failure
is separate from the fact that AVS delivered the callback.

`SIGNAL_SEND` records asynchronous transport submission and its result. Backend acceptance is not
remote device receipt. A request without an outcome is incomplete evidence, not automatic success.
Runtime setup/run and engine create/ready/release events distinguish process setup from individual calls.
Raw AVS messages remain process-global supporting evidence; do not assign them to a call by timestamp alone.

## Export coverage

The Apple writer keeps counts of records offered to it, successfully written, disabled, rejected by
queue/size limits, failed in storage, discarded after expiration, and pending. Logger-level filtering
happens before the writer and is **not** counted. Counts cover the current writer lifetime, not all
historical rotated files.

Flush attempts an atomic `wire_logs*.coverage.json` checkpoint. Snapshot writes the exporting writer's
checkpoint and copies available app/NSE checkpoints with log files. A checkpoint contains the process
UUID and capture time; it is not a completeness certificate. An old/missing NSE checkpoint means its
later coverage is unknown. Export never waits for the other process to flush. Storage contention can
lose both records and a checkpoint; a killed process may never publish its last counters.

Checkpoint writes use the storage path directly, never the logger. They cannot recursively log a
storage failure. Existing nonblocking cross-process locking and bounded extension flush remain in place.
Disabling logging does not recover earlier events. Deleting logs removes checkpoints too.

The foundation tests exercise schema filtering, concurrent sequence assignment, stale binding release,
command failure correlation, callback exception safety, export immutability, queue rejection, storage
contention and discarded writes. These tests do not prove device media, OS call UI, background lifetime
or whole-call reconstruction. A blind physical-device test is still needed to validate end-to-end evidence.

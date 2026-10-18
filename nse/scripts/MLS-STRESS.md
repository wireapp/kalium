# MLS receiving stress test

`stress-mls-receiving.py` runs independent native processes on a booted disposable
Apple Silicon iOS simulator. It creates isolated accounts/stores and synthetic
sender keys; it does not use or clear the installed app's session.

From the Kalium root:

```sh
python3 nse/scripts/stress-mls-receiving.py --simulator SIMULATOR_UUID
```

Both fixture binaries are built first. `--skip-build` is for binaries already
rebuilt from the current sources. Defaults: three repetitions of eight cases,
200 messages per case, a real MLS epoch update before every ten messages.
`--messages`, `--rounds`, and `--report` control load and output. `--case` runs only
the cases with one ownership/checkpoint order: `before-commit`, `after-commit`,
`app-first`, `nse-first` or `app-resume`. Assertions remain
enabled. Per-process logs and a machine-readable report are retained; a partial
run reports fewer completed than expected cases and exits unsuccessfully.

Cases:

- Pause the NSE receiver before crypto commit; five independent attempts must be BUSY.
- Pause after crypto commit; five independent attempts must be BUSY.
- Fail before crypto commit, then retry from another process.
- Fail after crypto commit, then retry from another process.
- Fail the SQL processed-marker write after crypto commit, then retry.
- Hold a real main-app CoreLogic session, reject five NSE attempts, consume the
  batch using the app EventProcessor, release the session and replay in NSE.
- Receive in NSE first, then open the real app session and read all messages through
  public use cases before any sync/replay, again rejecting five concurrent NSE attempts.
- Open and release an app session, let NSE receive in another process, then reopen
  the session on the same CoreLogic instance. Read every message before any sync/replay.
  This case uses the production scope lifecycle without suspending an actual UI app.

Barriers signal actual transaction/session ownership; no race depends on hoping
that a sleep overlaps processing. Every case subsequently replays twice in fresh
receiver processes and checks the exact message ID/body set, absence of duplicate
rows and the expected deferred delivery confirmations (empty for app-first), processed marker, final MLS epoch and successful
decryption of a newly encrypted follow-up message. Closing verifies that native
file handles and account ownership are released.

The app uses its existing debug event-ingestion entry point, which invokes the
real EventProcessor and crypto transaction provider. The NSE process runs
NotificationProcessor's test-only target path: it pages the batch from a fixture
NotificationApi over REST and stops at the target, without isolating conversations.
The NSE itself catches up over the WebSocket and then scans the stored queue to its
end with `processToLive`. This deliberately bypasses backend push suppression when
WebSocket is connected. It does **not**
validate an actual WebSocket connection, APNs, extension launch/expiry, signed
App Group entitlements, force-kill recovery, or the WebSocket acknowledgement
protocol. Injected transaction failures unwind normally; they are not process
termination. These require separate host/device tests.

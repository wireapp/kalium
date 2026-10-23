Added `MessageScope.startTrackedAssetUpload` with per-attempt identity, lifecycle progress, scoped cancellation and completion after the asset message is sent. Failed attempts can retry the same persisted message and reuse an already uploaded asset.

  - ABI: additive.
  - Source: additive; existing `sendAssetMessage` callers require no changes.
  - Behavior: existing scheduling remains fire and forget; each upload now owns and removes its visibility monitor. Unfinished cancelled messages become failed and retryable; acknowledged sends stay sent. Tracked attempts use explicit retry rather than automatic pending-message resend and recheck file-sharing policy on retry.
  - Migration: use the tracked handle when the application needs to own and await sending beyond a screen's lifetime.

Added `RetryFailedMessageAndAwaitUseCase` for callers that must retain ownership through retry completion. Cells exposes per-upload cancellation barriers and an opt-in application owner for attachment drafts; cancellation waits for transfer cleanup, including nodes created after cancellation. Existing Android callers keep their scheduling API.

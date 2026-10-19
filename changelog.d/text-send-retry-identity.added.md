Text send failures now include `MessageOperationResult.Failure.messageId` when the message was persisted. Consumers can retry that message without creating a duplicate; a null ID means no message was persisted by that operation.

- Source: additive nullable property with a default value.
- ABI: consumers must recompile after the constructor signature change.
- Behavior: sending and delivery ownership remain unchanged.

Plain-text callers can capture a draft revision through `GetMessageDraftUseCase.plainTextRevision` and pass it to text sending. Kalium consumes only that revision after local message persistence, so leaving a screen cannot restore an already queued message as an unsent draft. Later edits, including identical text entered again, are preserved. Failed persisted messages remain available for retry in the timeline. Existing callers can omit the revision.

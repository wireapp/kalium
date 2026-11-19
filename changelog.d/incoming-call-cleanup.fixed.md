Apple call cleanup now deletes only the journal signal IDs captured when the native close callback arrives. Signals received while queued cleanup or MLS leave is suspended survive, so the next incoming call remains answerable. Continuing group calls retain their signals. AVS still identifies close callbacks only by conversation; this does not resolve an old close callback arriving after a newer start.

`CallSignalStore.closed` now accepts explicit `handledSignalIds` instead of an engine-generation selector and returns `CallSignalCloseResult` with the presentation ID and IDs actually removed. Callers must capture the set before asynchronous cleanup; an empty set retains signals.

Incoming replay rejections now throw `IncomingCallSignalException`, an `IllegalStateException` subclass with a bounded `IncomingCallSignalFailure` reason. Existing exception-category handling remains valid. Apple diagnostics record actual removed opaque signal IDs without payloads or exception messages.

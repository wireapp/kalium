Shared Drive uploads now enter `CellUploadState.Paused` after a connectivity failure and resume when the network returns. Paused uploads count as active. Multipart retries reuse completed parts; regular uploads restart.

Consumers that match `CellUploadState` exhaustively must handle `Paused`. Direct `CellsScope` callers must supply a `NetworkStateObserver`. `CellUploadInfo` adds `isConnectivityIssue` with a default of `false`. These constructor and data-class changes require consumers to recompile.

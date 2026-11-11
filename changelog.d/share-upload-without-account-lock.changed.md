On Apple, `AppleAccountRuntime.useUnheld` gives authenticated networking for a prepared account without holding the account, for uploads that need neither its database nor CoreCrypto. Its `UnheldAccountSession` reads the stored token for each request and refreshes it only while holding the account for that moment; it takes a token another process already refreshed instead. `AppleAccountRuntime.useDatabase` can wait for a busy account with `busyRetry`. `CellFileSharing.create` takes the account's Cells URL instead of its configuration and public link stores, so uploading, cancelling and publishing drafts need no database.

  - ABI: `CellFileSharing.create` changed parameters. `useUnheld`, `UnheldAccountSession` and `useDatabase(busyRetry)` are additive.
  - Source: callers of `CellFileSharing.create` pass the Cells backend URL (`getWireCellsConfig()?.backendUrl`) instead of the two stores.
  - Behavior: unchanged until the share extension uploads with `useUnheld`.

A stale-epoch MLS send no longer waits for sync while holding the crypto transaction that catch-up needs, which could stall both. The sender verifies the epoch, ends the transaction, waits for live, and retries once; deleting the pending message meanwhile still cancels it. The app also waits for incremental sync to reach live before checking sharing policy or importing files handed off by the iOS share extension.

  - Behavior: other sends and broadcasts start without waiting for live, as before. A second stale rejection returns the rejection instead of retrying again.
  - Source and ABI: unchanged.

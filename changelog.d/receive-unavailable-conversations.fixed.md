Sync no longer retries forever when a queued MLS welcome, protocol update, or MLS rejoin targets a conversation that returns `403 access-denied`; these events are skipped like `404 no-conversation`.

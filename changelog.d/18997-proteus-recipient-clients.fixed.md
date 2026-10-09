Prevent creation of a new Proteus one-to-one conversation when a fresh recipient client lookup returns no registered devices.

- Compatibility: additive API and ABI; existing method signatures are unchanged.
- API: adds `NoClientsForUser`, a `CoreFailure.FeatureFailure` carrying the recipient user ID.
- Behavior: existing Proteus conversations remain accessible without a client lookup; lookup failures propagate without creating a conversation.
- Migration: callers can handle `NoClientsForUser` to prompt the recipient to open Wire or log in before trying again.

MLS groups this device created during an interrupted team migration no longer stay hidden waiting for a Welcome that never comes. A database migration queues them for the existing pending MLS group join, which now runs one CoreCrypto transaction per conversation, marks a group established when CoreCrypto already has it, and drops conversations the backend reports as access denied.

  - Migration: none for consumers. The queue is drained by the existing foreground actions.

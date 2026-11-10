Resolving an MLS 1:1 no longer picks an unnamed team group with one other member after that group is migrated to MLS; it resolves to the backend's MLS 1:1. A database migration queues the contacts whose 1:1 was already switched to such a group, so the existing 1:1 resolution recovery switches them back once sync is live.

  - Migration: none for consumers. The queue is drained by the existing foreground actions.

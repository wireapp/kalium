Added text status to user profiles, live user events and message sender data. Loaded message pages refresh when a sender's status changes.

`MessageListUser` gained a nullable `textStatus` constructor parameter. Recompile clients when upgrading. Existing databases migrate automatically.

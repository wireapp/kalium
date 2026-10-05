# User database upgrade fixtures

These SQLite files were generated with the compiled production schema from the
exact preceding Super Kalium commit
`24f4a09e6b38af31b9bcb0d537f1f76b61958182`.

Fixture names and stored versions describe that historical parent. They remain
unchanged when current local migration files are renumbered; tests upgrade copies
to the current schema.

`fork149.db` uses that parent's `UserDatabase.Schema.create` through the
SQLDelight JDBC automatic-open path. `fork143.db` through `fork148.db` start from
that parent's checked-in schema 2 and run its generated migrations from 2 to the
named version in a transaction. Each file has its real `PRAGMA user_version`.
`fork143.db` also represents upstream version 143, before either fork local
operations or the upstream TZID migration. The files contain the old `Meeting.trial` column and no `tzid`; their local
indexes, timeline view, delivery queue and former notification receipt table
match the completed migrations at that version.

`upstream144.db` starts from `fork143.db`, applies only upstream migration 143
and sets version 144. Both meetings then receive `America/New_York` to test that
an already converted schema retains a supplied zone despite sharing its version
number with an old fork schema.

All files contain one one-time meeting, a daily series crossing Berlin's October
2026 DST change, one-time and legacy recurring occurrences, a conversation and
creator, a canonical message and read receipt, and pending/processed events.
Versions 146 and later also contain a queued delivery confirmation. Versions 147
and 148 contain a former notification receipt. Foreign key checks are empty.
The schema and user version come from the parent; only these test rows are added.

JVM and Apple tests copy these files before opening the production user database
builder. They also exercise explicit migration on the real old database before
production reopening. Every upgrade checks authoritative fields, one-time IDs,
message/receipt/event/queue state, local schema, recurring cache regeneration,
foreign keys and a second open. The former notification receipt table is removed
by its existing local migration.

JVM: creating and restoring backups works. Creating a backup used to delete the user's own database: every JVM user database was called `main.db`, and the backup database resolved to the same file. User databases now get per-user file names.

  - ABI: no change
  - Source: no change
  - Behavior: `nuke` also deletes the `-journal`, `-wal` and `-shm` files next to a database and returns `true` when there is no database to delete. The obfuscated database copy stays unavailable on JVM.
  - Migration: existing JVM databases called `main.db` are not picked up; a new database is created.

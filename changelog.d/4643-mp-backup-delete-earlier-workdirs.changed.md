`CreateMPBackupUseCase` now deletes earlier `-create-workdir` folders from the user cache before it creates a backup. A folder that cannot be deleted is logged and does not fail the backup.

  - ABI: none.
  - Source: none.
  - Behavior: earlier backup work folders no longer stay in the cache.
  - Migration: no action required.

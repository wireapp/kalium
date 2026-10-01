Fixed the backup export leaving its plain database copy open: its connections stayed open until the process ended, and on Windows the copy couldn't be deleted after the export, so the next export failed.

  - ABI: no change
  - Source: no change
  - Behavior: `DatabaseExporter.exportToPlainDB` closes the plain database before it returns; `deleteBackupDBFile` then removes it on every platform.
  - Migration: none.

Fixed the backup export and import on Apple.

  - ABI: none
  - Source: none
  - Behavior: the export no longer deletes the user's storage directory, the user database included, when it clears its previous backup file, and it attaches the user database by its file path. The import opens the backup file by its path, which SQLiter rejected before. `UserDatabaseBuilder.nuke()` now deletes only the database on Apple, as on the other platforms; before, it deleted the whole storage directory, with the user's asset files in it.

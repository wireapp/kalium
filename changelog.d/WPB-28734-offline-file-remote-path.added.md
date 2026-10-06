Added nullable `OfflineFileInfo.remotePath` and `CellFileEntity.remotePath` for retaining an offline file's remote folder path. Offline saves store it in the existing `CellFile.assetPath` column; saves without a path preserve a previously stored path.

  - Source: existing Kotlin callers can omit the new defaulted parameter. Handle a null path for records saved without one. Swift consumers of these data classes must supply the new nullable `remotePath` initializer/copy argument. Direct callers of generated `CellFilesQueries.upsertCellFile` supply nullable `assetPath`.
  - ABI: the data-class constructor and copy signatures change. Recompile clients using these classes, including JVM/Android and iOS frameworks.
  - Behavior: offline reads return the saved remote path; no database schema migration is needed.
  - Migration: pass the remote path when saving offline metadata to retain the file's folder location; use `nil` from Swift when unknown.

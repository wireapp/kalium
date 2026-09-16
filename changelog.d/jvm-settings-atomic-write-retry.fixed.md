JVM: replacing a settings file is retried for a short moment when it fails because another process, such as a virus scanner or the Windows search indexer, still holds the file open.

  - ABI: no change
  - Source: no change
  - Behavior: up to five attempts with a growing pause, about half a second in total; the last failure is rethrown.
  - Migration: none needed.

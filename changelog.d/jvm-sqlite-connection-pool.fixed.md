JVM: fixed that file-backed databases opened a new SQLite connection for every statement outside a transaction, paying for opening the file each time and losing per-connection state such as an attached database. They now keep their connections open, one writer and up to eight readers, and reads no longer wait for a running write.

  - ABI: no change
  - Source: no change
  - Behavior: SQLite's busy timeout is set to five seconds, so a second process writing to the same file waits that long for the lock.
  - Migration: none needed.

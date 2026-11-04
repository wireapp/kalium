When a logout wipes data, Kalium now also deletes the user's asset folder and cache folder, after it deletes the database. A failed delete is logged and does not stop the logout. `SELF_SOFT_LOGOUT` keeps all files.

  - ABI: none.
  - Source: none.
  - Behavior: wipe logouts remove the user's files from disk.
  - Migration: no action required.

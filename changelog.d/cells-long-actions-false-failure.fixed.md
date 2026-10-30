Moving, renaming, restoring and deleting Cells nodes by path no longer report a failure when the server takes more than 5 seconds, e.g. when moving many nodes at once, although the action succeeded. The actions now run as background jobs that are polled until they finish (up to 2 minutes), instead of asking the server to await them: past its await timeout the server answered 408, the HTTP client silently resent the request, and the resent request failed with a 404 because the nodes had already moved.

  - Source: unchanged
  - ABI: unchanged
  - Behavior: these actions now only complete once their jobs finished; a job still running after 2 minutes is reported as a failure, although it may still finish.

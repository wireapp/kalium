Multiplatform backup restore now stops between persistence pages when cancelled. A page already being written finishes before cancellation propagates. Restore reports persistence failures, cleans importer resources on every exit, and safely retries reaction pages without duplicate errors or partial reaction writes.

  - ABI and source: restore API unchanged. Persistence adds `ReactionDAO.insertOrIgnoreReactions`; custom DAO implementations must implement this method before using the new restore path.
  - Behavior: failed persistence returns a restore failure instead of success. Progress callbacks run on the main dispatcher and reach 100% after successful persistence.
  - Migration: restore callers keep the same API and cancel the coroutine running restore to stop it.

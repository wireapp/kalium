Fixed link previews without a permanent URL: `MessageDetailsView` returned the literal text `' || url || '` as `permanentUrl` instead of falling back to the preview's `url` when `permanent_url` was NULL.

  - ABI: no change
  - Source: no change
  - Behavior: user database migration 145 recreates `MessageDetailsView`; messages read after the update carry the preview URL as `permanentUrl` when `permanent_url` is NULL.
  - Migration: runs automatically with the database schema update.

Super Kalium keeps upstream migration 145 and moves its local migrations from 145 through 157 to 146 through 158. The later poll view retains its columns and the corrected fallback. Historical fork fixtures keep their original versions; the legacy upgrade mapping skips the local operations they already completed.

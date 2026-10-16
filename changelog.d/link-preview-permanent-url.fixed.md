Fixed link previews without a permanent URL: `MessageDetailsView` returned the literal text `' || url || '` as `permanentUrl` instead of falling back to the preview's `url` when `permanent_url` was NULL.

  - ABI: no change
  - Source: no change
  - Behavior: user database migration 145 recreates `MessageDetailsView`; messages read after the update carry the preview URL as `permanentUrl` when `permanent_url` is NULL.
  - Migration: runs automatically with the database schema update.

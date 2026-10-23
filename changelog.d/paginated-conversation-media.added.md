Added `MessageScope.observePaginatedMediaMessages` to page through conversation images and videos, including media whose original file has not been downloaded.

  - ABI: additive
  - Source: additive
  - Behavior: the new observer excludes Cells, hidden messages and expiring messages and orders media by date and message ID. Existing image and file observers are unchanged.
  - Migration: consumers can call the new observer for a mixed media viewer; no database migration is required.

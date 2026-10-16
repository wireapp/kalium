Fixed upgrades from user database versions 140 through 143 on strict SQLite builds by removing the legacy message view before the meeting table rename and recreating it with the corrected link preview URL fallback.

  - ABI: no change
  - Source: no change
  - Behavior: existing messages and link preview rows are preserved during the upgrade.
  - Migration: migration 143 repairs the view early; migration 145 still repairs databases already past migration 143. No schema version increase beyond the parent change.

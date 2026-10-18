Meeting recurrence now uses the meeting's IANA time-zone ID to preserve the creator's local start time across daylight-saving changes. Meeting creation and updates send `tzid`, and local storage retains it. Legacy payloads and migrated rows without a zone use Europe/Berlin. Recurring meetings with unsupported IDs wait until the platform supports their zone; one-time meetings keep their original time.

Paging, direct next-occurrence lookup and sync rebuild the requested recurrence range from its original series anchor and retain existing occurrence IDs. This fills gaps left by other reads, including overnight ongoing meetings and mixed recurring/one-time lists.

Meetings require API v17. Enable development APIs to negotiate v17; API v16 no longer provides meeting endpoints. The v17 meeting response replaces `trial` with `tzid`.

  - ABI: meeting model constructors and copy signatures change. Occurrence generation `Count` adds optional `from` and `includeOngoing` arguments. Recompile Kotlin/JVM consumers and rebuild Apple frameworks.
  - Source: supply `tzid` to `Meeting`, `UpsertMeeting`, and `UpsertMeetingRequest`; replace DTO/entity `trial` access with `tzid`. Existing `Meeting` and `UpsertMeeting` component positions remain unchanged.
  - Migration: pass the device's current IANA zone when creating a meeting. Preserve the stored meeting zone when editing unless the user changes the schedule's zone.
  - Database: user migration 143 is unchanged. Migration 152 clears recurring occurrence caches once so they regenerate using the stored zone, while preserving one-time occurrences. Offline paging and direct next-occurrence lookup regenerate locally, including series with a missing interval. Global migrations are unchanged.

Pre-release user databases at versions 144 through 149 also receive the
TZID conversion. Migration distinguishes their old trial column from other
schemas and skips operations those databases already completed under their earlier numbering. App
opening and backup migration preserve meeting fields, one-time occurrence IDs,
messages, read receipts, events and delivery confirmations. Explicit backup
migration commits the schema version with its transaction. NSE opening still
requires an app-prepared database.

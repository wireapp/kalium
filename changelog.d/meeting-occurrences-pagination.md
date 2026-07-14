### Added
- Added `UserSessionScope.meetings` with `GetPaginatedMeetingOccurrencesUseCase` to page meeting occurrences and `ObserveMeetingOccurrenceUseCase` to observe a single meeting occurrence by ID.
- Added local meeting occurrence storage and generation for recurring meetings, including periodic cache maintenance and paged occurrence loading.
- Added API v16 meeting sync through `/meetings/list`; meetings are synced only when the negotiated backend API version supports v16 or newer.

### Migration
No action required unless consumers want to surface meetings. Meeting data is currently gated by API v16 support; a backend-provided meetings feature flag is expected later and is not used yet.

### Compatibility
ABI: additive.
Source: additive.
Behavior: meetings sync and occurrence paging run only on backends that support API v16; older backends remain no-op/unsupported for meetings.

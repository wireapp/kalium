### Changed
- Added development support for meetings API v19. Meeting type, link, and `hasPassword` protection status (defaulting to `false`) are available on received meetings; creation accepts an optional password.
- Replaced the public `UpsertMeeting` input with `CreateMeeting` and `UpdateMeeting`. `CreateMeeting.password` is optional and defaults to `null`.
- Meeting guest-link sharing availability now requires API v19 and an enabled meetings feature flag. The meeting availability use cases expose this separately from general meetings support.

### Migration
Use `CreateMeeting` with `CreateNewMeetingUseCase` and `UpdateMeeting` with `UpdateMeetingUseCase`. Set `Meeting.Type.IMMEDIATE` for Meet Now meetings; scheduled is the default.

### Compatibility
ABI: breaking for callers using the previous meeting input type or use case signatures; the guest-link availability methods are additive.
Source: migration required for those callers.
Behavior: existing meetings default to scheduled; no password is sent unless supplied.

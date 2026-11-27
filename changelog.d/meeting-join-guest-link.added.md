Added `MeetingScope.joinMeetingViaCode` to join meeting guest links through API 19, including optional passwords and specific results for invalid passwords, expired links, and unavailable guest links. Successful joins persist the meeting and its conversation for the meeting list.

Added `IsMeetingsEnabledUseCase.isGuestLinkSharingSupported()` to check backend guest-link support independently of the user's meetings feature flag. Documented how this differs from meeting creation and guest-link sharing eligibility.

  - ABI: additive public meeting join API and capability check.
  - Source: implementations of `IsMeetingsEnabledUseCase` must implement `isGuestLinkSharingSupported()`.
  - Behavior: users without the meetings feature flag can join guest links when the backend supports API 19. Both the standard invalid-password response and the backend HTTP 400 password-query parsing error are treated as invalid meeting passwords.
  - Migration: use `isGuestLinkSharingSupported()` when joining guest links; keep `isGuestLinkSharingEnabled()` for sharing links.

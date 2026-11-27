Added email invitees to meetings. `Meeting`, `CreateMeeting`, and `UpdateMeeting` now expose `invitedEmails`, defaulting to an empty list. Meeting request and response DTOs carry these addresses as `invited_emails`.

API v19 adds `MeetingApi.addInvitations` and `MeetingApi.deleteInvitations`, using `MeetingInvitationsRequest.emails`. Updating a meeting sends added and removed addresses through these endpoints; removals use the invitation cancellation endpoint. Earlier API versions return an unsupported-API error for both endpoints.

  - ABI: breaking changes to public meeting model, DTO, and persistence entity constructors and generated `copy` methods, as well as persistence meeting mapper signatures. `MeetingApi` gains two abstract methods. Existing component positions are preserved.
  - Source: existing Kotlin model constructor calls remain compatible through default empty lists. Custom `MeetingApi` implementations must implement the two new invitation methods; direct persistence meeting mapper callers must supply the new email lists.
  - Behavior: supplied email addresses are invited independently of conversation membership, including addresses belonging to team members. Meetings without email invitees retain their existing behavior.
  - Migration: recompile Kotlin/JVM consumers and rebuild Apple frameworks. Supply `invitedEmails` when creating or updating meetings to manage email invitations.
  - Database: user migration 168 adds persisted email invitees and initializes existing meetings with an empty list.

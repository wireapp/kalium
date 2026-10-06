Regular adminless deletion reminders now decode when the team is missing or null, allowing the existing app and iOS Notification Service Extension handlers to persist the scheduled deletion time.

  - Source: `AdminlessDeleteReminderDTO.teamId` is nullable and defaults to `null`; direct DTO consumers must handle a nullable team when recompiling. The JVM constructor signature is unchanged.
  - Behavior: missing, explicit-null, and populated teams are accepted; populated values and required sender fields retain their existing behavior.

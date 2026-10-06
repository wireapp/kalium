System adminless deletion reminders and membership updates now decode when sender data is absent. System reminders also accept an absent team, allowing existing app and iOS Notification Service Extension handling to process these events.

  - Source: `SystemAdminlessDeleteReminderDTO.from`, `SystemAdminlessDeleteReminderDTO.teamId`, and `SystemMemberUpdateDTO.from` are nullable and default to `null`; direct DTO consumers must handle nullable values when recompiling.
  - Behavior: missing, explicit-null, and populated values are accepted for these fields. Regular conversation event requirements are unchanged.

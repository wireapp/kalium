Added `UserSessionScope.observeUnreadConversationBadgeCount()` to observe an account's unread conversation count for app icon badges. Each eligible conversation counts once, including archived conversations. Fully muted conversations, blocked contacts, deleted conversations, connection requests, self conversations, and meetings are excluded; mentions-and-replies-only conversations follow the existing unread badge rules.

`KaliumNSEResult.unreadConversationBadgeCount` contains the absolute count after successful receiving. A count-read failure or timeout preserves already-produced message and call results.

  - ABI: added the session observation method and appended an optional NSE result field.
  - Behavior: hosts may update an app icon badge without counting delivered notifications or loading conversation pages.
  - Migration: rebuild the NSE framework and apply the count only for the account currently selected by the app.

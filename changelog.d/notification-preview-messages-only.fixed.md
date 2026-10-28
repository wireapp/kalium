Hidden message content no longer turns connection requests and conversation deletions into generic message alerts. When the account's message-content preference is disabled or unreadable, or the host hides content, `notificationPreview` and `KaliumNSEResult` still hide message text, kind, sender and conversation names, but connection requests and conversation deletions keep their content type and names, as on Android. Their `presentationReason` is no longer `MESSAGE_CONTENT_HIDDEN`.

  - ABI: unchanged.
  - Source: unchanged.
  - Migration: hosts that present `CONNECTION_REQUEST` and `CONVERSATION_DELETED` results need no change.

`KaliumNSEResult.alerts` returns other unread messages that were stored without an alert, as `KaliumNSEAlert` values.

  - ABI: additive
  - Source: additive
  - Behavior: a request without its own alert, such as one whose event an earlier attempt processed, may now show the newest unnotified message. Unread messages the app received without marking them notified, up to six hours old, alert on the next push.
  - Migration: post `alerts` as local notifications after reporting calls and before completing the request. While running, mark conversations notified as the Android app does, including the open conversation.

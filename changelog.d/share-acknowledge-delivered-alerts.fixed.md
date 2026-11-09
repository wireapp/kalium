On Apple, the share extension posts the message alerts it took through the host, as the notification extension does, and acknowledges each one the host accepted before it releases the account. The app no longer shows a second alert for a message the share extension already presented. An alert the host did not accept stays pending for the account's next owner.

  - ABI: breaking. `KaliumShareSender` takes a `KaliumShareMessageDelivery`, and `KaliumShareResult.alerts` is removed. `KaliumShareAlert.messageKind` is new and labels pictures, files and other typed messages as `KaliumNSEAlert.messageKind` does.
  - Behavior: `send` posts its message alerts itself before it returns; `presentGenericAlert` is unchanged.
  - Migration: pass a `KaliumShareMessageDelivery` that posts each alert under the identity the app and the notification extension use, and stop posting result alerts.

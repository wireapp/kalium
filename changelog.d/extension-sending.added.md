Added `KaliumShare`, an XCFramework for the iOS share extension. `KaliumShareSender` lists the
conversations an extension may send to and sends text through the app's sender while it keeps the
account live over the WebSocket, then returns the alerts owed by events received meanwhile (ADR 12).

  - ABI: additive
  - Behavior: unchanged for the app. Its sending path now runs on the same sender in `:domain:messaging:sending`.
  - Migration: none.

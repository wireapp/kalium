Removed `KaliumNSEReceiver.process`, which returned message alerts without delivering or acknowledging them.

  - ABI: breaking for the KaliumNSE Apple framework. The removed method is gone.
  - Source: breaking for hosts that call `process`.
  - Behavior: no change for `processAndDeliver`.
  - Migration: call `processAndDeliver` with a `KaliumNSEMessageDelivery` that enqueues each alert and reports whether the system accepted it.

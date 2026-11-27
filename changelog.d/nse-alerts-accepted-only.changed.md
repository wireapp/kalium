`KaliumNSEResult.alerts` from `processAndDeliver` lists only the alerts the host accepted, so the host shows the push's own content when posting its alert failed.

  - ABI and source: unchanged.
  - Behavior: rejected alerts are no longer listed; they stay pending for the account's next owner.

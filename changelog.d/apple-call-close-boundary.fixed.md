Apple call cleanup no longer deletes a newer incoming call's signal when the previous call's cleanup is delayed, so that call can still be answered.

  - ABI: changed (`CallSignalStore.submitted` and `CallSignalStore.closed` take an extra defaulted parameter)
  - Source: compatible for callers; implementations add the parameter
  - Behavior: only signals submitted before the close callback are dropped

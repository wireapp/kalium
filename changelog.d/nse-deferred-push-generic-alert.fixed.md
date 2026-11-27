The iOS notification extension shows the generic alert when it defers the push's own event or stops at an ordering barrier before it, instead of showing nothing.

  - ABI and source: unchanged.
  - Behavior: `KaliumNSEResult.presentGenericAlert` is also set for these results.

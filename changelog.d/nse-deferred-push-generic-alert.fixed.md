The iOS notification extension shows the generic alert when it defers the push's own event or stops at an ordering barrier before it, instead of showing nothing. A deferred event in a conversation that is muted or allows only mentions and replies stays silent.

  - ABI and source: unchanged.
  - Behavior: `KaliumNSEResult.presentGenericAlert` is also set for these results; a deferred target in such a conversation is reported as `DEFERRED_MUTED_CONVERSATION`.

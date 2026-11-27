A call signal is no longer lost when an Apple receiving attempt stops between its event's crypto commit and the signal's ready mark: the next receiving run that reaches the end of the queue without deferrals makes it ready, so the call can still ring or be reported as missed.

  - ABI: additive (`CallSignalJournal.committedAll`)
  - Source: compatible

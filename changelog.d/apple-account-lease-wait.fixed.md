On Apple, the app waits at most 15 seconds for an account that an app extension holds, also when it reopens the account after a background release; that wait stays cancellable. Opening then throws `AccountBusyException` with what the holding process recorded, and the account stays usable for a later attempt. Extensions can check `KaliumNSEReceiver.hasQuarantinedAccount` and `KaliumShareSender.hasQuarantinedAccount` to end their process after a failed account close.

  - ABI: additive (`AccountBusyException`).
  - Source: additive.
  - Migration: Apple callers of `CoreLogic.openSessionScope` should treat `AccountBusyException` as retryable; other platforms never throw it.

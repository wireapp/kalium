Removed the Apple diagnostic file writer `AppleDiagnosticLogWriter`, the extension logging facades `KaliumNSELogging` and `KaliumShareLogging`, and `KaliumNSERequest.appPresentsAlerts`.

  - ABI: breaking for Apple frameworks. The `KaliumNSERequest` initializer loses its last argument; the removed types are gone.
  - Source: breaking for hosts that pass `appPresentsAlerts` or use the removed types.
  - Behavior: a busy, unprepared or incompatible account, and legal-hold work left for the app, always set `presentGenericAlert`, as they did with `appPresentsAlerts = false`. Kalium no longer writes log files or takes a cross-process log lock on Apple.
  - Migration: drop the `appPresentsAlerts` argument. Hosts that want file logs install their own writer, call `CoreLogger.init` and, in the NSE, `AppleCallTrace.configure` with role `NSE`.

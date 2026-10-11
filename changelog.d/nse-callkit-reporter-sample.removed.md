Removed the legacy `nse/host/KaliumNSECallKitReporter.swift` sample. Hosts read
`KaliumNSEResult.calls` and report calls through their own CallKit integration.

  - Source: breaking for hosts that compiled the sample file.
  - Migration: report `KaliumNSEResult.calls` from the host's CallKit integration.

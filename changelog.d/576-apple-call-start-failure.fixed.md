Apple outgoing call startup now reports failure instead of success when AVS rejects the start, the MLS conference join fails, the native handle is unavailable, or calling is disabled. Failed starts close the call row they created and leave an MLS conference they joined. Startup waits for the native handle and AVS readiness before creating the call row.

  - ABI: additive; `StartCallUseCase.Result.StartFailure` is a new result.
  - Source: breaking for exhaustive `when` over `StartCallUseCase.Result` on every platform; handle `StartFailure`.
  - Behavior: `Success` means the start was submitted to AVS, not that media is established. Only the Apple engine produces `StartFailure`; the Android engine is unchanged.

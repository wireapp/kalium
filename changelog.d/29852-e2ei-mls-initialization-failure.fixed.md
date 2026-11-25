Fixed certificate enrollment crashes caused by MLS initialization failures. Failed clients are not cached, and incoming events remain pending for retry when initialization fails.

  - Behavior: while MLS initialization fails (other than MLS being disabled), incremental sync stops before acknowledging events and retries, instead of skipping MLS events.
  - Migration: no caller changes required.

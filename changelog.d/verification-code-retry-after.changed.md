Verification-code rate-limit failures now expose the backend's optional `retryAfterSeconds` delay, preserving numeric `Retry-After` seconds instead of discarding them.

  - ABI and source: breaking. `TooManyRequests` changes from an object to a data class; `InvalidRequestError` gains an optional constructor property. Recompile consumers.
  - Migration: match `TooManyRequests` by type (`is TooManyRequests`) and read `retryAfterSeconds`. Construct `TooManyRequests()` when no delay is available. Missing or invalid headers remain `null`.

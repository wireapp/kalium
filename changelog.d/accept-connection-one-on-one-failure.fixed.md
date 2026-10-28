Accepting a connection request no longer reports a failure when the backend accepted it but the 1:1 conversation could not be resolved afterwards, for example because the other user had no MLS key packages. `AcceptConnectionRequestUseCase` now returns `Success` in that case, still adds the unverified-conversation warning, and queues the 1:1 resolution for the next foreground recovery when the failure may pass, such as missing key packages. A failed backend update still returns `Failure`.

  - ABI: unchanged.
  - Source: unchanged.
  - Migration: hosts no longer need to treat a failed accept as possibly accepted.

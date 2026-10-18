Retrying a failed message now returns `MessageOperationResult.Failure` when its content cannot be encoded, including a deleted message placeholder. It leaves the message status unchanged and does not schedule a send, preventing a crash. Android and Apple clients use the same guard.

  - ABI: unchanged.
  - Source: unchanged.
  - Behavior: unsupported content is rejected before retry side effects; supported message retries keep their existing behavior.
  - Migration: no API migration is required. Consumers should handle the existing failure result when offering message retry.

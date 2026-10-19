Deleting a meeting for everyone now removes its meeting conversation locally for the creator, allowing the existing conversation observer to end the active call. Meetings attached to other conversation types preserve their conversation.

  - ABI and source: unchanged.
  - Behavior: meeting lookup and remote deletion failures return failure; local conversation cleanup failures after successful meeting deletion retain success.
  - Migration: no consumer changes required; hosts continue running the existing call observer.

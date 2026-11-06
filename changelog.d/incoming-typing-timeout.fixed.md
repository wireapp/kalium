Received typing indicators now expire 60 seconds after the last STARTED event, matching web and iOS. Expiry is evaluated while `ObserveUsersTypingUseCase` is collected, so no timers run for conversations that are not observed.

  - ABI: no change.
  - Source: no change.
  - Migration: no caller changes required.

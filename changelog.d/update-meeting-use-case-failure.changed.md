Changed `UpdateMeetingUseCase.Result.Failure` to expose specific update failure types.

  - ABI: changed
  - Source: changed for consumers matching `Failure` as a singleton object.
  - Behavior: meeting update conversation-name failures now return `UpdateConversationNameFailure`.
  - Migration: handle `Failure.Other` and `Failure.UpdateConversationNameFailure`.

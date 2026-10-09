Creating a meeting now returns its conversation ID so clients can open the call immediately.

  - ABI: changed; `CreateNewMeetingUseCase.Result.Success` now takes a `ConversationId` instead of being a singleton object.
  - Source: consumers matching or returning `Success` must handle its `conversationId` property and construct it with a conversation ID.
  - Behavior: callers can use the returned conversation ID to start the call as soon as meeting creation succeeds.
  - Migration: replace references to the `Success` object with `Success(conversationId)` and update result handling to read `conversationId`.

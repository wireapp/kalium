Added `MessageScope.openMessageTimeline` with concrete keyset page requests and results, plus
`getPaginatedFlowOfMessageListItems` for Paging consumers. `MessageListItem` provides typed rendering
data without full message or asset credentials. Timeline sessions refresh by message anchors and
invalidate when their database dependencies change.

  - ABI: additive.
  - Source: additive.
  - Behavior: existing offset APIs keep their signatures and positional behavior.
  - Migration: opt into the new API explicitly. Disable placeholders, migrate navigation to message
    anchors, observe invalidation, and close sessions when leaving the timeline. Resolve asset and
    preview credentials through existing detail APIs. Client rollout still requires benchmark results.

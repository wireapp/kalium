Added `CellsScope.emptyRecycleBin` to permanently delete everything in the recycle bin of a conversation in a single request. It takes the conversation id and deletes the `<conversationId>/recycle_bin` path for good. No local cleanup is needed, as it already happened when the nodes were moved to the recycle bin.

  - Source: additive
  - ABI: additive
  - Behavior: no rollback; if the request fails, some nodes may already have been deleted.

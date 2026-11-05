Added `CellsScope.emptyRecycleBin` to permanently delete everything in the recycle bin of a conversation in a single request. It takes the conversation id and deletes the `<conversationId>/recycle_bin` path for good. No local cleanup is needed, as it already happened when the nodes were moved to the recycle bin. Deleting by path (`CellsRepository.deleteFiles`) now waits for the server to finish the delete, so a list loaded right after no longer shows the deleted nodes.

  - Source: additive
  - ABI: additive
  - Behavior: no rollback; if the request fails, some nodes may already have been deleted.

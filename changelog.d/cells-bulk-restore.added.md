Added `CellsScope.restoreNodesFromRecycleBin` to restore several Cells nodes from the recycle bin in a single request, making them available again as message attachments like `restoreNodeFromRecycleBin` does for one node.

  - Source: additive
  - ABI: additive
  - Behavior: no rollback; if the request fails, some nodes may still have been restored and their attachment status is only updated on their next refresh.

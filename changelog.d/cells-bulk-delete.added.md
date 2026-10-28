Added `CellsScope.deleteCellAssetsUseCase` to move several Cells nodes to the recycle bin in a single request, cleaning up their local data like `deleteCellAssetUseCase` does for one node. Nodes are passed as `NodeToDelete` (the node UUID plus the local file path to remove) and identified by UUID, so a node renamed or moved meanwhile is still deleted. For folders, set `NodeToDelete.folderPath` to also clean up the local copies of the files stored under them. Pass `permanentDelete = true` to delete them for good instead, e.g. to clean up the recycle bin.

  - Source: additive
  - ABI: additive
  - Behavior: no rollback; if the request fails, some nodes may still have been deleted and their local data is only cleaned up on their next refresh.

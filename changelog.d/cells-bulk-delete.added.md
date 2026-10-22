Added `CellsScope.deleteCellAssetsUseCase` to move several Cells nodes to the recycle bin in a single request, cleaning up their local data like `deleteCellAssetUseCase` does for one node. Nodes are passed as `NodeToDelete` (a `NodeLocator` plus the local file path to remove).

  - Source: additive
  - ABI: additive
  - Behavior: no rollback; if the request fails, some nodes may still have been deleted and their local data is only cleaned up on their next refresh.

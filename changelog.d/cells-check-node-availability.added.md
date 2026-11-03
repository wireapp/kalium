Added `CellsScope.checkCellNodeAvailability` to check that a Cells node listed earlier is still on the server, e.g. before opening it. It returns `CellNodeAvailability.Available` with the node as it is now (a file with fresh content and preview URLs, or a folder), `Gone` when the node was deleted, moved to the recycle bin or the self user lost access to it (its local data is then cleaned up like for a delete), or `Unknown` when it couldn't be checked. Pass `inRecycleBin = true` for nodes listed in the recycle bin, so that being recycled doesn't count as gone.

  - Source: additive
  - ABI: additive
  - Behavior: a 403 counts as gone, like in `refreshAsset`.

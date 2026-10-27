Deleting Cells nodes no longer removes them from the messages they were shared in: such attachments are kept and shown as not found, only their local copy is removed. Deleting a folder now also cleans up the local copies of the files stored under it, at any depth; pass its remote path as `folderPath` to `deleteCellAssetUseCase`. The local cleanup also completes when the caller is cancelled after the server delete succeeded. The local copy recorded for a deleted node is removed even when the caller doesn't pass it, and files downloaded to be opened are now saved with their remote path, so deleting a folder above them cleans them up too.

  - Source: additive (`folderPath` defaults to null)
  - ABI: `DeleteCellAssetUseCase.invoke` gains a parameter; recompile consumers.
  - Behavior: previously a deleted file's attachment disappeared from its messages through the `MessageAttachments` foreign key cascade.

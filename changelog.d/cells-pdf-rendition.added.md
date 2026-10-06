Cells document listings now expose `Node.File.pdfPreviewUrl`, and `CellsScope.getPdfPreviewUrl` retrieves the PDF rendition for a node ID. Image thumbnails select the largest image rendition and continue polling while it is being generated. Saving a thumbnail refreshes the conversation message list on Android and iOS.

  - Source: additive; existing refresh calls and positional model arguments retain their meaning.
  - ABI: `NodePreview` and `Node.File` constructor/copy signatures change; recompile consumers when upgrading. Cells is not yet covered by the logic-only ABI snapshots.
  - Migration: use the PDF URL for read-only document display; `previewUrl` remains an image thumbnail. A missing, pending or failed PDF rendition returns null. When constructing `NodePreview` directly, supply its content type for rendition selection. Existing offline file paths remain intact.

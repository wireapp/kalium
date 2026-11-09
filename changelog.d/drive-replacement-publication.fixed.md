Drive file replacement now confirms the uploaded content when publication assigns a new version ID or omits optional draft/head flags. Confirmation excludes pre-existing versions and requires the replacement to match the current downloadable version; callers receive its published version ID. Publication waits for a non-blank draft content hash. Draft metadata and post-publication confirmation have separate bounded waits, so the draft polling deadline cannot cancel the publication request.

  - ABI and source: unchanged.
  - Migration: no action required.

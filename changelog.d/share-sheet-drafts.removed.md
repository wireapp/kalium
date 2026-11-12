Removed Cells draft uploads from the Apple share sender: `KaliumShareDrafts`, `KaliumShareDraftState`, `KaliumShareSender.prepareDraft` and `discardDraft`, `KaliumShareRequest.preparedDraftIds`, `KaliumShareConversation.isCells`, and `prepareCells`, `discardCells` and `storeCells` of the `@InternalKaliumApi` `ShareOutboxSending`.

  - ABI: breaking for the KaliumShare Apple framework. The `KaliumShareRequest` and `KaliumShareConversation` initializers lose their last argument; the removed types and methods are gone.
  - Source: breaking for hosts that prepare drafts, pass `preparedDraftIds` or read `isCells`.
  - Behavior: files for a Cells conversation upload when `send` runs, like other files, with progress through `KaliumShareProgress`; a share handed to the app uploads them when the app imports it. Draft records and queued draft work of earlier builds are deleted without being read.
  - Migration: stop preparing drafts and call `send` or `handOff` with the attachments.

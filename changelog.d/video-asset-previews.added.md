Support optional, separately encrypted video image previews in asset messages. Upload parameters can include a preview; received previews are persisted and can be resolved without downloading the original video through `MessageScope.getAssetMessagePreview`.

Existing Kotlin/JVM constructor and copy entry points are retained, including the old Swift initializer and copy selectors, as deprecated compatibility overloads.

Preview metadata is stored in typed database columns; existing preview envelopes are converted during migration. Preview images are limited to 200,000 bytes. Oversized declared previews are rejected before downloading, and streamed encrypted bytes and decoded files are capped independently. Image bytes remain in file storage.

Preview decryption rejects incomplete IVs and reports cryptography failures without swallowing cancellation. Managed uploads remain available for retry until the message is persisted. Message deletion and expiry remove previews using their asset key and domain.

Preview cache lookups validate the normalized domain both before downloading and after concurrent cache insertion. Same-key entries from another domain are rejected without removing their file or cache entry.

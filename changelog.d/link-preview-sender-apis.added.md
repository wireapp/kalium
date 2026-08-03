Added sender-side link preview APIs for text messages through `MessageScope.detectLinkPreviewTarget` and `MessageScope.generateLinkPreview`.

  - ABI: additive
  - Source: additive
  - Behavior: sender flows can detect and generate previews, and preview generation still degrades to plain text when preview generation or preview image upload fails.

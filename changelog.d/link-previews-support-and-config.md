### Added
- Added synced link preview user configuration through `WIRE_LINK_PREVIEWS`, exposed via `UserScope.observeLinkPreviewsEnabled` and `UserScope.persistLinkPreviewsStatusConfig`.
- Added sender-side link preview APIs for text messages through `MessageScope.detectLinkPreviewTarget` and `MessageScope.generateLinkPreview`.
- Added mapping and persistence support for `MessageLinkPreview` data already present on text messages, including preview image metadata and local-path updates for preview assets.

### Migration
Consumers can replace app-local link preview settings with `UserScope.observeLinkPreviewsEnabled` and `UserScope.persistLinkPreviewsStatusConfig`. Clients that prefetch sender-side previews while composing text messages can use `MessageScope.detectLinkPreviewTarget` together with `MessageScope.generateLinkPreview`.

### Compatibility
ABI: additive.
Source: additive.
Behavior: link previews are now handled for text messages in Kalium. Sender flows can detect and generate previews, `WIRE_LINK_PREVIEWS` is synced across clients, and text messages that already contain preview data are mapped and persisted. Preview generation still degrades to plain text when preview generation or preview image upload fails.

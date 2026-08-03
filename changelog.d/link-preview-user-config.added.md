Added synced link preview user configuration through `WIRE_LINK_PREVIEWS`, exposed via `UserScope.observeLinkPreviewsEnabled` and `UserScope.persistLinkPreviewsStatusConfig`.

  - ABI: additive
  - Source: additive
  - Behavior: `WIRE_LINK_PREVIEWS` is synced across clients and can replace app-local link preview settings.

Added the SSO identity provider ID to authenticated session data.

  - ABI: breaking for previously compiled consumers constructing or copying `StoreSessionParam`; otherwise additive.
  - Source: additive.
  - Behavior: retained SSO accounts keep identity provider information.

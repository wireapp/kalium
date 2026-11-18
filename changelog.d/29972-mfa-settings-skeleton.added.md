Added account-scoped `MFAScope` APIs for MFA setup, authenticator management,
recovery codes, and disabling MFA. Currently backed by a temporary fake
implementation; backend integration is not included.

  - ABI: additive
  - Source: additive
  - Behavior: existing consumers are unaffected unless they call the new MFA APIs.

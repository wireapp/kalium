`AddAuthenticatedUserUseCase` no longer reports `SsoIdentityChanged` for a retained
account that has no previously recorded SSO identity provider ID. An identity provider
change is now reported only when both the stored and the incoming IdP ID are known and
differ; a login against an account with no recorded IdP succeeds and records the IdP for
later comparisons.

  - ABI: no change
  - Source: no change
  - Behavior: accounts stored before the SSO identity provider ID was persisted no
    longer prompt for a destructive account replacement on their first SSO re-login.
  - Migration: no action required.

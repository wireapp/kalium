Extended SSO-code login with the identity provider and server configuration used to
initiate login, plus authenticated system-settings capability detection before session
completion.

  - ABI: breaking for previously compiled JVM and KMP consumers. `SSOInitiateLoginResult.Success`
    gained `identityProviderId` and `serverConfigId`; `GetSSOLoginSessionUseCase.invoke`
    gained `checkIdpChangeDetection`; and `SSOLoginSessionResult.Success` gained
    `isIdpChangeDetectionEnabled`, changing its constructor and generated data-class methods.
  - Source: direct construction of `SSOInitiateLoginResult.Success` and custom
    `GetSSOLoginSessionUseCase` implementations must be updated. Existing Kotlin calls to
    `invoke(cookie)` and constructions of `SSOLoginSessionResult.Success` remain compatible
    after recompilation through default arguments.
  - Behavior: when `checkIdpChangeDetection` is true, SSO-code login reads authenticated
    system settings and exposes whether the pending IdP should be compared. A known incoming
    IdP with no retained IdP is treated as `SsoIdentityChanged` and requires confirmation;
    the default `false` preserves the legacy SSO-code flow.
  - Migration: recompile consumers, pass the canonical IdP and selected server configuration
    when constructing `SSOInitiateLoginResult.Success`, update custom use-case implementations
    for the boolean parameter, and use `isIdpChangeDetectionEnabled` before comparing the
    pending IdP.

package com.wire.kalium.logic.featureFlags

data class KaliumConfigs(
    val isChangeEmailEnabled: Boolean = false,
    val isMarketingCommunicationEnabled: Boolean = false,
    val isSSoEnabled: Boolean = false,
    val isAccountCreationEnabled: Boolean = false,
    val blockOnJailbreakOrRoot: Boolean = false,
    val blockOnPasswordPolicy: Boolean = false,
    val forceAppLock: Boolean = false,
    val forceConstantBitrateCalls: Boolean = false,
    val forceHideScreenContent: Boolean = false,
    val forcePrivateKeyboard: Boolean = false,
    val keepWebSocketOn: Boolean = false,
    val wipeOnCookieInvalid: Boolean = false,
    val submitCrashReports: Boolean = false,
    val webLinkPreview: Boolean = false,
    val developerFeaturesEnabled: Boolean = false,
    val isLoggingEnabled: Boolean = false,
    val isSafeLoggingEnabled: Boolean = false,
    val enableBlacklist: Boolean = false,
    val fileRestrictionEnabled: Boolean = false,
    val customUrlScheme: String = "",
    val fileRestrictionList: String = "",
    val httpProxyPort: String = "",
    val httpProxyUrl: String = "",
    val websiteUrl: String = "",
    val webSocketUrl: String = "",
    val certificate: String = "",
    val domain: String = "",
    val supportEmail: String = "",
    val teamsUrl: String = "",
    val backendUrl: String = "",
    val blacklistHost: String = "",
    val accountUrl: String = "",
    val maxAccount: Int = 0,
    val newPasswordMaximumLength: Int = 0,
    val newPasswordMinimumLength: Int = 0,
    val passwordMaxAttempts: Int = 0,
    val appLockTimeout: Int = 0
)


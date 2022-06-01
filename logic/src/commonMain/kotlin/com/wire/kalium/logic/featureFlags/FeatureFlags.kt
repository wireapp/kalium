package com.wire.kalium.logic.featureFlags

data class BuildTimeConfigs(
    val isChangeEmailEnabled: Boolean,
    val isMarketingCommunicationEnabled: Boolean,
    val isSSoEnabled: Boolean,
    val isAccountCreationEnabled: Boolean,
    val blockOnJailbreakOrRoot: Boolean,
    val blockOnPasswordPolicy: Boolean,
    val forceAppLock: Boolean,
    val forceConstantBitrateCalls: Boolean,
    val forceHideScreenContent: Boolean,
    val forcePrivateKeyboard: Boolean,
    val keepWebSocketOn: Boolean,
    val wipeOnCookieInvalid: Boolean,
    val submitCrashReports: Boolean,
    val webLinkPreview: Boolean,
    val developerFeaturesEnabled: Boolean,
    val isLoggingEnabled: Boolean,
    val isSafeLoggingEnabled: Boolean,
    val countlyAppKey: String,
    val countlyServerUrl: String,
    val customUrlScheme: String,
    val enableBlacklist: Boolean,
    val fileRestrictionEnabled: Boolean,
    val fileRestrictionList: String,
    val httpProxyPort: String,
    val httpProxyUrl: String,
    val websiteUrl: String,
    val webSocketUrl: String,
    val certificate: String,
    val domain: String,
    val supportEmail: String,
    val teamsUrl: String,
    val backendUrl: String,
    val blacklistHost: String,
    val accountUrl: String,
    val maxAccount: Int,
    val newPasswordMaximumLength: Int,
    val newPasswordMinimumLength: Int,
    val passwordMaxAttempts: Int,
    val appLockTimeout: Int
    )


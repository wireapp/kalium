package com.wire.kalium.logic.featureFlags

data class KaliumConfigs(
    val isChangeEmailEnabled: Boolean = false,
    val forceConstantBitrateCalls: Boolean = false,
    val submitCrashReports: Boolean = false,
    val developerFeaturesEnabled: Boolean = false,
    val isLoggingEnabled: Boolean = false,
    val isSafeLoggingEnabled: Boolean = false,
    val enableBlacklist: Boolean = false,
    val fileRestrictionEnabled: Boolean = false,
    var isMLSSupportEnabled: Boolean = true,
    // Disabling db-encryption will crash on android-api level below 30
    val shouldEncryptData: Boolean = true,
    val encryptProteusStorage: Boolean = false,
    val lowerKeyPackageLimits: Boolean = false,
    val lowerKeyingMaterialsUpdateThreshold: Boolean = false,
    val fileRestrictionList: String = "",
    val certificate: String = "",
    val domain: String = "",
    val blacklistHost: String = "",
    val maxAccount: Int = 0,
    val developmentApiEnabled: Boolean = false
)

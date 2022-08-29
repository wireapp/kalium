package com.wire.kalium.logic.data.keypackage

import com.wire.kalium.logic.featureFlags.KaliumConfigs

interface KeyPackageLimitsProvider {

    fun needsRefill(keyPackageCount: Int): Boolean

    fun refillAmount(): Int

}

class KeyPackageLimitsProviderImpl(
    private val kaliumConfigs: KaliumConfigs
) : KeyPackageLimitsProvider {

    private val keyPackageUploadLimit: Int
        get() = if (kaliumConfigs.lowerKeyPackageLimits) KEY_PACKAGE_LIMIT_LOW else KEY_PACKAGE_LIMIT

    private val keyPackageUploadThreshold: Float
        get() = KEY_PACKAGE_THRESHOLD

    override fun needsRefill(keyPackageCount: Int): Boolean {
        return keyPackageCount < (keyPackageUploadLimit * keyPackageUploadThreshold)
    }

    override fun refillAmount() = keyPackageUploadLimit

    companion object {
        internal const val KEY_PACKAGE_LIMIT = 100
        internal const val KEY_PACKAGE_LIMIT_LOW = 10
        internal const val KEY_PACKAGE_THRESHOLD = 0.5F
    }
}

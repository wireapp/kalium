package com.wire.kalium.logic.featureFlags

interface FeatureSupport {
    val isMLSSupported: Boolean
}

@Suppress("MagicNumber")
class FeatureSupportImpl(
    kaliumConfigs: KaliumConfigs,
    apiVersion: Int
) : FeatureSupport {

    override val isMLSSupported: Boolean = kaliumConfigs.isMLSSupportEnabled && apiVersion >= 3
}

package com.wire.kalium.logic.featureFlags

interface FeatureSupport {
    val isMLSSupported: Boolean
}

class FeatureSupportImpl(
    kaliumConfigs: KaliumConfigs,
    apiVersion: Int
) : FeatureSupport {

    override val isMLSSupported: Boolean = kaliumConfigs.isMLSSupportEnabled && apiVersion >= 2
}

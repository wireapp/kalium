package com.wire.kalium.logic.data.featureConfig

// we didn't follow name convention because Enum.valueOf() only checks the constant name
@Suppress("EnumNaming")
enum class FeatureConfigStatus {
    enabled,
    disabled
}

@Suppress("EnumNaming")
enum class FeatureConfigName {
    fileSharing
}

package com.wire.kalium.logic.data.featureConfig

// we didn't follow name convention because Enum.valueOf() only checks the constant name
enum class FeatureConfigStatus {
    enabled,
    disabled
}

enum class FeatureConfigName {
    fileSharing
}

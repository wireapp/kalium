package com.wire.kalium.logic.data.featureConfig


enum class FeatureConfigStatus(val status: String) {
    ENABLED("enabled"),
    DISABLED("disabled")
}

enum class FeatureConfigName(val featureName: String) {
    FILE_SHARING("fileSharing")
}

package com.wire.kalium.logger

class UserScopedLogger(
    userId: String,
    userDomain: String,
    private val tag: String = "UserLogger"
) : KaliumLogger(Config.userConfig(userId, userDomain, tag)) {
    @Suppress("unused")
    override fun withFeatureId(featureId: Companion.ApplicationFlow): KaliumLogger {
        val currentLogger = this
        currentLogger.kermitLogger = kermitLogger.withTag("$tag:featureId:${featureId.name.lowercase()}")
        return currentLogger
    }

}

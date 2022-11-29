package com.wire.kalium.logger

class UserScopedLogger(
    userId: String, userDomain: String, tag: String = "UserLogger"
): KaliumLogger(KaliumLogger.Config.userConfig(userId , userDomain, tag))

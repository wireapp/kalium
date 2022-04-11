package com.wire.kalium.logic.data.sso

import com.wire.kalium.logic.configuration.ServerConfig

object SSOUtil {
    internal fun generateSuccessRedirect(serverConfig: ServerConfig) =
        "wire://$SUCCESS_HOST/?\$cookie&$QUERY_USER_ID=\$user\$$QUERY_SERVER_CONFIG=${serverConfig.title}"

    internal fun generateErrorRedirect() = "wire://$ERROR_HOST/?\$label"

    const val QUERY_USER_ID = "user"
    const val QUERY_SERVER_CONFIG = "location"
    const val SUCCESS_HOST = "sso-success.kalium"
    const val ERROR_HOST = "sso-error.kalium"
}

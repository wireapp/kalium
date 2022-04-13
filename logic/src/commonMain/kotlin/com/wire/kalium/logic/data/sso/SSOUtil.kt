package com.wire.kalium.logic.data.sso

object SSOUtil {
    internal fun generateSuccessRedirect(serverConfigId: String) =
        "wire://$SUCCESS_HOST/?\$cookie&$QUERY_USER_ID=\$user\$$QUERY_SERVER_CONFIG=${serverConfigId}"

    internal fun generateErrorRedirect() = "wire://$ERROR_HOST/?\$label"

    const val QUERY_USER_ID = "user"
    const val QUERY_SERVER_CONFIG = "location"
    const val SUCCESS_HOST = "sso-success.kalium"
    const val ERROR_HOST = "sso-error.kalium"
}

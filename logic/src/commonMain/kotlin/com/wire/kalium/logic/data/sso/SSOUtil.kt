package com.wire.kalium.logic.data.sso

object SSOUtil {
    internal fun generateSuccessRedirect(serverConfigId: String) =
        "wire://$SUCCESS_PATH/?$QUERY_COOKIES=\$cookie&$QUERY_USER_ID=\$userId\$$QUERY_SERVER_CONFIG=${serverConfigId}"

    internal fun generateErrorRedirect() = "wire://$SSO_LOGIN_HOST/$ERROR_PATH/?$QUERY_ERROR=\$label"

    const val QUERY_USER_ID = "user"
    const val QUERY_COOKIES = "cookies"
    const val QUERY_SERVER_CONFIG = "location"
    const val QUERY_ERROR = "error"
    const val SSO_LOGIN_HOST = "sso-login"
    const val SUCCESS_PATH = "success"
    const val ERROR_PATH = "failure"
}

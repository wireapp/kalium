package com.wire.kalium.logic.data.sso

object SSOUtil {
    internal fun generateSuccessRedirect(serverConfigId: String) =
        "wire://$SSO_LOGIN_HOST/$SUCCESS_PATH/?$QUERY_COOKIE=\$cookie&$QUERY_USER_ID=\$userid&$QUERY_SERVER_CONFIG=${serverConfigId}"

    internal fun generateErrorRedirect() = "wire://$SSO_LOGIN_HOST/$ERROR_PATH/?$QUERY_ERROR=\$label"

    private const val QUERY_USER_ID = "userId"
    private const val QUERY_COOKIE = "cookie"
    private const val QUERY_SERVER_CONFIG = "location"
    private const val QUERY_ERROR = "errorCode"
    private const val SSO_LOGIN_HOST = "sso-login"
    private const val SUCCESS_PATH = "success"
    private const val ERROR_PATH = "failure"
}

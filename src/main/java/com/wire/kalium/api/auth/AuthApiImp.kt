package com.wire.kalium.api.auth

import javax.ws.rs.core.Cookie

class AuthApiImp: AuthApi {
    override fun renewAccessToken(cookie: Cookie): RenewAccessTokenResponse {
        TODO("Not yet implemented")
    }

    override fun removeCookies(removeCookiesRequest: RemoveCookiesRequest) {
        TODO("Not yet implemented")
    }
}

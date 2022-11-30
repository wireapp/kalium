package com.wire.kalium.logic.feature.session

import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.session.SessionManager

internal interface GetProxyCredentialsUseCase {
    operator fun invoke(): ProxyCredentialsDTO?
}

internal class GetProxyCredentialsUseCaseImpl internal constructor(
    private val sessionManager: SessionManager
) : GetProxyCredentialsUseCase {
    override fun invoke(): ProxyCredentialsDTO? = sessionManager.proxyCredentials()
}

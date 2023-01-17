package com.wire.kalium.logic.feature.session

import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

internal interface GetProxyCredentialsUseCase {
    suspend operator fun invoke(): ProxyCredentialsDTO?
}

internal class GetProxyCredentialsUseCaseImpl internal constructor(
    private val sessionManager: SessionManager,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : GetProxyCredentialsUseCase {
    override suspend fun invoke(): ProxyCredentialsDTO? =
        withContext(dispatchers.default) {
            sessionManager.proxyCredentials()
        }
}

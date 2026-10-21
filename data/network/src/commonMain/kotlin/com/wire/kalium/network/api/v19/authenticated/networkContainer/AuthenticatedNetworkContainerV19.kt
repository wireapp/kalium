/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.network.api.v19.authenticated.networkContainer

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.api.base.authenticated.meeting.MeetingApi
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.v18.authenticated.networkContainer.AuthenticatedNetworkContainerV18
import com.wire.kalium.network.api.v19.authenticated.MeetingApiV19
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.network.session.SessionManager
import io.ktor.client.engine.HttpClientEngine
import io.ktor.websocket.WebSocketSession

@Suppress("LongParameterList")
internal class AuthenticatedNetworkContainerV19 internal constructor(
    sessionManager: SessionManager,
    nomadServiceUrl: String? = null,
    selfUserId: UserId,
    certificatePinning: CertificatePinning,
    mockEngine: HttpClientEngine?,
    mockWebSocketSession: WebSocketSession?,
    kaliumLogger: KaliumLogger,
    engine: HttpClientEngine = mockEngine ?: defaultHttpEngine(
        serverConfigDTOApiProxy = sessionManager.serverConfig().links.apiProxy,
        proxyCredentials = sessionManager.proxyCredentials(),
        certificatePinning = certificatePinning
    )
) : AuthenticatedNetworkContainerV18(
    sessionManager = sessionManager,
    nomadServiceUrl = nomadServiceUrl,
    selfUserId = selfUserId,
    certificatePinning = certificatePinning,
    mockEngine = mockEngine,
    mockWebSocketSession = mockWebSocketSession,
    kaliumLogger = kaliumLogger,
    engine = engine
) {
    override val meetingApi: MeetingApi get() = MeetingApiV19(networkClient)
}

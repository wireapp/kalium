package com.wire.kalium.logic.data.id

import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FederatedIdMapperTest {

    lateinit var federatedIdMapper: FederatedIdMapper

    @Mock
    private val sessionRepository = mock(classOf<SessionRepository>())

    @Mock
    private val serverConfigRepository = mock(classOf<ServerConfigRepository>())

    @Mock
    private val qualifiedIdMapper = mock(classOf<QualifiedIdMapper>())

    private val qualifiedId = "aaa-bbb-ccc@wire.com"

    @BeforeTest
    fun setUp() {
        federatedIdMapper =
            FederatedIdMapperImpl(selfUserId, qualifiedIdMapper, sessionRepository)

        given(qualifiedIdMapper).invocation { qualifiedIdMapper.fromStringToQualifiedID(qualifiedId) }
            .then { QualifiedID("aaa-bbb-ccc", "wire.com") }
    }

    @Test
    fun givenAUserId_whenCurrentEnvironmentIsFederated_thenShouldMapTheValueWithDomain() = runTest {
        given(sessionRepository)
            .function(sessionRepository::userSession)
            .whenInvokedWith(any())
            .then { Either.Right(authSession) }

        given(serverConfigRepository)
            .function(serverConfigRepository::configByLinks)
            .whenInvokedWith(any())
            .then { Either.Right(serverConfigFederated) }

        val federatedId = federatedIdMapper.parseToFederatedId(qualifiedId)

        assertEquals(qualifiedId, federatedId)
    }

    @Test
    fun givenAUserId_whenCurrentEnvironmentIsNotFederated_thenShouldMapTheValueWithoutDomain() = runTest {
        given(sessionRepository)
            .function(sessionRepository::userSession)
            .whenInvokedWith(any())
            .then { Either.Right(authSession) }

        given(serverConfigRepository)
            .function(serverConfigRepository::configByLinks)
            .whenInvokedWith(any())
            .then { Either.Right(serverConfigNonFederated) }

        val federatedId = federatedIdMapper.parseToFederatedId(qualifiedId)

        assertEquals("aaa-bbb-ccc", federatedId)
    }

    companion object {
        val selfUserId = UserId("aaa-bbb-ccc", "wire.com")
        val serverConfigFederated = newServerConfig(1, federationEnabled = true)
        val serverConfigNonFederated = newServerConfig(2, federationEnabled = false)

        val authSession = AuthSession(
            AuthSession.Session.Valid(
                selfUserId,
                "accessToken",
                "refreshToken",
                "token_type",
            ),
            serverConfigFederated.links
        )
    }
}

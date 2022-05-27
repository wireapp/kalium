package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.configuration.server.toCommonApiVersionType
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.logic.util.stubs.newServerConfigEntity
import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.model.PersistenceSession
import com.wire.kalium.persistence.model.ServerConfigEntity
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import com.wire.kalium.network.api.UserId as UserIdDTO

class SessionMapperTest {

    @Mock
    val serverConfigMapper: ServerConfigMapper = mock(classOf<ServerConfigMapper>())

    @Mock
    val idMapper = mock(classOf<IdMapper>())

    private lateinit var sessionMapper: SessionMapper

    @BeforeTest
    fun setup() {
        sessionMapper = SessionMapperImpl(serverConfigMapper, idMapper)
    }


    @Test
    fun givenAnAuthSession_whenMappingToSessionCredentials_thenValuesAreMappedCorrectly() {
        val authSession: AuthSession = randomAuthSession()

        given(idMapper).invocation { toApiModel(authSession.tokens.userId) }
            .then { QualifiedID(authSession.tokens.userId.value, authSession.tokens.userId.domain) }
        val acuteValue: SessionDTO =
            with(authSession.tokens) { SessionDTO(UserIdDTO(userId.value, userId.domain), tokenType, accessToken, refreshToken) }

        val expectedValue: SessionDTO = sessionMapper.toSessionDTO(authSession)
        assertEquals(expectedValue, acuteValue)
    }

    @Test
    fun givenAnAuthSession_whenMappingToPersistenceSession_thenValuesAreMappedCorrectly() {
        val authSession: AuthSession = randomAuthSession()
        val serverConfigEntity = with(authSession.serverConfig) {
            ServerConfigEntity(
                id,
                links.api,
                links.accounts,
                links.webSocket,
                links.blackList,
                links.teams,
                links.website,
                links.title,
                metaData.federation,
                metaData.commonApiVersion.version,
                metaData.domain
            )
        }

        given(idMapper).invocation { toDaoModel(authSession.tokens.userId) }
            .then { PersistenceQualifiedId(authSession.tokens.userId.value, authSession.tokens.userId.domain) }
        given(serverConfigMapper).invocation { toEntity(authSession.serverConfig) }.then { serverConfigEntity }

        val expected: PersistenceSession = with(authSession.tokens) {
            PersistenceSession(
                userId = UserIDEntity(userId.value, userId.domain),
                tokenType = tokenType,
                accessToken = accessToken,
                refreshToken = refreshToken,
                serverConfigEntity = serverConfigEntity
            )
        }

        val actual: PersistenceSession = sessionMapper.toPersistenceSession(authSession)
        assertEquals(expected, actual)
        verify(serverConfigMapper).invocation { toEntity(authSession.serverConfig) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAPersistenceSession_whenMappingFromPersistenceSession_thenValuesAreMappedCorrectly() {
        val persistenceSession: PersistenceSession = randomPersistenceSession()
        val serverConfig = with(persistenceSession.serverConfigEntity) {
            ServerConfig(
                id,
                ServerConfig.Links(
                    apiBaseUrl,
                    accountBaseUrl,
                    webSocketBaseUrl,
                    blackListUrl,
                    teamsUrl,
                    websiteUrl,
                    title,
                    ),
                ServerConfig.MetaData(
                    federation,
                    commonApiVersion.toCommonApiVersionType(),
                    domain
                )
            )
        }

        given(idMapper).invocation { fromDaoModel(persistenceSession.userId) }
            .then { UserId(persistenceSession.userId.value, persistenceSession.userId.domain) }
        given(serverConfigMapper).invocation { fromEntity(persistenceSession.serverConfigEntity) }.then { serverConfig }

        val acuteValue: AuthSession = with(persistenceSession) {
            AuthSession(
                AuthSession.Tokens(
                    userId = UserId(userId.value, userId.domain),
                    tokenType = tokenType,
                    accessToken = accessToken,
                    refreshToken = refreshToken
                ),
                serverConfig = serverConfig
            )
        }

        val expectedValue: AuthSession = sessionMapper.fromPersistenceSession(persistenceSession)
        assertEquals(expectedValue, acuteValue)
        verify(serverConfigMapper).invocation { fromEntity(persistenceSession.serverConfigEntity) }.wasInvoked(exactly = once)
        verify(idMapper).invocation { fromDaoModel(persistenceSession.userId) }.wasInvoked(exactly = once)
    }


    private companion object {
        val randomString get() = Random.nextBytes(64).decodeToString()
        val userId = UserId("user_id", "user.domain.io")

        fun randomAuthSession(): AuthSession =
            AuthSession(AuthSession.Tokens(userId, randomString, randomString, randomString), TEST_CONFIG)

        fun randomPersistenceSession(): PersistenceSession =
            PersistenceSession(UserIDEntity(userId.value, userId.domain), randomString, randomString, randomString, TEST_ENTITY)

        val TEST_CONFIG: ServerConfig = newServerConfig(1)

        val TEST_ENTITY: ServerConfigEntity = newServerConfigEntity(1)
    }
}

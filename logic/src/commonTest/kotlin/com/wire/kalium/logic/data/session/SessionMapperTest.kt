package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.logic.util.stubs.newServerConfigEntity
import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.model.AuthSessionEntity
import com.wire.kalium.persistence.model.ServerConfigEntity
import com.wire.kalium.persistence.model.SsoIdEntity
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

        given(idMapper).invocation { toApiModel(authSession.token.userId) }
            .then { QualifiedID(authSession.token.userId.value, authSession.token.userId.domain) }
        val acuteValue: SessionDTO =
            with(authSession.token as AuthSession.Token.Valid) {
                SessionDTO(
                    UserIdDTO(userId.value, userId.domain),
                    tokenType,
                    accessToken,
                    refreshToken
                )
            }

        val expectedValue: SessionDTO = sessionMapper.toSessionDTO(authSession.token as AuthSession.Token.Valid)
        assertEquals(expectedValue, acuteValue)
    }

    @Test
    fun givenAnAuthSession_whenMappingToPersistenceSession_thenValuesAreMappedCorrectly() {
        val authSession: AuthSession = randomAuthSession()
        val serverConfigEntity = with(authSession.serverLinks) {
            ServerConfigEntity.Links(api, accounts, webSocket, blackList, teams, website, title, isOnPremises)
        }

        given(idMapper).invocation { toDaoModel(authSession.token.userId) }
            .then { PersistenceQualifiedId(authSession.token.userId.value, authSession.token.userId.domain) }
        given(serverConfigMapper).invocation { toEntity(authSession.serverLinks) }.then { serverConfigEntity }

        given(idMapper).invocation { idMapper.toSsoIdEntity(TEST_SSO_ID) }.then { TEST_SSO_ID_ENTITY }

        val expected: AuthSessionEntity = with(authSession.token as AuthSession.Token.Valid) {
            AuthSessionEntity.Valid(
                userId = UserIDEntity(userId.value, userId.domain),
                tokenType = tokenType,
                accessToken = accessToken,
                refreshToken = refreshToken,
                serverLinks = serverConfigEntity,
                ssoId = TEST_SSO_ID_ENTITY
            )
        }

        val actual: AuthSessionEntity = sessionMapper.toPersistenceSession(authSession, TEST_SSO_ID)
        assertEquals(expected, actual)
        verify(serverConfigMapper).invocation { toEntity(authSession.serverLinks) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAPersistenceSession_whenMappingFromPersistenceSession_thenValuesAreMappedCorrectly() {
        val authSessionEntity: AuthSessionEntity.Valid = randomPersistenceSession()
        val serverLinks = with(authSessionEntity.serverLinks) {
            ServerConfig.Links(api, accounts, webSocket, blackList, teams, website, title, isOnPremises)
        }

        given(idMapper).invocation { fromDaoModel(authSessionEntity.userId) }
            .then { UserId(authSessionEntity.userId.value, authSessionEntity.userId.domain) }
        given(serverConfigMapper).invocation { fromEntity(authSessionEntity.serverLinks) }.then { serverLinks }

        val acuteValue: AuthSession = with(authSessionEntity) {
            AuthSession(
                AuthSession.Token.Valid(
                    userId = UserId(userId.value, userId.domain),
                    tokenType = tokenType,
                    accessToken = accessToken,
                    refreshToken = refreshToken
                ), serverLinks = serverLinks
            )
        }

        val expectedValue: AuthSession = sessionMapper.fromPersistenceSession(authSessionEntity)
        assertEquals(expectedValue, acuteValue)
        verify(serverConfigMapper).invocation { fromEntity(authSessionEntity.serverLinks) }.wasInvoked(exactly = once)
        verify(idMapper).invocation { fromDaoModel(authSessionEntity.userId) }.wasInvoked(exactly = once)
    }

    private companion object {
        val randomString get() = Random.nextBytes(64).decodeToString()
        val userId = UserId("user_id", "user.domain.io")
        fun randomAuthSession(): AuthSession =
            AuthSession(AuthSession.Token.Valid(userId, randomString, randomString, randomString), TEST_CONFIG.links)

        fun randomPersistenceSession(): AuthSessionEntity.Valid =
            AuthSessionEntity.Valid(
                UserIDEntity(userId.value, userId.domain),
                randomString,
                randomString,
                randomString,
                TEST_ENTITY.links,
                TEST_SSO_ID_ENTITY
            )

        val TEST_CONFIG: ServerConfig = newServerConfig(1)

        val TEST_ENTITY: ServerConfigEntity = newServerConfigEntity(1)
        val TEST_SSO_ID = SsoId("scim_external", "subject", null)
        val TEST_SSO_ID_ENTITY = SsoIdEntity("scim_external", "subject", null)
    }
}

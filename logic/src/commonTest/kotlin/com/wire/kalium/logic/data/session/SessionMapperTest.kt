package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AuthTokens
import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.persistence.client.AuthTokenEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.model.SsoIdEntity
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
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
    fun givenAnAuthTokens_whenMappingToSessionCredentials_thenValuesAreMappedCorrectly() {
        val authSession: AuthTokens = TEST_AUTH_TOKENS

        given(idMapper).invocation { toApiModel(authSession.userId) }
            .then { QualifiedID(authSession.userId.value, authSession.userId.domain) }

        val acuteValue: SessionDTO =
            with(authSession) {
                SessionDTO(
                    UserIdDTO(userId.value, userId.domain),
                    tokenType,
                    accessToken,
                    refreshToken
                )
            }

        val expectedValue: SessionDTO = sessionMapper.toSessionDTO(authSession)
        assertEquals(expectedValue, acuteValue)
    }

    @Test
    fun givenAnAuthTokens_whenMappingToPersistenceAuthTokens_thenValuesAreMappedCorrectly() {
        val authSession: AuthTokens = TEST_AUTH_TOKENS

        given(idMapper).invocation { toDaoModel(authSession.userId) }
            .then { PersistenceQualifiedId(authSession.userId.value, authSession.userId.domain) }

        given(idMapper).invocation { idMapper.toSsoIdEntity(TEST_SSO_ID) }.then { TEST_SSO_ID_ENTITY }

        val expected: AuthTokenEntity = with(authSession) {
            AuthTokenEntity(
                userId = UserIDEntity(userId.value, userId.domain),
                tokenType = tokenType,
                accessToken = accessToken,
                refreshToken = refreshToken
            )
        }

        val actual: AuthTokenEntity = sessionMapper.toAuthTokensEntity(authSession)
        assertEquals(expected, actual)
    }

    private companion object {
        val userId = UserId("user_id", "user.domain.io")

        val TEST_AUTH_TOKENS = AuthTokens(
            userId = userId,
            tokenType = "Bearer",
            accessToken = "access_token",
            refreshToken = "refresh_token"
        )

        val TEST_SSO_ID = SsoId("scim_external", "subject", null)
        val TEST_SSO_ID_ENTITY = SsoIdEntity("scim_external", "subject", null)
    }
}

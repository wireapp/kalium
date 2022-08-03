package com.wire.kalium.logic.data.id

import com.wire.kalium.logic.configuration.server.CURRENT_DOMAIN
import com.wire.kalium.logic.configuration.server.FEDERATION_ENABLED
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import io.mockative.Mock
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
    private val userRepository = mock(classOf<UserRepository>())

    @Mock
    private val qualifiedIdMapper = mock(classOf<QualifiedIdMapper>())

    @Mock
    private val kaliumPreferences = mock(classOf<KaliumPreferences>())

    private val qualifiedId = "aaa-bbb-ccc@wire.com"

    @BeforeTest
    fun setUp() {
        federatedIdMapper = FederatedIdMapperImpl(userRepository, qualifiedIdMapper, kaliumPreferences)

        given(kaliumPreferences)
            .invocation { getString(CURRENT_DOMAIN) }
            .then { "wire.com" }

        given(qualifiedIdMapper).invocation { qualifiedIdMapper.fromStringToQualifiedID(qualifiedId) }
            .then { QualifiedID("aaa-bbb-ccc", "wire.com") }

    }

    @Test
    fun givenAUserId_whenCurrentEnvironmentIsFederated_thenShouldMapTheValueWithDomain() = runTest {
        given(kaliumPreferences)
            .invocation { getBoolean(FEDERATION_ENABLED, false) }
            .then { true }

        val federatedId = federatedIdMapper.parseToFederatedId(qualifiedId)

        assertEquals(qualifiedId, federatedId)
    }

    @Test
    fun givenAUserId_whenCurrentEnvironmentIsNotFederated_thenShouldMapTheValueWithoutDomain() = runTest {
        given(kaliumPreferences)
            .invocation { getBoolean(FEDERATION_ENABLED, false) }
            .then { false }

        val federatedId = federatedIdMapper.parseToFederatedId(qualifiedId)

        assertEquals("aaa-bbb-ccc", federatedId)
    }
}

package com.wire.kalium.logic.data.id

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FederatedIdMapperTest {

    lateinit var federatedIdMapper: FederatedIdMapper

    @Mock
    private val sessionRepository = mock(classOf<SessionRepository>())

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
            .function(sessionRepository::isFederated)
            .whenInvokedWith(any())
            .then { Either.Right(true) }

        val federatedId = federatedIdMapper.parseToFederatedId(qualifiedId)

        assertEquals(qualifiedId, federatedId)
    }

    @Test
    fun givenAUserId_whenCurrentEnvironmentIsNotFederated_thenShouldMapTheValueWithoutDomain() = runTest {
        given(sessionRepository)
            .function(sessionRepository::isFederated)
            .whenInvokedWith(any())
            .then { Either.Right(false) }

        val federatedId = federatedIdMapper.parseToFederatedId(qualifiedId)

        assertEquals("aaa-bbb-ccc", federatedId)
    }

    @Test
    fun givenError_whenGettingUserFederationStatus_thenShouldMapTheValueWithoutDomain() = runTest {
        given(sessionRepository)
            .function(sessionRepository::isFederated)
            .whenInvokedWith(any())
            .then { Either.Left(StorageFailure.Generic(IOException("why are we still here just to suffer!"))) }

        val federatedId = federatedIdMapper.parseToFederatedId(qualifiedId)

        assertEquals("aaa-bbb-ccc", federatedId)
    }

    companion object {
        val selfUserId = UserId("aaa-bbb-ccc", "wire.com")
    }
}

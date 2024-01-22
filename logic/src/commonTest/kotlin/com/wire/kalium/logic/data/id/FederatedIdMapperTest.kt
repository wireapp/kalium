/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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

    private lateinit var federatedIdMapper: FederatedIdMapper

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
            .suspendFunction(sessionRepository::isFederated)
            .whenInvokedWith(any())
            .then { Either.Right(true) }

        val federatedId = federatedIdMapper.parseToFederatedId(qualifiedId)

        assertEquals(qualifiedId, federatedId)
    }

    @Test
    fun givenAUserId_whenCurrentEnvironmentIsNotFederated_thenShouldMapTheValueWithoutDomain() = runTest {
        given(sessionRepository)
            .suspendFunction(sessionRepository::isFederated)
            .whenInvokedWith(any())
            .then { Either.Right(false) }

        val federatedId = federatedIdMapper.parseToFederatedId(qualifiedId)

        assertEquals("aaa-bbb-ccc", federatedId)
    }

    @Test
    fun givenError_whenGettingUserFederationStatus_thenShouldMapTheValueWithoutDomain() = runTest {
        given(sessionRepository)
            .suspendFunction(sessionRepository::isFederated)
            .whenInvokedWith(any())
            .then { Either.Left(StorageFailure.Generic(IOException("why are we still here just to suffer!"))) }

        val federatedId = federatedIdMapper.parseToFederatedId(qualifiedId)

        assertEquals("aaa-bbb-ccc", federatedId)
    }

    companion object {
        val selfUserId = UserId("aaa-bbb-ccc", "wire.com")
    }
}

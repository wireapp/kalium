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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import io.mockative.any
import io.mockative.coEvery
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FederatedIdMapperTest {

    private lateinit var federatedIdMapper: FederatedIdMapper

    private val sessionRepository = mock(SessionRepository::class)
    private val qualifiedIdMapper = mock(QualifiedIdMapper::class)

    private val qualifiedId = "aaa-bbb-ccc@wire.com"

    @BeforeTest
    fun setUp() {
        federatedIdMapper =
            FederatedIdMapperImpl(selfUserId, qualifiedIdMapper, sessionRepository)

        every {
            qualifiedIdMapper.fromStringToQualifiedID(qualifiedId)
        }.returns(QualifiedID("aaa-bbb-ccc", "wire.com"))
    }

    @Test
    fun givenAUserId_whenCurrentEnvironmentIsFederated_thenShouldMapTheValueWithDomain() = runTest {
        coEvery {
            sessionRepository.isFederated(any())
        }.returns(Either.Right(true))

        val federatedId = federatedIdMapper.parseToFederatedId(qualifiedId)

        assertEquals(qualifiedId, federatedId)
    }

    @Test
    fun givenAUserId_whenCurrentEnvironmentIsNotFederated_thenShouldMapTheValueWithoutDomain() = runTest {
        coEvery {
            sessionRepository.isFederated(any())
        }.returns(Either.Right(false))

        val federatedId = federatedIdMapper.parseToFederatedId(qualifiedId)

        assertEquals("aaa-bbb-ccc", federatedId)
    }

    @Test
    fun givenError_whenGettingUserFederationStatus_thenShouldMapTheValueWithoutDomain() = runTest {
        coEvery {
            sessionRepository.isFederated(any())
        }.returns(Either.Left(StorageFailure.Generic(IOException("why are we still here just to suffer!"))))

        val federatedId = federatedIdMapper.parseToFederatedId(qualifiedId)

        assertEquals("aaa-bbb-ccc", federatedId)
    }

    companion object {
        val selfUserId = UserId("aaa-bbb-ccc", "wire.com")
    }
}

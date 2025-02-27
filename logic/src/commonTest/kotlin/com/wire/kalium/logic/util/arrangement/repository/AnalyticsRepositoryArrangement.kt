/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.analytics.AnalyticsRepository
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.datetime.Instant

@Suppress("INAPPLICABLE_JVM_NAME")
internal interface AnalyticsRepositoryArrangement {
    val analyticsRepository: AnalyticsRepository
    suspend fun withContactsAmountCached(result: Either<StorageFailure, Int>)
    suspend fun withCountContactsAmount(result: Either<StorageFailure, Int>)
    suspend fun withTeamMembersAmountCached(result: Either<StorageFailure, Int>)
    suspend fun withCountTeamMembersAmount(result: Either<StorageFailure, Int>)
    suspend fun withSetContactsAmountCachingDate()
    suspend fun withSetTeamMembersAmountCached()
    suspend fun withSetContactsAmountCached()
    suspend fun withLastContactsDateUpdateDate(result: Either<StorageFailure, Instant>)
}

@Suppress("INAPPLICABLE_JVM_NAME")
internal open class AnalyticsRepositoryArrangementImpl : AnalyticsRepositoryArrangement {
    @Mock
    override val analyticsRepository: AnalyticsRepository = mock(AnalyticsRepository::class)

    override suspend fun withContactsAmountCached(result: Either<StorageFailure, Int>) {
        coEvery { analyticsRepository.getContactsAmountCached() }.returns(result)
    }

    override suspend fun withCountContactsAmount(result: Either<StorageFailure, Int>) {
        coEvery { analyticsRepository.countContactsAmount() }.returns(result)
    }

    override suspend fun withTeamMembersAmountCached(result: Either<StorageFailure, Int>) {
        coEvery { analyticsRepository.getTeamMembersAmountCached() }.returns(result)
    }

    override suspend fun withCountTeamMembersAmount(result: Either<StorageFailure, Int>) {
        coEvery { analyticsRepository.countTeamMembersAmount() }.returns(result)
    }

    override suspend fun withSetContactsAmountCachingDate() {
        coEvery { analyticsRepository.setContactsAmountCachingDate(any()) }.returns(Unit)
    }

    override suspend fun withSetTeamMembersAmountCached() {
        coEvery { analyticsRepository.setTeamMembersAmountCached(any()) }.returns(Unit)
    }

    override suspend fun withSetContactsAmountCached() {
        coEvery { analyticsRepository.setContactsAmountCached(any()) }.returns(Unit)
    }

    override suspend fun withLastContactsDateUpdateDate(result: Either<StorageFailure, Instant>) {
        coEvery { analyticsRepository.getLastContactsDateUpdateDate() }.returns(result)
    }
}

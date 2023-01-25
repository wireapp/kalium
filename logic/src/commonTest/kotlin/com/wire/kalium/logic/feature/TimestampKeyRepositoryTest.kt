/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature

import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

class TimestampKeyRepositoryTest {
    @Test
    fun givenNoPreviousTimestamp_whenCallHasPassed_thenReturnsTrue() = runTest {
        val key = TimestampKeys.LAST_KEY_PACKAGE_COUNT_CHECK
        val (_, timestampKeyRepository) = Arrangement()
            .withMetaDataDaoValueReturns(null)
            .arrange()

        val result = timestampKeyRepository
            .hasPassed(key, 24.hours)

        result.shouldSucceed { hasPassed ->
            assertEquals(hasPassed, true)
        }
    }

    @Test
    fun givenPreviousTimestamp_whenCallHasPassedWithDistantFuture_thenReturnsFalse() = runTest {
        val key = TimestampKeys.LAST_KEY_PACKAGE_COUNT_CHECK
        val expectedTimestamp = Instant.DISTANT_FUTURE
        val (_, timestampKeyRepository) = Arrangement()
            .withMetaDataDaoValueReturns(expectedTimestamp)
            .arrange()

        val result = timestampKeyRepository
            .hasPassed(key, 24.hours)

        result.shouldSucceed { hasPassed ->
            assertEquals(hasPassed, false)
        }
    }

    @Test
    fun givenATimeStampKey_whenCallReset_thenDAOFunctionCalledByCorrectValue() = runTest {
        val key = TimestampKeys.LAST_KEY_PACKAGE_COUNT_CHECK
        val (arrangement, timestampKeyRepository) = Arrangement()
            .withMetaDataDaoInsertValue()
            .arrange()

        val result = timestampKeyRepository
            .reset(key)

        result.shouldSucceed()

        verify(arrangement.metadataDAO)
            .suspendFunction(arrangement.metadataDAO::insertValue)
            .with(anything(), eq(key.name))
            .wasInvoked(once)
    }

    @Test
    fun givenATimeStampKeyAndTimeStamp_whenCallUpdate_thenDAOFunctionCalledByCorrectValue() = runTest {
        val key = TimestampKeys.LAST_KEY_PACKAGE_COUNT_CHECK
        val timestamp = Instant.DISTANT_FUTURE
        val (arrangement, timestampKeyRepository) = Arrangement()
            .withMetaDataDaoInsertValue()
            .arrange()

        val result = timestampKeyRepository
            .update(key, timestamp)

        result.shouldSucceed()

        verify(arrangement.metadataDAO)
            .suspendFunction(arrangement.metadataDAO::insertValue)
            .with(eq(timestamp.toIsoDateTimeString()), eq(key.name))
            .wasInvoked(once)
    }

    private class Arrangement {
        @Mock
        val metadataDAO = mock(classOf<MetadataDAO>())

        fun withMetaDataDaoValueReturns(timestamp: Instant?) = apply {
            given(metadataDAO).suspendFunction(metadataDAO::valueByKeyFlow)
                .whenInvokedWith(anything())
                .thenReturn(flowOf(timestamp?.toIsoDateTimeString()))
        }

        fun withMetaDataDaoInsertValue() = apply {
            given(metadataDAO).suspendFunction(metadataDAO::insertValue)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Unit)
        }

        fun arrange() = this to TimestampKeyRepositoryImpl(metadataDAO)
    }
}

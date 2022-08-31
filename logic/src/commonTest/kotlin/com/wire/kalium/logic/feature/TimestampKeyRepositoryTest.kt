package com.wire.kalium.logic.feature

import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dao.MetadataDAO
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
            .with(eq(timestamp.toString()), eq(key.name))
            .wasInvoked(once)
    }

    private class Arrangement {
        @Mock
        val metadataDAO = mock(classOf<MetadataDAO>())

        fun withMetaDataDaoValueReturns(timestamp: Instant?) = apply {
            given(metadataDAO).suspendFunction(metadataDAO::valueByKeyFlow)
                .whenInvokedWith(anything())
                .thenReturn(flowOf(timestamp?.toString()))
        }

        fun withMetaDataDaoInsertValue() = apply {
            given(metadataDAO).suspendFunction(metadataDAO::insertValue)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Unit)
        }

        fun arrange() = this to TimestampKeyRepositoryImpl(metadataDAO)
    }
}

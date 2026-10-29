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

package com.wire.kalium.logic.configuration

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserConfigDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class E2EIRotationCheckpointTest {
    @Test
    fun firstEnrollmentAndCompletedRotationHaveNoPendingCheckpoint() = runTest {
        val database = TestUserDatabase(UserIDEntity("checkpoint", "example.test"), StandardTestDispatcher(testScheduler))
        try {
            val repository = UserConfigDataSource(database.builder.userPrefsDAO, database.builder.userConfigDAO, lazy { error("Unused") })
            assertEquals(Either.Right(null), repository.getE2EIRotationCheckpoint())
            val checkpoint = "pending rotation".encodeToByteArray()
            assertEquals(Either.Right(Unit), repository.setE2EIRotationCheckpoint(checkpoint))
            assertContentEquals(checkpoint, assertIs<Either.Right<ByteArray?>>(repository.getE2EIRotationCheckpoint()).value)
            assertEquals(Either.Right(Unit), repository.deleteE2EIRotationCheckpoint())
            assertEquals(Either.Right(null), repository.getE2EIRotationCheckpoint())
        } finally {
            database.builder.close()
            database.delete()
        }
    }

    @Test
    fun corruptOrUnreadableCheckpointIsNotTreatedAsFirstEnrollment() = runTest {
        val database = TestUserDatabase(UserIDEntity("corrupt-checkpoint", "example.test"), StandardTestDispatcher(testScheduler))
        try {
            val dao = object : UserConfigDAO by database.builder.userConfigDAO {
                override suspend fun getE2EIRotationCheckpoint(): String = "not base64!"
            }
            val repository = UserConfigDataSource(database.builder.userPrefsDAO, dao, lazy { error("Unused") })
            assertIs<StorageFailure.Generic>(assertIs<Either.Left<StorageFailure>>(repository.getE2EIRotationCheckpoint()).value)
            val failingDao = object : UserConfigDAO by database.builder.userConfigDAO {
                override suspend fun getE2EIRotationCheckpoint(): String? = error("Storage unavailable")
            }
            val failingRepository = UserConfigDataSource(database.builder.userPrefsDAO, failingDao, lazy { error("Unused") })
            assertIs<StorageFailure.Generic>(assertIs<Either.Left<StorageFailure>>(failingRepository.getE2EIRotationCheckpoint()).value)
        } finally {
            database.builder.close()
            database.delete()
        }
    }

    @Test
    fun cancelledReadRemainsCancellation() = runTest {
        val database = TestUserDatabase(UserIDEntity("cancelled-checkpoint", "example.test"), StandardTestDispatcher(testScheduler))
        try {
            val dao = object : UserConfigDAO by database.builder.userConfigDAO {
                override suspend fun getE2EIRotationCheckpoint(): String? = throw CancellationException("Cancelled")
            }
            val repository = UserConfigDataSource(database.builder.userPrefsDAO, dao, lazy { error("Unused") })
            assertFailsWith<CancellationException> { repository.getE2EIRotationCheckpoint() }
        } finally {
            database.builder.close()
            database.delete()
        }
    }
}

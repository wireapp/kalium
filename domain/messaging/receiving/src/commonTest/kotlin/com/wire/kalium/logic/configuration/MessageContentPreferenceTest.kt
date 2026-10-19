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
import com.wire.kalium.persistence.config.UserConfigStorage
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class MessageContentPreferenceTest {
    @Test
    fun absentSavedCorruptAndRecoveredValuesHaveDistinctResults() = runTest {
        val database = TestUserDatabase(UserIDEntity("mapping", "example.test"), StandardTestDispatcher(testScheduler))
        try {
            val repository = UserConfigDataSource(database.builder.userPrefsDAO, database.builder.userConfigDAO, lazy { error("Unused") })
            assertEquals(Either.Right(MessageContentPreference.Unset), repository.getMessageContentPreference())
            for ((enabled, expected) in listOf(false to MessageContentPreference.Disabled, true to MessageContentPreference.Enabled)) {
                assertEquals(Either.Right(Unit), repository.setMessageContentPreference(enabled))
                assertEquals(Either.Right(expected), repository.getMessageContentPreference())
            }
            database.builder.metadataDAO.insertValue("invalid", UserConfigStorage.UserPreferences.MESSAGE_CONTENT_ENABLED.key)
            assertIs<StorageFailure.Generic>(assertIs<Either.Left<StorageFailure>>(repository.getMessageContentPreference()).value)
            assertEquals(Either.Right(Unit), repository.setMessageContentPreference(false))
            assertEquals(Either.Right(MessageContentPreference.Disabled), repository.getMessageContentPreference())
        } finally {
            database.builder.close()
            database.delete()
        }
    }

    @Test
    fun failedWritesPreserveSavedValueAndReadFailureCanRecover() = runTest {
        val database = TestUserDatabase(UserIDEntity("failure", "example.test"), StandardTestDispatcher(testScheduler))
        try {
            val saved = database.builder.userPrefsDAO
            saved.persistMessageContentPreference(false)
            var fail = true
            val storage = object : UserConfigStorage by saved {
                override suspend fun readMessageContentPreference(): Boolean? {
                    check(!fail) { "Unreadable storage" }
                    return saved.readMessageContentPreference()
                }
                override suspend fun persistMessageContentPreference(enabled: Boolean) {
                    check(!fail) { "Write rejected" }
                    saved.persistMessageContentPreference(enabled)
                }
            }
            val repository = UserConfigDataSource(storage, database.builder.userConfigDAO, lazy { error("Unused") })
            assertIs<Either.Left<StorageFailure>>(repository.getMessageContentPreference())
            assertIs<Either.Left<StorageFailure>>(repository.setMessageContentPreference(true))
            assertEquals(false, saved.readMessageContentPreference())
            fail = false
            assertEquals(Either.Right(MessageContentPreference.Disabled), repository.getMessageContentPreference())
            assertEquals(Either.Right(Unit), repository.setMessageContentPreference(true))
            assertEquals(Either.Right(MessageContentPreference.Enabled), repository.getMessageContentPreference())
        } finally {
            database.builder.close()
            database.delete()
        }
    }

    @Test
    fun writesCompleteAfterStorageAndCancellationPropagates() = runTest {
        val database = TestUserDatabase(UserIDEntity("completion", "example.test"), StandardTestDispatcher(testScheduler))
        try {
            val saved = database.builder.userPrefsDAO
            val gate = CompletableDeferred<Unit>()
            val storage = object : UserConfigStorage by saved {
                override suspend fun readMessageContentPreference(): Boolean? = throw CancellationException("Cancelled read")
                override suspend fun persistMessageContentPreference(enabled: Boolean) {
                    gate.await()
                    saved.persistMessageContentPreference(enabled)
                }
            }
            val repository = UserConfigDataSource(storage, database.builder.userConfigDAO, lazy { error("Unused") })
            assertFailsWith<CancellationException> { repository.getMessageContentPreference() }
            val cancelled = async { repository.setMessageContentPreference(true) }
            runCurrent()
            cancelled.cancel()
            assertFailsWith<CancellationException> { cancelled.await() }
            val pending = async { repository.setMessageContentPreference(false) }
            runCurrent()
            assertFalse(pending.isCompleted)
            gate.complete(Unit)
            assertEquals(Either.Right(Unit), pending.await())
            assertEquals(false, saved.readMessageContentPreference())
        } finally {
            database.builder.close()
            database.delete()
        }
    }
}

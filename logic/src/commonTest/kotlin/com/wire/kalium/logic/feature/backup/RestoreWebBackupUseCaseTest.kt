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

package com.wire.kalium.logic.feature.backup

import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.PersistMigratedMessagesUseCase
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.util.IgnoreIOS
import com.wire.kalium.persistence.dao.MigrationDAO
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Path
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertTrue

@IgnoreIOS // TODO re-enable when BackupUtils is implemented on Darwin
class RestoreWebBackupUseCaseTest {

    private val fakeFileSystem = FakeKaliumFileSystem()

    @Test
    fun givenASupportedBackupVersion_whenRestoringCorrectData_thenTheMigrationIsTriggered() = runTest {
        // given
        val backupPath = fakeFileSystem.rootCachePath
        val (arrangement, useCase) = Arrangement()
            .withUnencryptedBackup()
            .withMigrateMessagesSuccess()
            .arrange()

        // when
        val result = useCase(backupPath, metadata = backupMetadata)

        // then
        assertTrue(result is RestoreBackupResult.Success)
        coVerify {
            arrangement.migrationDAO.insertConversation(any())
        }.wasInvoked(atLeast = once)
        coVerify {
            arrangement.persistMigratedMessagesUseCase.invoke(any(), any())
        }.wasInvoked(atLeast = once)
    }

    @Test
    fun givenAnUnSupportedBackupVersion_whenRestoringCorrectData_thenThenErrorIsReturned() = runTest {
        // given
        val backupPath = fakeFileSystem.rootCachePath
        val (arrangement, useCase) = Arrangement()
            .withUnencryptedBackup()
            .withMigrateMessagesSuccess()
            .arrange()

        // when
        val result = useCase(backupPath, metadata = backupMetadata.copy(version = "1"))

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.IncompatibleBackup)
    }

    @Test
    fun givenAnUnsupportedEventsBackup_whenRestoringData_thenThenErrorIsReturned() = runTest {
        // given
        val backupPath = fakeFileSystem.rootCachePath
        val (arrangement, useCase) = Arrangement()
            .withUnencryptedBackup(
                withWrongEventsFile = true
            )
            .withMigrateMessagesSuccess()
            .arrange()

        // when
        val result = useCase(backupPath, metadata = backupMetadata)

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.BackupIOFailure)
    }

    @Test
    fun givenAnUnsupportedConversationsBackupButProperEventsBackup_whenRestoringData_thenEventsMigrationIsTriggered() = runTest {
        // given
        val backupPath = fakeFileSystem.rootCachePath
        val (arrangement, useCase) = Arrangement()
            .withUnencryptedBackup(
                withWrongEventsFile = false,
                withWrongConversationFile = true
            )
            .withMigrateMessagesSuccess()
            .arrange()

        // when
        val result = useCase(backupPath, metadata = backupMetadata)

        // then
        assertTrue(result is RestoreBackupResult.Success)
        coVerify {
            arrangement.migrationDAO.insertConversation(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.persistMigratedMessagesUseCase.invoke(any(), any())
        }.wasInvoked(atLeast = once)
    }

    private inner class Arrangement {

        @Mock
        val persistMigratedMessagesUseCase = mock(PersistMigratedMessagesUseCase::class)

        @Mock
        val migrationDAO = mock(MigrationDAO::class)

        @Mock
        val restartSlowSyncProcessForRecoveryUseCase = mock(RestartSlowSyncProcessForRecoveryUseCase::class)

        private val selfUserId = currentTestUserId

        private fun createFile(filePath: Path, json: String): Path {
            fakeFileSystem.sink(filePath).buffer().use {
                it.write(json.encodeToByteArray())
            }
            return filePath
        }

        fun withUnencryptedBackup(
            withWrongMetadataFile: Boolean = false,
            withWrongEventsFile: Boolean = false,
            withWrongConversationFile: Boolean = false
        ) = apply {
            with(fakeFileSystem) {
                val metadataFileName = if (withWrongMetadataFile) {
                    "wrong-metadata.json"
                } else {
                    BackupConstants.BACKUP_METADATA_FILE_NAME
                }
                val eventsFileName = if (withWrongEventsFile) {
                    "wrong-events.json"
                } else {
                    BackupConstants.BACKUP_WEB_EVENTS_FILE_NAME
                }
                val conversationsFileName = if (withWrongConversationFile) {
                    "wrong-conversations.json"
                } else {
                    BackupConstants.BACKUP_WEB_CONVERSATIONS_FILE_NAME
                }

                createFile(tempFilePath(metadataFileName), Json.encodeToString(backupMetadata))
                createFile(tempFilePath(eventsFileName), JSON_WEB_EVENTS)
                createFile(tempFilePath(conversationsFileName), JSON_WEB_CONVERSATIONS)
            }
        }

        suspend fun withMigrateMessagesSuccess() = apply {
            coEvery {
                persistMigratedMessagesUseCase.invoke(any(), any())
            }.returns(Either.Right(Unit))
        }

        fun arrange() = this to RestoreWebBackupUseCaseImpl(
            kaliumFileSystem = fakeFileSystem,
            selfUserId = selfUserId,
            migrationDAO = migrationDAO,
            persistMigratedMessages = persistMigratedMessagesUseCase,
            restartSlowSyncProcessForRecovery = restartSlowSyncProcessForRecoveryUseCase
        )
    }

    companion object {
        val currentTestUserId = UserId("some-user-id", "some-domain")
        const val clientId = "dummy-client-id"

        val backupMetadata = BackupMetadata(
            "Web",
            "19",
            currentTestUserId.toString(),
            "2023-03-25T14:17:27.364Z",
            clientId
        )
        const val JSON_WEB_EVENTS = """[
  {
    "conversation": "conversation-id",
    "from": "some-user-id",
    "from_client_id": "some-client-id",
    "id": "message-id",
    "qualified_conversation": {
      "domain": "wire.com",
      "id": "conversation-id"
    },
    "status": 2,
    "time": "2023-02-25T14:17:27.364Z",
    "data": {
      "content": "hey",
      "mentions": [],
      "previews": [],
      "expects_read_confirmation": true,
      "legal_hold_status": 1
    },
    "type": "conversation.message-add",
    "category": 16,
    "primary_key": 3,
    "reactions": {
      "some-user-id": "❤️"
    },
    "version": 2
  }
]"""
        const val JSON_WEB_CONVERSATIONS = """[
  {
    "access": [
      "invite",
      "code"
    ],
    "access_role": [
      "team_member",
      "non_team_member",
      "guest",
      "service"
    ],
    "archived_state": false,
    "archived_timestamp": 0,
    "cleared_timestamp": 0,
    "creator": "3e0d5346-21a6-4a83-ab1b-fde0848731bf",
    "domain": "wire.com",
    "ephemeral_timer": null,
    "epoch": -1,
    "global_message_timer": null,
    "id": "f7a139f4-04a8-42e1-b3a5-24fd4a9547c0",
    "is_guest": false,
    "is_managed": false,
    "last_event_timestamp": 1677334658266,
    "last_read_timestamp": 1677334658266,
    "last_server_timestamp": 1677334658266,
    "legal_hold_status": 1,
    "muted_state": 0,
    "muted_timestamp": 0,
    "name": "#SPAM 4",
    "others": [
      "649b6774-81aa-4e4d-90e5-26e7cadd3ff1",
      "75684917-61a6-44f5-be2e-ff8427e6711a"
    ],
    "protocol": "proteus",
    "qualified_others": [
      {
        "domain": "wire.com",
        "id": "649b6774-81aa-4e4d-90e5-26e7cadd3ff1"
      },
      {
        "domain": "wire.com",
        "id": "75684917-61a6-44f5-be2e-ff8427e6711a"
      }
    ],
    "receipt_mode": 1,
    "roles": {
      "649b6774-81aa-4e4d-90e5-26e7cadd3ff1": "wire_admin",
      "75684917-61a6-44f5-be2e-ff8427e6711a": "wire_member",
      "3e0d5346-21a6-4a83-ab1b-fde0848731bf": "wire_admin"
    },
    "status": 0,
    "team_id": "e1684e2f-39d8-4caf-8e11-0da24a46280b",
    "type": 0
  }
]"""

    }
}

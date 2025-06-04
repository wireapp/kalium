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
package com.wire.kalium.logic.feature.backup

import com.wire.backup.dump.BackupExportResult
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.backup.mapper.toBackupConversation
import com.wire.kalium.logic.feature.backup.mapper.toBackupMessage
import com.wire.kalium.logic.feature.backup.mapper.toBackupUser
import com.wire.kalium.logic.feature.backup.provider.BackupExporter
import com.wire.kalium.logic.feature.backup.provider.MPBackupExporterProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage.TEXT_MESSAGE
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateMPBackupUseCaseTest {

    private val dispatchers = TestKaliumDispatcher

    @BeforeTest
    fun before() {
        Dispatchers.setMain(dispatchers.default)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun givenValidData_whenCreatingBackup_thenDataIsAddedToBackup() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withExporter()
            .withUsers(listOf(testUser))
            .withConversations(listOf(TestConversation.CONVERSATION))
            .withMessages(listOf(TEXT_MESSAGE))
            .arrange()

        val result = useCase("test_password")

        assertTrue(result is CreateBackupResult.Success)
        coVerify { arrangement.exporter.add(testUser.toBackupUser()) }.wasInvoked(1)
        coVerify { arrangement.exporter.add(TestConversation.CONVERSATION.toBackupConversation()) }.wasInvoked(1)
        coVerify { arrangement.exporter.add(TEXT_MESSAGE.toBackupMessage()!!) }.wasInvoked(1)
    }

    @Test
    fun givenZippingFails_whenCreatingBackup_thenErrorIsReturned() = runTest {

        val (_, useCase) = Arrangement()
            .withErrorExporter()
            .withUsers(listOf(testUser))
            .withConversations(listOf(TestConversation.CONVERSATION))
            .withMessages(listOf(TEXT_MESSAGE))
            .arrange()

        val result = useCase("test_password")

        assertTrue(result is CreateBackupResult.Failure)
    }

    private inner class Arrangement {

        val userRepository = mock(UserRepository::class)
        val backupRepository = mock(BackupRepository::class)
        val exporter = mock(BackupExporter::class)
        val exporterProvider = mock(MPBackupExporterProvider::class)

        suspend fun withUsers(users: List<OtherUser>) = apply {
            coEvery { backupRepository.getUsers() }.returns(users)
        }

        suspend fun withConversations(conversations: List<Conversation>) = apply {
            coEvery { backupRepository.getConversations() }.returns(conversations)
        }

        fun withMessages(messages: List<Message.Standalone>) = apply {
            every { backupRepository.getMessages() }.returns(flowOf(messages))
        }

        suspend fun withExporter() = apply {

            coEvery { exporter.finalize(any()) }.returns(BackupExportResult.Success("testPath/backupFile.zip"))

            every {
                exporterProvider.provideExporter(
                    selfUserId = any(),
                    workDirectory = any(),
                    outputDirectory = any(),
                    fileZipper = any(),
                )
            }.returns(exporter)
        }

        suspend fun withErrorExporter() = apply {

            coEvery { exporter.finalize(any()) }.returns(BackupExportResult.Failure.ZipError("Zip failure"))

            every {
                exporterProvider.provideExporter(
                    selfUserId = any(),
                    workDirectory = any(),
                    outputDirectory = any(),
                    fileZipper = any(),
                )
            }.returns(exporter)
        }

        suspend fun arrange(): Pair<Arrangement, CreateMPBackupUseCase> {

            coEvery { userRepository.getSelfUser() }.returns(selfUser.right())

            return this to CreateMPBackupUseCaseImpl(
                backupRepository = backupRepository,
                userRepository = userRepository,
                kaliumFileSystem = FakeKaliumFileSystem(),
                fileSystem = FakeFileSystem(),
                exporterProvider = exporterProvider,
                dispatchers = dispatchers
            )
        }
    }

    private companion object {
        private val selfUserId = QualifiedID("participant1", "domain")
        private val selfUser = SelfUser(
            id = selfUserId,
            name = null,
            handle = "test_user",
            email = null,
            phone = null,
            accentId = 0,
            teamId = null,
            connectionStatus = ConnectionState.NOT_CONNECTED,
            previewPicture = null,
            completePicture = null,
            availabilityStatus = UserAvailabilityStatus.AVAILABLE,
            expiresAt = null,
            supportedProtocols = null,
            userType = UserType.INTERNAL,
        )
        
        private val testUser = OtherUser(
            id = QualifiedID("participant2", "domain"),
            name = null,
            handle = "test_user",
            email = null,
            phone = null,
            accentId = 0,
            teamId = null,
            connectionStatus = ConnectionState.NOT_CONNECTED,
            previewPicture = null,
            completePicture = null,
            availabilityStatus = UserAvailabilityStatus.AVAILABLE,
            expiresAt = null,
            supportedProtocols = null,
            userType = UserType.INTERNAL,
            botService = null,
            deleted = false,
            defederated = false,
            isProteusVerified = false,
        )
    }
}

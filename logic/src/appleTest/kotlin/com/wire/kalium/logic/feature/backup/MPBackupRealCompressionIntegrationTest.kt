/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.backup.PagedData
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.reaction.MessageReactionWithUsers
import com.wire.kalium.logic.data.message.reaction.MessageReactions
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.backup.provider.MPBackupExporterProviderImpl
import com.wire.kalium.logic.feature.backup.provider.MPBackupImporterProviderImpl
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import okio.Path
import okio.Sink
import okio.Source
import okio.buffer
import okio.use
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MPBackupRealCompressionIntegrationTest {

    private val dispatchers = TestKaliumDispatcher
    private val fileSystem = FileSystem.SYSTEM
    private val testDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kalium-mp-backup-${Random.nextInt().toUInt()}"
    private val kaliumFileSystem = RealKaliumFileSystem(testDir)

    @BeforeTest
    fun before() {
        Dispatchers.setMain(dispatchers.default)
        fileSystem.createDirectories(testDir)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        fileSystem.deleteRecursively(testDir, mustExist = false)
    }

    @Test
    fun givenEncryptedMPBackup_whenCreatingAndRestoringWithRealCompression_thenDataIsRestored() = runTest {
        val password = "test_password"
        val exportRepository = BackupRepositoryStub(
            users = listOf(TestUser.OTHER),
            conversations = listOf(TestConversation.CONVERSATION),
            messages = listOf(TestMessage.TEXT_MESSAGE),
            reactions = listOf(testReaction),
        )
        val userRepository = mock(UserRepository::class)
        coEvery { userRepository.getSelfUser() }.returns(TestUser.SELF.right())
        val createUseCase = CreateMPBackupUseCaseImpl(
            backupRepository = exportRepository,
            userRepository = userRepository,
            kaliumFileSystem = kaliumFileSystem,
            fileSystem = fileSystem,
            exporterProvider = MPBackupExporterProviderImpl(fileSystem),
            dispatchers = dispatchers,
        )

        val createResult = createUseCase(password) {}

        assertIs<CreateBackupResult.Success>(createResult)
        assertTrue(fileSystem.exists(createResult.backupFilePath))
        assertTrue((fileSystem.metadata(createResult.backupFilePath).size ?: 0L) > 0)

        val restoreWithWrongPassword = restoreUseCase(BackupRepositoryStub()).invoke(
            createResult.backupFilePath,
            "wrong_password",
        ) {}
        assertEquals(
            RestoreBackupResult.Failure(RestoreBackupResult.BackupRestoreFailure.InvalidPassword),
            restoreWithWrongPassword,
        )

        val restoreRepository = BackupRepositoryStub()
        val restoreResult = restoreUseCase(restoreRepository).invoke(createResult.backupFilePath, password) {}

        assertIs<RestoreBackupResult.Success>(restoreResult)
        assertEquals(listOf(TestUser.OTHER.id.value.lowercase()), restoreRepository.insertedUsers.map { it.id.value })
        assertEquals(listOf(TestConversation.CONVERSATION.id.value.lowercase()), restoreRepository.insertedConversations.map { it.id.value })
        assertEquals(listOf(TestMessage.TEXT_MESSAGE.id.lowercase()), restoreRepository.insertedMessages.map { it.id.lowercase() })
        assertEquals(listOf(testReaction.messageId.lowercase()), restoreRepository.insertedReactions.map { it.messageId.lowercase() })
    }

    private fun restoreUseCase(backupRepository: BackupRepository) = RestoreMPBackupUseCaseImpl(
        selfUserId = TestUser.SELF.id,
        backupRepository = backupRepository,
        kaliumFileSystem = kaliumFileSystem,
        backupImporterProvider = MPBackupImporterProviderImpl(fileSystem),
        dispatchers = dispatchers,
    )

    private class BackupRepositoryStub(
        private val users: List<OtherUser> = emptyList(),
        private val conversations: List<Conversation> = emptyList(),
        private val messages: List<Message.Standalone> = emptyList(),
        private val reactions: List<MessageReactions> = emptyList(),
    ) : BackupRepository {
        val insertedUsers = mutableListOf<OtherUser>()
        val insertedConversations = mutableListOf<Conversation>()
        val insertedMessages = mutableListOf<Message.Standalone>()
        val insertedReactions = mutableListOf<MessageReactions>()

        override suspend fun getUsers(): List<OtherUser> = users

        override suspend fun getConversations(): List<Conversation> = conversations

        override suspend fun getMessages(pageSize: Int): Flow<PagedData<Message.Standalone>> = flowOf(
            PagedData(messages, totalPages = 1)
        )

        override suspend fun getReactions(pageSize: Int): Flow<PagedData<MessageReactions>> = flowOf(
            PagedData(reactions, totalPages = 1)
        )

        override suspend fun insertUsers(users: List<OtherUser>): Either<CoreFailure, Unit> {
            insertedUsers += users
            return Unit.right()
        }

        override suspend fun insertConversations(conversations: List<Conversation>): Either<CoreFailure, Unit> {
            insertedConversations += conversations
            return Unit.right()
        }

        override suspend fun insertMessages(messages: List<Message.Standalone>): Either<CoreFailure, Unit> {
            insertedMessages += messages
            return Unit.right()
        }

        override suspend fun insertReactions(reactions: List<MessageReactions>): Either<CoreFailure, Unit> {
            insertedReactions += reactions
            return Unit.right()
        }
    }

    private class RealKaliumFileSystem(private val rootDir: Path) : KaliumFileSystem {
        private val fs = FileSystem.SYSTEM

        override val rootCachePath: Path = rootDir / "cache"
        override val rootDBPath: Path = rootDir / "db"

        override fun sink(outputPath: Path, mustCreate: Boolean): Sink = fs.sink(outputPath, mustCreate)
        override fun source(inputPath: Path): Source = fs.source(inputPath)
        override fun createDirectories(dir: Path) = fs.createDirectories(dir)
        override fun createDirectory(dir: Path, mustCreate: Boolean) = fs.createDirectory(dir, mustCreate)
        override fun delete(path: Path, mustExist: Boolean) = fs.delete(path, mustExist)
        override fun deleteContents(dir: Path, mustExist: Boolean) = fs.deleteRecursively(dir, mustExist)
        override fun exists(path: Path): Boolean = fs.exists(path)
        override fun copy(sourcePath: Path, targetPath: Path) = fs.copy(sourcePath, targetPath)

        override fun tempFilePath(pathString: String?): Path {
            val name = pathString ?: "temp_${Random.nextInt().toUInt()}"
            return rootCachePath / name
        }

        override fun providePersistentAssetPath(assetName: String): Path = rootDir / "assets" / assetName
        override fun selfUserAvatarPath(): Path = providePersistentAssetPath("self_avatar.jpg")
        override suspend fun readByteArray(inputPath: Path): ByteArray = fs.source(inputPath).buffer().use { it.readByteArray() }
        override suspend fun writeData(outputSink: Sink, dataSource: Source): Long =
            outputSink.buffer().use { it.writeAll(dataSource) }

        override suspend fun listDirectories(dir: Path): List<Path> = fs.list(dir)
        override fun size(path: Path): Long? = fs.metadata(path).size
    }

    private companion object {
        private val testReaction = MessageReactions(
            messageId = TestMessage.TEXT_MESSAGE.id,
            conversationId = TestConversation.ID,
            reactions = listOf(
                MessageReactionWithUsers(
                    emoji = ":)",
                    users = listOf(TestUser.OTHER.id)
                )
            )
        )
    }
}

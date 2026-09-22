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

import com.wire.backup.data.BackupDateTime
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupMessageContent
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.dump.BackupExportResult
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.backup.PagedData
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.message.reaction.MessageReactionWithUsers
import com.wire.kalium.logic.data.message.reaction.MessageReactions
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.data.user.type.UserTypeInfo
import com.wire.kalium.logic.feature.backup.mapper.toBackupConversation
import com.wire.kalium.logic.feature.backup.mapper.toBackupMessage
import com.wire.kalium.logic.feature.backup.mapper.toBackupReaction
import com.wire.kalium.logic.feature.backup.mapper.toBackupUser
import com.wire.kalium.logic.feature.backup.provider.BackupExporter
import com.wire.kalium.logic.feature.backup.provider.MPBackupExporterProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage.TEXT_MESSAGE
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.verify.VerifyMode
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verifySuspend
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
            .withMessages(listOf(TEXT_MESSAGE))
            .withReactions(listOf(testReaction))
            .arrange()

        val result = useCase("test_password") {}

        assertTrue(result is CreateBackupResult.Success)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.exporter.add(testUser.toBackupUser()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.exporter.add(TestConversation.CONVERSATION.toBackupConversation()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.exporter.add(TEXT_MESSAGE.toBackupMessage()!!) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.exporter.add(testReaction.toBackupReaction()) }
    }

    @Test
    fun givenMixedMentions_whenCreatingBackup_thenOnlyValidMentionsAreExported() = runTest {
        val message = TEXT_MESSAGE.copy(
            content = MessageContent.Text(
                value = "Alice Bob Carol",
                mentions = listOf(
                    MessageMention(0, 5, selfUserId, true),
                    MessageMention(-1, 3, selfUserId, true),
                    MessageMention(6, 0, selfUserId, true),
                    MessageMention(6, -1, selfUserId, true),
                    MessageMention(10, 5, testUser.id, false),
                ),
                quotedMessageReference = MessageContent.QuoteReference("quoted-message", null, true),
            ),
            editStatus = Message.EditStatus.Edited(TEXT_MESSAGE.date),
        )
        val (arrangement, useCase) = Arrangement().withExporter().withMessages(listOf(message)).arrange()
        val expected = expectedBackupMessage(
            message,
            BackupMessageContent.Text(
                text = "Alice Bob Carol",
                mentions = listOf(
                    BackupMessageContent.Text.Mention(BackupQualifiedId("participant1", "domain"), 0, 5),
                    BackupMessageContent.Text.Mention(BackupQualifiedId("participant2", "domain"), 10, 5),
                ),
                quotedMessageId = "quoted-message",
            ),
        ).copy(lastEditTime = BackupDateTime(TEXT_MESSAGE.date.toEpochMilliseconds()))

        val result = useCase("test_password") {}

        assertTrue(result is CreateBackupResult.Success)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.exporter.add(expected) }
    }

    @Test
    fun givenOnlyMalformedMentions_whenCreatingBackup_thenMessageAndFollowingMessageAreExported() = runTest {
        val message = TEXT_MESSAGE.copy(
            content = MessageContent.Text(
                value = "Alice Bob",
                mentions = listOf(
                    MessageMention(-1, 5, selfUserId, true),
                    MessageMention(0, 0, selfUserId, true),
                    MessageMention(6, -1, selfUserId, true),
                ),
            ),
        )
        val followingMessage = TEXT_MESSAGE.copy(id = "following-message", content = MessageContent.Text("Following"))
        val (arrangement, useCase) = Arrangement()
            .withExporter()
            .withMessages(listOf(message, followingMessage))
            .arrange()
        val expected = expectedBackupMessage(message, BackupMessageContent.Text("Alice Bob", mentions = emptyList()))
        val expectedFollowing = expectedBackupMessage(followingMessage, BackupMessageContent.Text("Following"))

        val result = useCase("test_password") {}

        assertTrue(result is CreateBackupResult.Success)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.exporter.add(expected) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.exporter.add(expectedFollowing) }
    }

    private fun expectedBackupMessage(message: Message.Regular, content: BackupMessageContent) = BackupMessage(
        id = message.id,
        conversationId = BackupQualifiedId(message.conversationId.value, message.conversationId.domain),
        senderUserId = BackupQualifiedId(message.senderUserId.value, message.senderUserId.domain),
        senderClientId = message.sender?.id.toString(),
        creationDate = BackupDateTime(message.date.toEpochMilliseconds()),
        content = content,
    )

    @Test
    fun givenZippingFails_whenCreatingBackup_thenErrorIsReturned() = runTest {

        val (_, useCase) = Arrangement()
            .withErrorExporter()
            .withMessages(listOf(TEXT_MESSAGE))
            .arrange()

        val result = useCase("test_password") {}

        assertTrue(result is CreateBackupResult.Failure)
    }

    private inner class Arrangement {

        val userRepository = mock<UserRepository>(mode = MockMode.autoUnit)
        val exporter = mock<BackupExporter>(mode = MockMode.autoUnit)
        val exporterProvider = mock<MPBackupExporterProvider>(mode = MockMode.autoUnit)

        var backupMessages: List<Message.Standalone> = emptyList()
        var backupReactions: List<MessageReactions> = emptyList()

        val backupRepository = object : BackupRepository {
            override suspend fun getUsers(): List<OtherUser> = listOf(testUser)

            override suspend fun getConversations(): List<Conversation> = listOf(TestConversation.CONVERSATION)

            override suspend fun getMessages(pageSize: Int): Flow<PagedData<Message.Standalone>> = flowOf(
                PagedData(
                    data = backupMessages,
                    totalPages = 1,
                )
            )

            override suspend fun getReactions(pageSize: Int): Flow<PagedData<MessageReactions>> = flowOf(
                PagedData(
                    data = backupReactions,
                    totalPages = 1,
                )
            )

            override suspend fun insertUsers(users: List<OtherUser>): Either<CoreFailure, Unit> = Unit.right()

            override suspend fun insertConversations(conversations: List<Conversation>): Either<CoreFailure, Unit> = Unit.right()

            override suspend fun insertMessages(messages: List<Message.Standalone>): Either<CoreFailure, Unit> = Unit.right()

            override suspend fun insertReactions(reactions: List<MessageReactions>): Either<CoreFailure, Unit> = Unit.right()
        }

        fun withMessages(messages: List<Message.Standalone>) = apply {
            backupMessages = messages
        }

        fun withReactions(reactions: List<MessageReactions>) = apply {
            backupReactions = reactions
        }

        suspend fun withExporter() = apply {

            everySuspend { exporter.finalize(any()) } returns (BackupExportResult.Success("testPath/backupFile.zip"))

            every {
                exporterProvider.provideExporter(
                    selfUserId = any(),
                    workDirectory = any(),
                    outputDirectory = any(),
                    fileZipper = any(),
                    logger = any(),
                )
            } returns (exporter)
        }

        suspend fun withErrorExporter() = apply {

            everySuspend { exporter.finalize(any()) } returns (BackupExportResult.Failure.ZipError("Zip failure"))

            every {
                exporterProvider.provideExporter(
                    selfUserId = any(),
                    workDirectory = any(),
                    outputDirectory = any(),
                    fileZipper = any(),
                    logger = any(),
                )
            } returns (exporter)
        }

        suspend fun arrange(): Pair<Arrangement, CreateMPBackupUseCase> {

            everySuspend { userRepository.getSelfUser() } returns (selfUser.right())

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
            userType = UserTypeInfo.Regular(UserType.INTERNAL),
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
            userType = UserTypeInfo.Regular(UserType.INTERNAL),
            botService = null,
            deleted = false,
            defederated = false,
            isProteusVerified = false,
        )

        private val testReaction = MessageReactions(
            messageId = "messageId",
            conversationId = TestConversation.ID,
            reactions = listOf(
                MessageReactionWithUsers(
                    emoji = ":)",
                    users = listOf(testUser.id)
                )
            )
        )
    }
}

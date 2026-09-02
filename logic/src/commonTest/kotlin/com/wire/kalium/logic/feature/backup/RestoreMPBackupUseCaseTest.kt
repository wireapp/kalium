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

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupEmojiReaction
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.data.BackupReaction
import com.wire.backup.data.BackupUser
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.data.user.type.UserTypeInfo
import com.wire.kalium.logic.feature.backup.mapper.toBackupConversation
import com.wire.kalium.logic.feature.backup.mapper.toBackupMessage
import com.wire.kalium.logic.feature.backup.mapper.toBackupUser
import com.wire.kalium.logic.feature.backup.provider.BackupImporter
import com.wire.kalium.logic.feature.backup.provider.ImportDataPagerForMocking
import com.wire.kalium.logic.feature.backup.provider.ImportResult
import com.wire.kalium.logic.feature.backup.provider.ImportResultPagerForMocking
import com.wire.kalium.logic.feature.backup.provider.MPBackupImporterProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.verify.VerifyMode
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.buffer
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreMPBackupUseCaseTest {

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
    fun givenACorrectNonEncryptedBackupFile_whenRestoring_thenTheBackupIsRestoredSuccessfully() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withSuccessImport()
            .withImporterWorkFile()
            .arrange()
        val progress = mutableListOf<Float>()

        useCase(arrangement.storedPath, null, progress::add)

        verifySuspend(VerifyMode.exactly(1)) { arrangement.backupRepository.insertUsers(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.backupRepository.insertConversations(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.backupRepository.insertMessages(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.backupRepository.insertReactions(any()) }
        verify(VerifyMode.exactly(1)) { arrangement.resultPager.close() }
        assertEquals(listOf(0.25f, 0.5f, 0.75f, 1f), progress)
        assertFalse(arrangement.fileSystem.exists(arrangement.importerWorkFile))
    }

    @Test
    fun givenAValidEncryptedBackupFile_whenRestoring_thenTheBackupIsRestoredCorrectly() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withSuccessImport()
            .arrange()

        useCase(arrangement.storedPath, "test_password") {}

        verifySuspend(VerifyMode.exactly(1)) { arrangement.backupRepository.insertUsers(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.backupRepository.insertConversations(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.backupRepository.insertMessages(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.backupRepository.insertReactions(any()) }
    }

    @Test
    fun givenInvalidPassword_whenRestoring_thenTheErrorReturned() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withInvalidPassword()
            .arrange()

        val result = useCase(arrangement.storedPath, "invalid_password") {}

        assertTrue(result is RestoreBackupResult.Failure)
        assertEquals(RestoreBackupResult.BackupRestoreFailure.InvalidPassword, result.failure)
    }

    @Test
    fun givenParsingError_whenRestoring_thenTheErrorReturned() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withParsingFailure()
            .withImporterWorkFile()
            .arrange()

        val result = useCase(arrangement.storedPath, "invalid_password") {}

        assertTrue(result is RestoreBackupResult.Failure)
        assertEquals(RestoreBackupResult.BackupRestoreFailure.BackupIOFailure("Parsing failure"), result.failure)
        assertFalse(arrangement.fileSystem.exists(arrangement.importerWorkFile))
    }

    @Test
    fun givenUnzipError_whenRestoring_thenTheErrorReturned() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withUnzipFailure()
            .arrange()

        val result = useCase(arrangement.storedPath, "invalid_password") {}

        assertTrue(result is RestoreBackupResult.Failure)
        assertEquals(RestoreBackupResult.BackupRestoreFailure.BackupIOFailure("Unzipping error"), result.failure)
    }

    @Test
    fun givenOtherError_whenRestoring_thenTheErrorReturned() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withOtherFailure()
            .arrange()

        val result = useCase(arrangement.storedPath, "invalid_password") {}

        assertTrue(result is RestoreBackupResult.Failure)
        assertEquals(RestoreBackupResult.BackupRestoreFailure.BackupIOFailure("Unknown error"), result.failure)
    }

    @Test
    fun givenPersistenceFailure_whenRestoring_thenFailureIsReturnedAndRemainingDataIsNotPersisted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSuccessImport()
            .withUsersInsertFailure()
            .arrange()
        val progress = mutableListOf<Float>()

        val result = useCase(arrangement.storedPath, null, progress::add)

        assertEquals(
            RestoreBackupResult.Failure(
                RestoreBackupResult.BackupRestoreFailure.BackupIOFailure("Failed to persist backup data")
            ),
            result,
        )
        verifySuspend(VerifyMode.exactly(1)) { arrangement.backupRepository.insertUsers(any()) }
        verifySuspend(VerifyMode.exactly(0)) { arrangement.backupRepository.insertConversations(any()) }
        verifySuspend(VerifyMode.exactly(0)) { arrangement.backupRepository.insertMessages(any()) }
        verifySuspend(VerifyMode.exactly(0)) { arrangement.backupRepository.insertReactions(any()) }
        assertTrue(progress.isEmpty())
    }

    @Test
    fun givenCancellationDuringPagePersistence_whenPageFinishes_thenNoNextPageIsPersisted() = runTest {
        val pageStarted = CompletableDeferred<Unit>()
        val finishPage = CompletableDeferred<Unit>()
        var insertedPageCount = 0
        val (arrangement, useCase) = Arrangement()
            .withSuccessImport()
            .withImporterWorkFile()
            .withUsersPages(
                arrayOf(testUser.toBackupUser()),
                arrayOf(testUser.toBackupUser()),
            )
            .withUsersInsertAction {
                insertedPageCount++
                pageStarted.complete(Unit)
                finishPage.await()
            }
            .arrange()

        val restoreJob = launch {
            useCase(arrangement.storedPath, null) {}
        }
        pageStarted.await()

        restoreJob.cancel()
        assertFalse(restoreJob.isCompleted)
        finishPage.complete(Unit)
        restoreJob.join()

        assertTrue(restoreJob.isCancelled)
        assertEquals(1, insertedPageCount)
        verify(VerifyMode.exactly(1)) { arrangement.usersPager.nextPage() }
        verify(VerifyMode.exactly(1)) { arrangement.resultPager.close() }
        verifySuspend(VerifyMode.exactly(0)) { arrangement.backupRepository.insertConversations(any()) }
        assertFalse(arrangement.fileSystem.exists(arrangement.importerWorkFile))
    }

    @Test
    fun givenCancellationDuringFinalPagePersistence_whenPageFinishes_thenCancellationWinsOverSuccess() = runTest {
        val pageStarted = CompletableDeferred<Unit>()
        val finishPage = CompletableDeferred<Unit>()
        val progress = mutableListOf<Float>()
        val (arrangement, useCase) = Arrangement()
            .withSuccessImport()
            .withReactionsInsertAction {
                pageStarted.complete(Unit)
                finishPage.await()
            }
            .arrange()

        val restoreJob = launch {
            useCase(arrangement.storedPath, null, progress::add)
        }
        pageStarted.await()

        restoreJob.cancel()
        finishPage.complete(Unit)
        restoreJob.join()

        assertTrue(restoreJob.isCancelled)
        assertEquals(listOf(0.25f, 0.5f, 0.75f), progress)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.backupRepository.insertReactions(any()) }
    }

    @Test
    fun givenCancellationDuringConversationPersistence_whenPageFinishes_thenMessagesDoNotStart() = runTest {
        val pageStarted = CompletableDeferred<Unit>()
        val finishPage = CompletableDeferred<Unit>()
        val (arrangement, useCase) = Arrangement()
            .withSuccessImport()
            .withConversationsInsertAction {
                pageStarted.complete(Unit)
                finishPage.await()
            }
            .arrange()

        val restoreJob = launch { useCase(arrangement.storedPath, null) {} }
        pageStarted.await()

        restoreJob.cancel()
        finishPage.complete(Unit)
        restoreJob.join()

        assertTrue(restoreJob.isCancelled)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.backupRepository.insertConversations(any()) }
        verifySuspend(VerifyMode.exactly(0)) { arrangement.backupRepository.insertMessages(any()) }
    }

    @Test
    fun givenCancellationDuringMessagePersistence_whenPageFinishes_thenReactionsDoNotStart() = runTest {
        val pageStarted = CompletableDeferred<Unit>()
        val finishPage = CompletableDeferred<Unit>()
        val (arrangement, useCase) = Arrangement()
            .withSuccessImport()
            .withMessagesInsertAction {
                pageStarted.complete(Unit)
                finishPage.await()
            }
            .arrange()

        val restoreJob = launch { useCase(arrangement.storedPath, null) {} }
        pageStarted.await()

        restoreJob.cancel()
        finishPage.complete(Unit)
        restoreJob.join()

        assertTrue(restoreJob.isCancelled)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.backupRepository.insertMessages(any()) }
        verifySuspend(VerifyMode.exactly(0)) { arrangement.backupRepository.insertReactions(any()) }
    }

    @Test
    fun givenBackupContainsUsersWithEmptyDomain_whenRestoring_thenMalformedUserIsSkipped() = runTest {
        val malformedBackupUser = testUser.toBackupUser().copy(
            id = BackupQualifiedId("participant-malformed", "")
        )
        val capturedInsertedUsers = mutableListOf<List<OtherUser>>()
        val capturedInsertedConversations = mutableListOf<List<Conversation>>()
        val capturedInsertedMessages = mutableListOf<List<Message.Standalone>>()

        val (arrangement, useCase) = Arrangement()
            .withSuccessImport()
            .withUsersPages(arrayOf(malformedBackupUser, testUser.toBackupUser()))
            .captureInsertedUsers(capturedInsertedUsers)
            .captureInsertedConversations(capturedInsertedConversations)
            .captureInsertedMessages(capturedInsertedMessages)
            .arrange()

        val result = useCase(arrangement.storedPath, null) {}

        // Restore should complete even if one backup user has malformed qualified ID (empty domain).
        assertTrue(result is RestoreBackupResult.Success)

        // Only the valid user should be inserted; malformed one must be skipped.
        assertEquals(1, capturedInsertedUsers.size)
        val insertedUsers = capturedInsertedUsers.first()
        assertEquals(1, insertedUsers.size)
        val insertedUser = insertedUsers.firstOrNull()
        assertNotNull(insertedUser)
        assertEquals("domain", insertedUser.id.domain)

        // Conversations should still be restored correctly.
        assertEquals(1, capturedInsertedConversations.size)
        val insertedConversations = capturedInsertedConversations.first()
        assertEquals(1, insertedConversations.size)
        val insertedConversation = insertedConversations.firstOrNull()
        assertNotNull(insertedConversation)
        assertTrue(insertedConversation.id.domain.isNotBlank())

        // Messages should still be restored correctly and keep non-empty qualified ID domains.
        assertEquals(1, capturedInsertedMessages.size)
        val insertedMessages = capturedInsertedMessages.first()
        assertEquals(1, insertedMessages.size)
        val insertedMessage = insertedMessages.firstOrNull()
        assertNotNull(insertedMessage)
        assertTrue(insertedMessage.senderUserId.domain.isNotBlank())
        assertTrue(insertedMessage.conversationId.domain.isNotBlank())
    }

    private inner class Arrangement {

        val backupRepository = mock<BackupRepository>(mode = MockMode.autoUnit)
        val importerProvider = mock<MPBackupImporterProvider>(mode = MockMode.autoUnit)
        val resultPager = mock<ImportResultPagerForMocking>(mode = MockMode.autoUnit)
        val usersPager = mock<ImportDataPagerForMocking<BackupUser>>(mode = MockMode.autoUnit)
        val conversationsPager = mock<ImportDataPagerForMocking<BackupConversation>>(mode = MockMode.autoUnit)
        val messagesPager = mock<ImportDataPagerForMocking<BackupMessage>>(mode = MockMode.autoUnit)
        val reactionsPager = mock<ImportDataPagerForMocking<BackupReaction>>(mode = MockMode.autoUnit)
        val importer = mock<BackupImporter>(mode = MockMode.autoUnit)
        val fileSystem = FakeKaliumFileSystem()
        var usersPages: List<Array<BackupUser>> = listOf(arrayOf(testUser.toBackupUser()))
        var usersInsertStubConfigured = false
        var usersInsertFailure: CoreFailure? = null
        var usersInsertAction: suspend () -> Unit = {}
        var conversationsInsertStubConfigured = false
        var conversationsInsertAction: suspend () -> Unit = {}
        var messagesInsertStubConfigured = false
        var messagesInsertAction: suspend () -> Unit = {}
        var reactionsInsertAction: suspend () -> Unit = {}
        var importerAction: suspend () -> Unit = {}

        val storedPath = "testPath/backupFile.zip".toPath()
        val importerWorkDir = fileSystem.tempFilePath("${storedPath.name}-restore-workdir")
        val importerWorkFile = "$importerWorkDir/importer.tmp".toPath()

        suspend fun withSuccessImport() = apply {
            everySuspend { importer.importFromFile(any(), any()) } calls {
                importerAction()
                ImportResult.Success(resultPager)
            }
        }

        suspend fun withInvalidPassword() = apply {
            everySuspend { importer.importFromFile(any(), any()) } returns (
                ImportResult.Failure.MissingOrWrongPassphrase
            )
        }

        suspend fun withParsingFailure() = apply {
            everySuspend { importer.importFromFile(any(), any()) } calls {
                importerAction()
                ImportResult.Failure.ParsingFailure
            }
        }

        suspend fun withUnzipFailure() = apply {
            everySuspend { importer.importFromFile(any(), any()) } returns (
                ImportResult.Failure.UnzippingError("Unzipping error")
            )
        }


        suspend fun withOtherFailure() = apply {
            everySuspend { importer.importFromFile(any(), any()) } returns (
                ImportResult.Failure.UnknownError("Unknown error")
            )
        }

        fun withUsersPages(vararg pages: Array<BackupUser>) = apply {
            usersPages = pages.toList()
        }

        fun withImporterWorkFile() = apply {
            importerAction = {
                fileSystem.createDirectories(importerWorkDir)
                fileSystem.sink(importerWorkFile).buffer().use { it.writeUtf8("restore work") }
            }
        }

        suspend fun captureInsertedUsers(captured: MutableList<List<OtherUser>>) = apply {
            usersInsertStubConfigured = true
            everySuspend { backupRepository.insertUsers(any()) } calls { invocation ->
                @Suppress("UNCHECKED_CAST")
                val insertedUsers = invocation.args[0] as List<OtherUser>
                captured.add(insertedUsers)
                Unit.right()
            }
        }

        fun withUsersInsertFailure() = apply {
            usersInsertFailure = CoreFailure.Unknown(IllegalStateException("persistence failure"))
        }

        fun withUsersInsertAction(action: suspend () -> Unit) = apply {
            usersInsertAction = action
        }

        fun withReactionsInsertAction(action: suspend () -> Unit) = apply {
            reactionsInsertAction = action
        }

        fun withConversationsInsertAction(action: suspend () -> Unit) = apply {
            conversationsInsertAction = action
        }

        fun withMessagesInsertAction(action: suspend () -> Unit) = apply {
            messagesInsertAction = action
        }

        suspend fun captureInsertedConversations(captured: MutableList<List<Conversation>>) = apply {
            conversationsInsertStubConfigured = true
            everySuspend { backupRepository.insertConversations(any()) } calls { invocation ->
                @Suppress("UNCHECKED_CAST")
                val insertedConversations = invocation.args[0] as List<Conversation>
                captured.add(insertedConversations)
                Unit.right()
            }
        }

        suspend fun captureInsertedMessages(captured: MutableList<List<Message.Standalone>>) = apply {
            messagesInsertStubConfigured = true
            everySuspend { backupRepository.insertMessages(any()) } calls { invocation ->
                @Suppress("UNCHECKED_CAST")
                val insertedMessages = invocation.args[0] as List<Message.Standalone>
                captured.add(insertedMessages)
                Unit.right()
            }
        }

        suspend fun arrange(): Pair<Arrangement, RestoreMPBackupUseCase> {

            every { importerProvider.providePeekImporter() } returns (importer)
            every { importerProvider.provideImporter(any(), any()) } returns (importer)

            if (!usersInsertStubConfigured) {
                everySuspend { backupRepository.insertUsers(any()) } calls {
                    usersInsertAction()
                    usersInsertFailure?.left() ?: Unit.right()
                }
            }
            if (!conversationsInsertStubConfigured) {
                everySuspend { backupRepository.insertConversations(any()) } calls {
                    conversationsInsertAction()
                    Unit.right()
                }
            }
            if (!messagesInsertStubConfigured) {
                everySuspend { backupRepository.insertMessages(any()) } calls {
                    messagesInsertAction()
                    Unit.right()
                }
            }
            everySuspend { backupRepository.insertReactions(any()) } calls {
                reactionsInsertAction()
                Unit.right()
            }

            val usersHasMorePagesValues = MutableList(usersPages.size) { true } + false
            var usersHasMorePagesIndex = 0
            var usersPageIndex = 0
            every { usersPager.hasMorePages() } calls { usersHasMorePagesValues[usersHasMorePagesIndex++] }
            every { usersPager.nextPage() } calls { usersPages[usersPageIndex++] }

            var conversationsHasMorePagesIndex = 0
            every { conversationsPager.hasMorePages() } calls { conversationsHasMorePagesIndex++ == 0 }
            every { conversationsPager.nextPage() } returns (arrayOf(TestConversation.CONVERSATION.toBackupConversation()))

            var messagesHasMorePagesIndex = 0
            every { messagesPager.hasMorePages() } calls { messagesHasMorePagesIndex++ == 0 }
            every { messagesPager.nextPage() } returns (arrayOf(TestMessage.TEXT_MESSAGE.toBackupMessage()!!))

            var reactionsHasMorePagesIndex = 0
            every { reactionsPager.hasMorePages() } calls { reactionsHasMorePagesIndex++ == 0 }
            every { reactionsPager.nextPage() } returns (arrayOf(testReaction))

            every { resultPager.usersPager } returns (usersPager)
            every { resultPager.conversationsPager } returns (conversationsPager)
            every { resultPager.messagesPager } returns (messagesPager)
            every { resultPager.reactionsPager } returns (reactionsPager)
            every { resultPager.totalPagesCount } returns (usersPages.size + 3)

            return this to RestoreMPBackupUseCaseImpl(
                selfUserId = selfUserId,
                backupRepository = backupRepository,
                kaliumFileSystem = fileSystem,
                backupImporterProvider = importerProvider,
                dispatchers = dispatchers
            )
        }
    }

    private companion object {
        private val selfUserId = QualifiedID("participant1", "domain")

        private val testUser = OtherUser(
            id = QualifiedID("participant2", "domain"),
            name = "",
            handle = "test_user",
            email = null,
            phone = null,
            accentId = 0,
            teamId = null,
            connectionStatus = ConnectionState.NOT_CONNECTED,
            previewPicture = null,
            completePicture = null,
            availabilityStatus = UserAvailabilityStatus.NONE,
            expiresAt = null,
            supportedProtocols = null,
            userType = UserTypeInfo.Regular(UserType.NONE),
            botService = null,
            deleted = false,
            defederated = false,
            isProteusVerified = false,
        )

        private val testReaction = BackupReaction(
            messageId = "messageId",
            conversationId = BackupQualifiedId("conversationId", "domain"),
            emojiReactions = listOf(
                BackupEmojiReaction(
                    emoji = ":)",
                    users = listOf(BackupQualifiedId("participant2", "domain"))
                )
            ),
        )
    }
}

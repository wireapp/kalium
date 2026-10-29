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

package com.wire.kalium.logic.feature.auth

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.data.BackupReaction
import com.wire.backup.data.BackupUser
import com.wire.backup.dump.BackupExportResult
import com.wire.backup.dump.FileZipper
import com.wire.backup.logger.BackupLogger
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.asset.AssetsStorageFolder
import com.wire.kalium.logic.data.asset.CacheFolder
import com.wire.kalium.logic.data.asset.DBFolder
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.backup.PagedData
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.reaction.MessageReactions
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.data.user.type.UserTypeInfo
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.backup.CreateBackupResult
import com.wire.kalium.logic.feature.backup.CreateMPBackupUseCaseImpl
import com.wire.kalium.logic.feature.backup.provider.BackupExporter
import com.wire.kalium.logic.feature.backup.provider.MPBackupExporterProvider
import com.wire.kalium.logic.feature.call.usecase.EndCallUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveEstablishedCallsUseCase
import com.wire.kalium.logic.feature.client.ClearClientDataUseCase
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.userstorage.di.UserStorage
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tickets 01 and 03 together: a real [LogoutUseCaseImpl] wired to a real [ClearUserDataUseCaseImpl],
 * with files that the app leaves on disk, including a backup archive made by the real [CreateMPBackupUseCaseImpl].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogoutWipesUserFilesTest {

    private val databaseFolder = Files.createTempDirectory("logout-wipes-files-").toFile()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(TestKaliumDispatcher.default)
    }

    @AfterTest
    fun cleanUp() {
        Dispatchers.resetMain()
        databaseFolder.deleteRecursively()
    }

    @Test
    fun givenUserFilesAndMpBackupArchive_whenHardLogout_thenDatabaseAssetFolderAndCacheFolderAreDeleted() = runTest {
        val arrangement = Arrangement().withUserFilesAndMpBackupArchive()
        arrangement.assertAllFilesExist()

        arrangement.logout(LogoutReason.SELF_HARD_LOGOUT)

        assertNull(arrangement.userDatabase.dbFileLocation())
        arrangement.allFiles.forEach { assertFalse(arrangement.fakeFileSystem.exists(it), "$it still exists") }
        assertFalse(arrangement.fakeFileSystem.exists(arrangement.workDir))
    }

    @Test
    fun givenUserFilesAndMpBackupArchive_whenRemovedClientAndWipeOnDeviceRemoval_thenAllFilesAreDeleted() = runTest {
        val arrangement = Arrangement()
            .withKaliumConfigs(KaliumConfigs(wipeOnDeviceRemoval = true))
            .withUserFilesAndMpBackupArchive()

        arrangement.logout(LogoutReason.REMOVED_CLIENT)

        arrangement.allFiles.forEach { assertFalse(arrangement.fakeFileSystem.exists(it), "$it still exists") }
    }

    @Test
    fun givenUserFilesAndMpBackupArchive_whenSessionExpiredAndWipeOnCookieInvalid_thenAllFilesAreDeleted() = runTest {
        val arrangement = Arrangement()
            .withKaliumConfigs(KaliumConfigs(wipeOnCookieInvalid = true))
            .withUserFilesAndMpBackupArchive()

        arrangement.logout(LogoutReason.SESSION_EXPIRED)

        arrangement.allFiles.forEach { assertFalse(arrangement.fakeFileSystem.exists(it), "$it still exists") }
    }

    @Test
    fun givenUserFilesAndMpBackupArchive_whenSoftLogout_thenDatabaseAndAllFilesAreKept() = runTest {
        val arrangement = Arrangement().withUserFilesAndMpBackupArchive()

        arrangement.logout(LogoutReason.SELF_SOFT_LOGOUT)

        assertNotNull(arrangement.userDatabase.dbFileLocation())
        arrangement.assertAllFilesExist()
    }

    private inner class Arrangement {
        val userDatabase = userDatabaseBuilder(
            PlatformDatabaseData(StorageData.FileBacked(databaseFolder)),
            UserIDEntity(USER_ID.value, USER_ID.domain), null, Dispatchers.IO
        )
        val fakeFileSystem = FakeFileSystem()
        val kaliumFileSystem = FakeKaliumFileSystem(fakeFileSystem)
        private val assetFolder = kaliumFileSystem.providePersistentAssetPath("placeholder").parent!!
        private val cacheFolder = kaliumFileSystem.rootCachePath
        private var kaliumConfigs = KaliumConfigs()
        lateinit var workDir: Path
        lateinit var archive: Path
        var allFiles: List<Path> = emptyList()

        private val sessionScope = CoroutineScope(SupervisorJob())
        private val globalTestScope = TestScope()
        private val logoutRepository = mock<LogoutRepository>(mode = MockMode.autoUnit)
        private val sessionRepository = mock<SessionRepository>(mode = MockMode.autoUnit)
        private val clientRepository = mock<ClientRepository>(mode = MockMode.autoUnit)
        private val userConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)
        private val deregisterTokenUseCase = mock<DeregisterTokenUseCase>(mode = MockMode.autoUnit)
        private val clearClientDataUseCase = mock<ClearClientDataUseCase>(mode = MockMode.autoUnit)
        private val userSessionScopeProvider = mock<UserSessionScopeProvider>(mode = MockMode.autoUnit)
        private val pushTokenRepository = mock<PushTokenRepository>(mode = MockMode.autoUnit)
        private val userSessionWorkScheduler = mock<UserSessionWorkScheduler>(mode = MockMode.autoUnit)
        private val observeEstablishedCallsUseCase = mock<ObserveEstablishedCallsUseCase>(mode = MockMode.autoUnit)
        private val endCall = mock<EndCallUseCase>(mode = MockMode.autoUnit)
        private val logoutCallback = mock<LogoutCallback>(mode = MockMode.autoUnit)

        init {
            assertNotNull(userDatabase.dbFileLocation())
        }

        fun withKaliumConfigs(configs: KaliumConfigs) = apply { kaliumConfigs = configs }

        suspend fun withUserFilesAndMpBackupArchive() = apply {
            val files = listOf(
                assetFolder / "attachment.pdf",
                assetFolder / "proteus" / "keystore",
                assetFolder / "mls" / "keystore",
                cacheFolder / "image_attachment.jpg",
            )
            files.forEach { write(it) }
            val result = createMpBackup()
            workDir = result.backupFilePath.parent!!
            archive = result.backupFilePath
            allFiles = files + archive
        }

        fun assertAllFilesExist() = allFiles.forEach { assertTrue(fakeFileSystem.exists(it), "$it is missing") }

        suspend fun logout(reason: LogoutReason) {
            val useCase = arrangeLogout()
            useCase(reason, waitUntilCompletes = false)
            globalTestScope.advanceUntilIdle()
        }

        private fun write(path: Path) {
            fakeFileSystem.createDirectories(path.parent!!)
            fakeFileSystem.write(path) { writeUtf8("content") }
        }

        private suspend fun createMpBackup(): CreateBackupResult.Success {
            val exporterProvider = object : MPBackupExporterProvider {
                override fun provideExporter(
                    selfUserId: BackupQualifiedId,
                    workDirectory: String,
                    outputDirectory: String,
                    fileZipper: FileZipper,
                    logger: BackupLogger?
                ): BackupExporter = object : BackupExporter {
                    override fun add(user: BackupUser) = Unit
                    override fun add(conversation: BackupConversation) = Unit
                    override fun add(message: BackupMessage) = Unit
                    override fun add(reaction: BackupReaction) = Unit
                    override suspend fun finalize(password: String): BackupExportResult {
                        val workPath = workDirectory.toPath()
                        val entry = workPath / "export.data"
                        write(entry)
                        return BackupExportResult.Success(fileZipper.zip(listOf(entry.toString()), workPath))
                    }
                }
            }
            val result = CreateMPBackupUseCaseImpl(
                backupRepository = emptyBackupRepository,
                userRepository = mock<UserRepository>(mode = MockMode.autoUnit).also {
                    everySuspend { it.getSelfUser() } returns selfUser.right()
                },
                kaliumFileSystem = kaliumFileSystem,
                fileSystem = fakeFileSystem,
                exporterProvider = exporterProvider,
                dispatchers = TestKaliumDispatcher
            ).invoke("") {}
            return assertTrue(result is CreateBackupResult.Success, "backup failed: $result").let { result as CreateBackupResult.Success }
        }

        private suspend fun arrangeLogout(): LogoutUseCaseImpl {
            everySuspend { logoutRepository.logout() } returns Either.Right(Unit)
            everySuspend { sessionRepository.logout(any(), any()) } returns Either.Right(Unit)
            everySuspend { deregisterTokenUseCase.invoke() } returns DeregisterTokenUseCase.Result.Success
            everySuspend { clientRepository.clearCurrentClientId() } returns Either.Right(Unit)
            everySuspend { clientRepository.clearRetainedClientId() } returns Either.Right(Unit)
            everySuspend { clientRepository.clearHasRegisteredMLSClient() } returns Either.Right(Unit)
            everySuspend { pushTokenRepository.setUpdateFirebaseTokenFlag(any()) } returns Either.Right(Unit)
            everySuspend { observeEstablishedCallsUseCase.invoke() } returns flowOf(emptyList())
            everySuspend { userConfigRepository.clearE2EISettings() } returns Unit
            everySuspend { userSessionWorkScheduler.cancelScheduledSendingOfPendingMessages() } returns Unit
            everySuspend { logoutRepository.onLogout(any()) } returns Unit
            everySuspend { logoutCallback(any(), any()) } returns Unit
            everySuspend { userSessionScopeProvider.delete(any()) } returns Unit
            everySuspend { userSessionScopeProvider.logout(any(), any(), any(), any()) } calls { invocation ->
                @Suppress("UNCHECKED_CAST")
                val prepare = invocation.args[2] as suspend () -> Unit
                @Suppress("UNCHECKED_CAST")
                val clear = invocation.args[3] as suspend () -> Unit
                prepare()
                clear()
                userSessionScopeProvider.delete(USER_ID)
                true
            }
            val clearUserData = ClearUserDataUseCaseImpl(
                userStorage = UserStorage(userDatabase),
                dataStoragePaths = DataStoragePaths(
                    AssetsStorageFolder(assetFolder.toString()),
                    CacheFolder(cacheFolder.toString()),
                    DBFolder(kaliumFileSystem.rootDBPath.toString())
                ),
                kaliumFileSystem = kaliumFileSystem
            )
            return LogoutUseCaseImpl(
                logoutRepository = logoutRepository,
                sessionScope = sessionScope,
                sessionRepository = sessionRepository,
                clientRepository = clientRepository,
                userConfigRepository = userConfigRepository,
                userId = USER_ID,
                deregisterTokenUseCase = deregisterTokenUseCase,
                clearClientDataUseCase = clearClientDataUseCase,
                clearUserDataUseCase = clearUserData,
                userSessionScopeProvider = userSessionScopeProvider,
                pushTokenRepository = pushTokenRepository,
                globalCoroutineScope = globalTestScope,
                userSessionWorkScheduler = userSessionWorkScheduler,
                getEstablishedCallsUseCase = observeEstablishedCallsUseCase,
                endCallUseCase = endCall,
                logoutCallback = logoutCallback,
                kaliumConfigs = kaliumConfigs
            )
        }
    }

    private val emptyBackupRepository = object : BackupRepository {
        override suspend fun getUsers(): List<OtherUser> = emptyList()
        override suspend fun getConversations(): List<Conversation> = emptyList()
        override suspend fun getMessages(pageSize: Int): Flow<PagedData<Message.Standalone>> = flowOf(PagedData(emptyList(), 1))
        override suspend fun getReactions(pageSize: Int): Flow<PagedData<MessageReactions>> = flowOf(PagedData(emptyList(), 1))
        override suspend fun insertUsers(users: List<OtherUser>): Either<CoreFailure, Unit> = Unit.right()
        override suspend fun insertConversations(conversations: List<Conversation>): Either<CoreFailure, Unit> = Unit.right()
        override suspend fun insertMessages(messages: List<Message.Standalone>): Either<CoreFailure, Unit> = Unit.right()
        override suspend fun insertReactions(reactions: List<MessageReactions>): Either<CoreFailure, Unit> = Unit.right()
    }

    private companion object {
        val USER_ID = QualifiedID("userId", "domain")
        val selfUser = SelfUser(
            id = USER_ID,
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
    }
}

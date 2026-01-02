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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.MlsDBSecret
import com.wire.kalium.cryptography.ProteusDBSecret
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.remoteBackup.BackupCryptoStateUseCase
import com.wire.kalium.logic.sync.remoteBackup.BackupCryptoStateUseCaseImpl
import com.wire.kalium.logic.util.IgnoreIOS
import com.wire.kalium.logic.util.SecurityHelper
import com.wire.kalium.network.api.base.authenticated.backup.MessageSyncApi
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertIs

@IgnoreIOS
class BackupCryptoStateUseCaseTest {

    @Test
    fun givenFeatureFlagDisabled_whenBackupInvoked_thenBackupIsSkipped() = runTest {
        // Given
        val (arrangement, backupCryptoStateUseCase) = Arrangement()
            .withMessageSyncEnabled(false)
            .withClientId(ClientId("test-client-id"))
            .withDefaultMocks()
            .withDefaultPassphrases()
            .arrange()

        // When
        val result = backupCryptoStateUseCase()

        // Then
        assertIs<Either.Left<CoreFailure>>(result)

        coVerify {
            arrangement.messageSyncApi.uploadStateBackup(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenNoClientRegistered_whenBackupInvoked_thenBackupIsSkipped() = runTest {
        // Given
        val (arrangement, backupCryptoStateUseCase) = Arrangement()
            .withMessageSyncEnabled(true)
            .withClientId(null)
            .withDefaultMocks()
            .withDefaultPassphrases()
            .arrange()

        // When
        val result = backupCryptoStateUseCase()

        // Then
        assertIs<Either.Left<CoreFailure>>(result)

        coVerify {
            arrangement.messageSyncApi.uploadStateBackup(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenEmptyCryptoFolders_whenBackupInvoked_thenBackupIsSkipped() = runTest {
        // Given
        val (arrangement, backupCryptoStateUseCase) = Arrangement()
            .withMessageSyncEnabled(true)
            .withClientId(ClientId("test-client-id"))
            .withDefaultMocks()
            .withDefaultPassphrases()
            .withCryptoFolders(hasProteusContent = false, hasMlsContent = false)
            .arrange()

        // When
        val result = backupCryptoStateUseCase()

        // Then
        assertIs<Either.Left<CoreFailure>>(result)

        coVerify {
            arrangement.messageSyncApi.uploadStateBackup(any(), any(), any())
        }.wasNotInvoked()
    }


    private class Arrangement {
        val userId = UserId("test-user", "test-domain")
        val fakeFileSystem = FakeKaliumFileSystem()
        val messageSyncApi = mock(MessageSyncApi::class)
        val clientIdProvider = mock(CurrentClientIdProvider::class)
        val rootPathsProvider = mock(RootPathsProvider::class)
        val securityHelper = mock(SecurityHelper::class)
        val eventRepository = mock(EventRepository::class)
        val mlsClientProvider = mock(MLSClientProvider::class)
        val coreCrypto = mock(CoreCryptoCentral::class)
        var kaliumConfigs = KaliumConfigs()

        val proteusPath: Path = fakeFileSystem.rootCachePath / "proteus"
        val mlsPath: Path = fakeFileSystem.rootCachePath / "mls"

        init {
            // Setup default paths
            every { rootPathsProvider.rootProteusPath(userId) }.returns(proteusPath.toString())
            every { rootPathsProvider.rootMLSPath(userId) }.returns(mlsPath.toString())
        }

        suspend fun withDefaultMocks() = apply {
            // Setup default event repository behavior
            coEvery { eventRepository.lastSavedEventId() }.returns(Either.Right(""))

            // Setup default mlsClientProvider behavior
            coEvery { mlsClientProvider.getCoreCrypto(any()) }.returns(Either.Right(coreCrypto))

            // Setup default exportDatabaseCopy behavior
            coEvery { coreCrypto.exportDatabaseCopy(any()) }.invokes { path ->
                // Create a dummy database export file
                fakeFileSystem.sink((path as String).toPath()).buffer().use {
                    it.writeUtf8("exported crypto database")
                }
            }
        }

        suspend fun withDefaultPassphrases() = apply {
            // Setup default passphrase responses
            coEvery { securityHelper.mlsDBSecret(any(), any()) }
                .returns(MlsDBSecret(ByteArray(32) { it.toByte() }))
            coEvery { securityHelper.proteusDBSecret(any(), any()) }
                .returns(ProteusDBSecret(ByteArray(32) { it.toByte() }))
        }

        fun withMessageSyncEnabled(enabled: Boolean) = apply {
            kaliumConfigs = kaliumConfigs.copy(
                messageSynchronizationEnabledFlag = enabled,
                remoteBackupURL = "https://example.com"
            )
        }

        suspend fun withClientId(clientId: ClientId?) = apply {
            coEvery { clientIdProvider.invoke() }.returns(
                clientId?.let { Either.Right(it) }
                    ?: Either.Left(CoreFailure.MissingClientRegistration)
            )
        }

        fun withCryptoFolders(hasProteusContent: Boolean, hasMlsContent: Boolean) = apply {
            if (hasProteusContent) {
                fakeFileSystem.createDirectories(proteusPath)
                fakeFileSystem.sink(proteusPath / "proteus-db.db").buffer().use {
                    it.writeUtf8("dummy proteus content")
                }
            }
            if (hasMlsContent) {
                fakeFileSystem.createDirectories(mlsPath)
                fakeFileSystem.sink(mlsPath / "mls-keystore.db").buffer().use {
                    it.writeUtf8("dummy mls content")
                }
            }
        }

        fun arrange(): Pair<Arrangement, BackupCryptoStateUseCase> =
            this to BackupCryptoStateUseCaseImpl(
                selfUserId = userId,
                currentClientIdProvider = clientIdProvider,
                messageSyncApi = messageSyncApi,
                rootPathsProvider = rootPathsProvider,
                kaliumFileSystem = fakeFileSystem,
                kaliumConfigs = kaliumConfigs,
                securityHelper = securityHelper,
                eventRepository = eventRepository,
                mlsClientProvider = mlsClientProvider
            )
    }
}

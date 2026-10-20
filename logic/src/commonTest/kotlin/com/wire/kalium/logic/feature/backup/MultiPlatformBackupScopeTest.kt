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

import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.conversation.RefreshConversationsWithoutMetadataUseCase
import dev.mokkery.answering.calls
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okio.IOException
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MultiPlatformBackupScopeTest {

    @Test
    fun givenDispatcherOverload_whenUsingLegacyNamedArguments_thenThePropertyUseCaseStillRuns() = runTest {
        val arrangement = Arrangement(backgroundScope)
        var progressCalled = false

        // This is the existing GUI source form: same-named function must not shadow property.invoke.
        val result = arrangement.scope.restore(
            backupFilePath = "legacy.wbu".toPath(),
            password = null,
            onProgress = { progressCalled = true },
        )

        arrangement.assertRestoreReachedStorageAndCleanedUp(result)
        assertFalse(progressCalled)
    }

    @Test
    fun givenDispatcherOverload_whenUsingExplicitDispatcher_thenTheConfiguredUseCaseRuns() = runTest {
        val arrangement = Arrangement(backgroundScope)
        var progressCalled = false

        val result = arrangement.scope.restore(progressDispatcher = Dispatchers.Default)(
            backupFilePath = "explicit.wbu".toPath(),
            password = null,
            onProgress = { progressCalled = true },
        )

        arrangement.assertRestoreReachedStorageAndCleanedUp(result)
        assertFalse(progressCalled)
    }

    private class Arrangement(originatingScope: CoroutineScope) {
        private val fileSystem = mock<KaliumFileSystem>()
        private val workDirectory = "source-compatibility-restore-workdir".toPath()
        val scope = MultiPlatformBackupScope(
            createGraph<MultiPlatformBackupCompatibilityGraph>().factory.create(
                object : LegacyMultiPlatformBackupDependencies {
                    override val selfUserId = QualifiedID("self", "example.com")
                    override val kaliumFileSystem = fileSystem
                    override val backupRepository = mock<BackupRepository>()
                    override val userRepository = mock<UserRepository>()
                    override val refreshConversationsWithoutMetadata = mock<RefreshConversationsWithoutMetadataUseCase>()
                    override val userSessionScope: CoroutineScope = originatingScope
                    override val waitUntilSyncLive: suspend () -> Unit = {}
                },
            ),
        )

        init {
            var clearAttempts = 0
            every { fileSystem.tempFilePath(any()) } returns workDirectory
            every { fileSystem.deleteContents(workDirectory, any()) } calls {
                clearAttempts++
                if (clearAttempts == 1) {
                    // Abort before constructing the real importer or touching any filesystem.
                    throw IOException("source compatibility storage failure")
                }
                Unit
            }
        }

        fun assertRestoreReachedStorageAndCleanedUp(result: RestoreBackupResult) {
            assertEquals(
                RestoreBackupResult.Failure(
                    RestoreBackupResult.BackupRestoreFailure.BackupIOFailure(
                        "IO error: source compatibility storage failure",
                    ),
                ),
                result,
            )
            verify(VerifyMode.exactly(2)) { fileSystem.deleteContents(workDirectory, any()) }
        }
    }
}

@DependencyGraph
internal interface MultiPlatformBackupCompatibilityGraph {
    val factory: LegacyMultiPlatformBackupGraph.Factory
}

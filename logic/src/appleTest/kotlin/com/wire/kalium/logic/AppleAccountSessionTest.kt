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

@file:OptIn(com.wire.kalium.util.InternalKaliumApi::class, com.wire.kalium.util.DelicateKaliumApi::class)

package com.wire.kalium.logic

import app.cash.sqldelight.async.coroutines.synchronous

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.StoreSessionParam
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.sync.receiving.ApplePreparedAccountStore
import dev.mokkery.mock
import dev.mokkery.everySuspend
import dev.mokkery.answering.calls
import dev.mokkery.matcher.any
import okio.FileSystem
import okio.Path.Companion.toPath
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.receiving.AccountRuntimeFailure
import com.wire.kalium.logic.sync.receiving.AppleAccountRuntimeOwner
import com.wire.kalium.logic.sync.receiving.AppleAccountRuntime
import com.wire.kalium.logic.data.client.toCrypto
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.MLSTransporter
import com.wire.kalium.cryptography.MLSEpochObserver
import com.wire.kalium.cryptography.MLSGroupId
import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.persistence.kmmSettings.ApplePersistenceConfig
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.logic.util.databaseSecurityHelper
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.util.KaliumDispatcherImpl
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.sync.receiving.AccountRuntimeLease
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("LargeClass") // Share the real native owner and SQL fixture across lifecycle barrier cases.
class AppleAccountSessionTest {
    private val userId = UserId("fcbe2b38-c66a-4fbe-9e54-6fab39c017a4", "example.com")
    private val fileManager = NSFileManager.defaultManager

    @Test
    fun `synchronous lookup cannot construct an unowned account`() = runTest {
        val (root, logic) = fixture()
        try {
            assertFailsWith<IllegalStateException> { logic.getSessionScope(userId) }
            assertFalse(fileManager.fileExistsAtPath(logic.rootPathsProvider.rootAccountPath(userId)))
        } finally {
            logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
            fileManager.removeItemAtPath(root, null)
        }
    }

    @Test
    fun `cancelled waiting app open creates no account storage`() = runTest {
        val (root, logic) = fixture()
        val owner = AppleAccountRuntimeOwner(root)
        val lease = assertIs<Either.Right<*>>(owner.tryAcquire(userId)).value
            as com.wire.kalium.logic.sync.receiving.AccountRuntimeLease
        try {
            val opening = async { logic.openSessionScope(userId) }
            runCurrent()
            assertFalse(opening.isCompleted)
            assertFalse(fileManager.fileExistsAtPath(logic.rootPathsProvider.rootAccountPath(userId)))
            opening.cancelAndJoin()
            assertEquals(Either.Left(AccountRuntimeFailure.BUSY), owner.tryAcquire(userId))
        } finally {
            lease.release()
            logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
            fileManager.removeItemAtPath(root, null)
        }
    }

    @Test
    fun `partial session construction retains account ownership`() = runTest {
        val (root, logic) = fixture()
        val owner = AppleAccountRuntimeOwner(root)
        try {
            // There is no prepared global account. Construction opens user SQLite before the
            // authenticated container rejects the missing account, exercising a real partial open.
            assertFailsWith<IllegalStateException> { logic.openSessionScope(userId) }
            assertTrue(fileManager.fileExistsAtPath("${logic.rootPathsProvider.rootAccountPath(userId)}/storage"))
            assertEquals(Either.Left(AccountRuntimeFailure.BUSY), owner.tryAcquire(userId))
            assertFailsWith<IllegalStateException> { logic.getSessionScope(userId) }
            assertFailsWith<IllegalStateException> { logic.openSessionScope(userId) }
            assertFailsWith<IllegalStateException> { logic.releaseSessionScope(userId) }
            assertEquals(Either.Left(AccountRuntimeFailure.BUSY), owner.tryAcquire(userId))
        } finally {
            logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
            // The failed constructor's lease deliberately survives until this native process exits.
            // Its unique temporary root must likewise stay in place while that lease is held.
        }
    }

    @Test
    fun `background handoff keeps session and DAO identity while releasing ownership`() = runTest {
        withContext(Dispatchers.Default) {
            val (root, logic) = fixture(prepared = true)
            val first = logic.openSessionScope(userId)
            logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
            val metadata = first.userStorage.database.metadataDAO
            metadata.insertValue("before", "handoff-test")
            try {
                logic.suspendSessionScope(userId)
                kotlin.test.assertSame(first, logic.getSessionScope(userId))
                assertEquals("before", metadata.valueByKey("handoff-test"))
                val lease = assertIs<Either.Right<*>>(AppleAccountRuntimeOwner(root).tryAcquire(userId)).value
                    as com.wire.kalium.logic.sync.receiving.AccountRuntimeLease
                val pending = async(start = CoroutineStart.UNDISPATCHED) { metadata.insertValue("after", "handoff-test") }
                assertFalse(pending.isCompleted)
                lease.release()
                val second = logic.openSessionScope(userId)
                kotlin.test.assertSame(first, second)
                pending.await()
                assertEquals("after", metadata.valueByKey("handoff-test"))
            } finally {
                logic.releaseSessionScope(userId)
            }
        }
    }

    @Test
    fun `app closes real account storage before releasing and can reopen it`() = runTest {
        withContext(Dispatchers.Default) {
            val (root, logic) = fixture(prepared = true)
            val owner = AppleAccountRuntimeOwner(root)
            try {
                val first = logic.openSessionScope(userId)
                assertEquals(Either.Left(AccountRuntimeFailure.BUSY), owner.tryAcquire(userId))
                logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
                logic.releaseSessionScope(userId)
                val lease = assertIs<Either.Right<*>>(owner.tryAcquire(userId)).value
                    as com.wire.kalium.logic.sync.receiving.AccountRuntimeLease
                lease.release()
                val second = logic.openSessionScope(userId)
                assertNotSame(first, second)
                assertFalse(logic.userSessionScopeProvider.value.logout(userId, first, { error("Stale logout ran") }, { error("Stale clear ran") }))
                assertEquals(Either.Left(AccountRuntimeFailure.BUSY), owner.tryAcquire(userId))
                logic.releaseSessionScope(userId)
            } finally {
                logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
                // CoreLogic's global database has process lifetime; keep its unique root until exit.
            }
        }
    }

    @Test
    fun `cancelled session removal waits for ownership before changing the account`() = runTest {
        val (root, logic) = fixture(prepared = true)
        val owner = AppleAccountRuntimeOwner(root)
        val lease = assertIs<Either.Right<*>>(owner.tryAcquire(userId)).value
            as com.wire.kalium.logic.sync.receiving.AccountRuntimeLease
        try {
            val removal = async { logic.getGlobalScope().deleteSession(userId) }
            runCurrent()
            assertFalse(removal.isCompleted)
            removal.cancelAndJoin()
            assertEquals(Either.Right(true), logic.getGlobalScope().sessionRepository.doesSessionExist(userId))
            assertFalse(fileManager.fileExistsAtPath(logic.rootPathsProvider.rootAccountPath(userId)))
            assertEquals(Either.Left(AccountRuntimeFailure.BUSY), owner.tryAcquire(userId))
        } finally {
            lease.release()
            logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
        }
    }

    @Test
    fun `release and duplicate logout wait for the complete originating logout`() = runTest {
        withContext(Dispatchers.Default) {
            val (root, logic) = fixture(prepared = true)
            val scope = logic.openSessionScope(userId)
            logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
            val provider = logic.userSessionScopeProvider.value
            val cells = scope.cells
            val cellsWorker = cells.launch(start = CoroutineStart.UNDISPATCHED) { awaitCancellation() }
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val logout = async {
                provider.logout(userId, scope, {
                    entered.complete(Unit)
                    finish.await()
                }, {
                    assertTrue(cellsWorker.isCompleted)
                    assertTrue(cells.coroutineContext.job.isCompleted)
                })
            }
            entered.await()
            val duplicate = async(start = CoroutineStart.UNDISPATCHED) {
                provider.logout(userId, scope, { error("Duplicate logout ran") }, { error("Duplicate clear ran") })
            }
            val release = async(start = CoroutineStart.UNDISPATCHED) { logic.releaseSessionScope(userId) }
            assertFalse(duplicate.isCompleted)
            assertFalse(release.isCompleted)
            assertEquals(Either.Left(AccountRuntimeFailure.BUSY), AppleAccountRuntimeOwner(root).tryAcquire(userId))
            finish.complete(Unit)
            assertTrue(logout.await())
            assertFalse(duplicate.await())
            release.await()
        }
    }

    @Test
    fun `cancelled credential installation cannot write before ownership`() = runTest {
        val (root, logic) = fixture(prepared = true)
        val owner = AppleAccountRuntimeOwner(root)
        val lease = assertIs<Either.Right<*>>(owner.tryAcquire(userId)).value
            as com.wire.kalium.logic.sync.receiving.AccountRuntimeLease
        val repository = mock<SessionRepository>()
        everySuspend { repository.storeSession(any()) } calls { error("Credentials changed without ownership") }
        try {
            val storing = async { logic.userSessionScopeProvider.value.storeSession(session(), repository) }
            runCurrent()
            assertFalse(storing.isCompleted)
            storing.cancelAndJoin()
            assertFalse(fileManager.fileExistsAtPath(logic.rootPathsProvider.rootAccountPath(userId)))
        } finally {
            lease.release()
            logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
        }
    }

    @Test
    fun `credential replacement closes old workers and revokes readiness before writing`() = runTest {
        withContext(Dispatchers.Default) {
            val (root, logic) = fixture(prepared = true)
            val previous = logic.openSessionScope(userId)
            logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
            val ready = "${logic.rootPathsProvider.rootAccountPath(userId)}/notification-extension-ready.json".toPath()
            FileSystem.SYSTEM.write(ready) { writeUtf8("old readiness") }
            val repository = mock<SessionRepository>()
            everySuspend { repository.storeSession(any()) } calls {
                assertTrue(previous.coroutineContext.job.isCompleted)
                assertFalse(FileSystem.SYSTEM.exists(ready))
                assertEquals(Either.Left(AccountRuntimeFailure.BUSY), AppleAccountRuntimeOwner(root).tryAcquire(userId))
                assertFailsWith<IllegalStateException> { logic.getSessionScope(userId) }
                Either.Right(Unit)
            }
            assertEquals(Either.Right(Unit), logic.userSessionScopeProvider.value.storeSession(session(), repository))
            assertNotSame(previous, logic.openSessionScope(userId))
            logic.releaseSessionScope(userId)
        }
    }

    @Test
    fun `failed credential installation stays unready and retains ownership`() = runTest {
        val (root, logic) = fixture(prepared = true)
        val accountRoot = logic.rootPathsProvider.rootAccountPath(userId)
        assertTrue(fileManager.createDirectoryAtPath(accountRoot, true, null, null))
        val ready = "$accountRoot/notification-extension-ready.json".toPath()
        FileSystem.SYSTEM.write(ready) { writeUtf8("old readiness") }
        val repository = mock<SessionRepository>()
        everySuspend { repository.storeSession(any()) } calls { Either.Left(StorageFailure.DataNotFound) }
        assertIs<Either.Left<*>>(logic.userSessionScopeProvider.value.storeSession(session(), repository))
        assertFalse(FileSystem.SYSTEM.exists(ready))
        assertEquals(Either.Left(AccountRuntimeFailure.BUSY), AppleAccountRuntimeOwner(root).tryAcquire(userId))
        assertFailsWith<IllegalStateException> { logic.openSessionScope(userId) }
        logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
    }

    @Test
    fun `failed readiness revocation prevents account removal`() = runTest {
        val (root, logic) = fixture(prepared = true)
        val ready = "${logic.rootPathsProvider.rootAccountPath(userId)}/notification-extension-ready.json"
        assertTrue(fileManager.createDirectoryAtPath(ready, true, null, null))
        assertIs<Either.Left<*>>(logic.userSessionScopeProvider.value.deleteSession(userId, logic.getGlobalScope().sessionRepository))
        assertEquals(Either.Right(true), logic.getGlobalScope().sessionRepository.doesSessionExist(userId))
        assertEquals(Either.Left(AccountRuntimeFailure.BUSY), AppleAccountRuntimeOwner(root).tryAcquire(userId))
        assertFalse(fileManager.fileExistsAtPath("${logic.rootPathsProvider.rootAccountPath(userId)}/storage"))
        logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
    }

    @Test
    fun `prepared real Proteus and MLS stores survive release and account reopen`() = runTest {
        withContext(Dispatchers.Default) {
            val (root, logic, keychainConfig) = fixture(prepared = true)
            val first = logic.openSessionScope(userId)
            val clientId = ClientId("abc123")
            val cipher = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            assertIs<Either.Right<*>>(logic.getGlobalScope().sessionRepository.storeSession(session()))
            assertIs<Either.Right<*>>(first.clientRepository.persistClientId(clientId))
            val proteus = first.proteusClientProvider.getOrCreate()
            val fingerprint = proteus.transaction("app-fingerprint") { it.getLocalFingerprint() }
            assertIs<Either.Right<*>>(first.userConfigRepository.setMLSEnabled(true))
            assertIs<Either.Right<*>>(first.userConfigRepository.setSupportedCipherSuite(SupportedCipherSuite(listOf(cipher), cipher)))
            val mls = assertIs<Either.Right<com.wire.kalium.cryptography.MLSClient>>(first.mlsClientProvider.getMLSClient(clientId)).value
            mls.initializeBasicCredential()
            val publicKey = mls.getPublicKey().first.toList()
            assertIs<Either.Right<*>>(first.clientRepository.setHasRegisteredMLSClient())
            assertIs<Either.Right<*>>(logic.userSessionScopeProvider.value.prepareReceiving(userId, first))
            logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
            logic.releaseSessionScope(userId)
            val owner = AppleAccountRuntimeOwner(root)
            val lease = assertIs<Either.Right<*>>(owner.tryAcquire(userId)).value
                as com.wire.kalium.logic.sync.receiving.AccountRuntimeLease
            val prepared = assertNotNull(ApplePreparedAccountStore(root, userId).read(lease))
            assertEquals(cipher, prepared.mlsCipherSuite)
            assertEquals(clientId.value, prepared.clientId)
            lease.release()
            assertEquals(Either.Right(fingerprint), AppleAccountRuntime.use(root, userId, keychainConfig, "nse-test", emptyMap()) { runtime ->
                assertEquals(Either.Left(AccountRuntimeFailure.BUSY), owner.tryAcquire(userId))
                assertNotNull(runtime.network.clientApi)
                val receivingMLS = checkNotNull(runtime.mlsCoreCrypto).mlsClient(
                    CryptoQualifiedClientId(clientId.value, CryptoUserID(userId.value, userId.domain)), cipher.toCrypto(),
                    object : MLSTransporter {
                        override suspend fun sendCommitBundle(commitBundle: CommitBundle) { error("Unexpected outgoing commit") }
                    },
                    object : MLSEpochObserver {
                        override suspend fun onEpochChange(groupId: MLSGroupId, epoch: ULong) { error("Unexpected epoch change") }
                    },
                    runtime.processingScope
                )
                assertEquals(publicKey, receivingMLS.getPublicKey().first.toList())
                runtime.proteusCoreCrypto.proteusClient().transaction("extension-fingerprint") { it.getLocalFingerprint() }
            })
            val second = logic.openSessionScope(userId)
            assertIs<Either.Right<*>>(logic.userSessionScopeProvider.value.prepareReceiving(userId, second))
            assertEquals(fingerprint, second.proteusClientProvider.getOrCreate().transaction("reopened-fingerprint") {
                it.getLocalFingerprint()
            })
            val reopenedMLS = assertIs<Either.Right<com.wire.kalium.cryptography.MLSClient>>(second.mlsClientProvider.getMLSClient()).value
            assertEquals(publicKey, reopenedMLS.getPublicKey().first.toList())
            // A restore revokes readiness even if a later restore step fails. Existing handles may
            // refer to the replaced files, so this scope can never certify them after invalidation.
            logic.userSessionScopeProvider.value.invalidateReceiving(userId, second, cryptoReplacement = true)
            assertIs<Either.Left<*>>(logic.userSessionScopeProvider.value.prepareReceiving(userId, second))
            assertIs<Either.Right<*>>(logic.userSessionScopeProvider.value.storeSession(session(), logic.getGlobalScope().sessionRepository))
            val third = logic.openSessionScope(userId)
            assertIs<Either.Right<*>>(logic.userSessionScopeProvider.value.prepareReceiving(userId, third))
            logic.userSessionScopeProvider.value.invalidateReceiving(userId, third)
            logic.releaseSessionScope(userId)
            val afterRestore = assertIs<Either.Right<*>>(owner.tryAcquire(userId)).value
                as com.wire.kalium.logic.sync.receiving.AccountRuntimeLease
            assertNull(ApplePreparedAccountStore(root, userId).read(afterRestore))
            afterRestore.release()
            // Files and credentials alone cannot authorize an interrupted registration, including
            // after a fresh scope opens with no readiness record.
            logic.openSessionScope(userId)
            logic.releaseSessionScope(userId)
            val afterReopen = assertIs<Either.Right<*>>(owner.tryAcquire(userId)).value
                as com.wire.kalium.logic.sync.receiving.AccountRuntimeLease
            assertNull(ApplePreparedAccountStore(root, userId).read(afterReopen))
            afterReopen.release()
        }
    }

    @Test
    fun `background handoff drains crypto callback writes without replacing the session`() = runTest {
        withContext(Dispatchers.Default) {
            val (root, logic) = fixture(prepared = true)
            val scope = logic.openSessionScope(userId)
            assertIs<Either.Right<*>>(logic.getGlobalScope().sessionRepository.storeSession(session()))
            assertIs<Either.Right<*>>(scope.clientRepository.persistClientId(ClientId("abc123")))
            val proteus = scope.proteusClientProvider.getOrCreate()
            assertIs<Either.Right<*>>(logic.userSessionScopeProvider.value.prepareReceiving(userId, scope))
            logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val worker = async {
                proteus.transaction("handoff-callback") {
                    entered.complete(Unit)
                    finish.await()
                    scope.userStorage.database.metadataDAO.insertValue(value = "committed", key = "crypto-callback")
                }
            }
            entered.await()
            val handoff = async(start = CoroutineStart.UNDISPATCHED) { logic.suspendSessionScope(userId) }
            try {
                assertFalse(handoff.isCompleted)
                finish.complete(Unit)
                worker.await()
                handoff.await()
                val lease = assertIs<Either.Right<*>>(AppleAccountRuntimeOwner(root).tryAcquire(userId)).value
                    as com.wire.kalium.logic.sync.receiving.AccountRuntimeLease
                lease.release()
                kotlin.test.assertSame(scope, logic.openSessionScope(userId))
                assertEquals("committed", scope.userStorage.database.metadataDAO.valueByKey("crypto-callback"))
                // The retained client must lazily open a new native generation after the handoff.
                assertTrue(proteus.newPreKeysAuto(1).isNotEmpty())
            } finally {
                finish.complete(Unit)
                worker.await()
                handoff.await()
                logic.releaseSessionScope(userId)
            }
        }
    }

    @Test
    fun `release times out draining a real crypto transaction before readiness validation`() = runTest {
        withContext(Dispatchers.Default) {
            val (root, logic) = fixture(prepared = true)
            val scope = logic.openSessionScope(userId)
            assertIs<Either.Right<*>>(logic.getGlobalScope().sessionRepository.storeSession(session()))
            assertIs<Either.Right<*>>(scope.clientRepository.persistClientId(ClientId("abc123")))
            val proteus = scope.proteusClientProvider.getOrCreate()
            assertIs<Either.Right<*>>(logic.userSessionScopeProvider.value.prepareReceiving(userId, scope))
            logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val worker = scope.launch {
                proteus.transaction("stalled-worker") {
                    entered.complete(Unit)
                    finish.await()
                }
            }
            entered.await()
            try {
                assertFailsWith<TimeoutCancellationException> { logic.releaseSessionScope(userId) }
                assertEquals(Either.Left(AccountRuntimeFailure.BUSY), AppleAccountRuntimeOwner(root).tryAcquire(userId))
                assertFailsWith<IllegalStateException> { logic.openSessionScope(userId) }
            } finally {
                finish.complete(Unit)
                worker.join()
                // The failed drain deliberately quarantines this account until process exit.
            }
        }
    }

    @Test
    fun `subscribed observer stays idle after full release until manual ownership open`() = runTest {
        withContext(Dispatchers.Default) {
            val (root, logic) = fixture(prepared = true)
            val owner = AppleAccountRuntimeOwner(root)
            val values = Channel<UserSessionScope?>(Channel.UNLIMITED)
            val observer = launch {
                logic.observeValidSession<UserSessionScope>(userId) {
                    val captured = this
                    flow { emit(captured); awaitCancellation() }
                }.collect { values.send(it) }
            }
            var external: AccountRuntimeLease? = null
            try {
                val first = values.nextAvailableScope()
                logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
                logic.releaseSessionScope(userId)
                external = assertIs<Either.Right<AccountRuntimeLease>>(owner.tryAcquire(userId)).value
                // Keep the external lease held: an idle repeated release must finish without trying
                // to acquire it, even while the old observation remains subscribed to Valid metadata.
                logic.releaseSessionScope(userId)
                assertNull(logic.userSessionScopeProvider.value.get(userId))
                assertEquals(Either.Right(true), logic.getGlobalScope().sessionRepository.doesValidSessionExist(userId))
                assertTrue(observer.isActive)
                val opening = async(start = CoroutineStart.UNDISPATCHED) { logic.openSessionScope(userId) }
                assertFalse(opening.isCompleted)
                assertEquals(Either.Left(AccountRuntimeFailure.BUSY), owner.tryAcquire(userId))
                external.release()
                external = null
                val second = opening.await()
                assertNotSame(first, second)
                kotlin.test.assertSame(second, values.nextAvailableScope())
                assertEquals(Either.Left(AccountRuntimeFailure.BUSY), owner.tryAcquire(userId))
                // Ordinary background handoff retains the generation and the subscribed observer.
                logic.suspendSessionScope(userId)
                kotlin.test.assertSame(second, logic.getSessionScope(userId))
                external = assertIs<Either.Right<AccountRuntimeLease>>(owner.tryAcquire(userId)).value
                external.release()
                external = null
                kotlin.test.assertSame(second, logic.openSessionScope(userId))
                assertTrue(observer.isActive)
            } finally {
                external?.release()
                observer.cancelAndJoin()
                logic.releaseSessionScope(userId)
                logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
                // Reuse the existing fixture policy for CoreLogic's process-owned global storage.
            }
        }
    }

    @Test
    fun `continuously missing and Invalid observers leave account ownership free`() = runTest {
        withContext(Dispatchers.Default) {
            for (prepared in listOf(false, true)) {
                val (root, logic) = fixture(prepared = prepared)
                val repository = logic.getGlobalScope().sessionRepository
                if (prepared) assertIs<Either.Right<Unit>>(repository.logout(userId, LogoutReason.SELF_SOFT_LOGOUT))
                val values = Channel<UserSessionScope?>(Channel.UNLIMITED)
                val observer = launch {
                    logic.observeValidSession<UserSessionScope>(userId) {
                        error("Missing/Invalid account constructed an observation scope")
                    }.collect { values.send(it) }
                }
                var external: AccountRuntimeLease? = null
                try {
                    assertNull(values.receive())
                    external = assertIs<Either.Right<AccountRuntimeLease>>(AppleAccountRuntimeOwner(root).tryAcquire(userId)).value
                    assertNull(logic.userSessionScopeProvider.value.get(userId))
                    assertFalse(fileManager.fileExistsAtPath("${logic.rootPathsProvider.rootAccountPath(userId)}/storage"))
                    assertTrue(observer.isActive)
                    // Keep observing the real SQL snapshot while another process owns the account.
                    assertEquals(Either.Right(false), repository.doesValidSessionExist(userId))
                    observer.cancelAndJoin()
                    assertEquals(Either.Left(AccountRuntimeFailure.BUSY), AppleAccountRuntimeOwner(root).tryAcquire(userId))
                } finally {
                    observer.cancelAndJoin()
                    external?.release()
                    logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
                }
            }
        }
    }

    @Test
    fun `cancelled initial live validation releases only its unused lease and permits another qualified user`() = runTest {
        withContext(Dispatchers.Default) {
            val otherUser = UserId(userId.value, "other.example.com")
            val (root, logic) = fixture(prepared = true, otherUser = otherUser)
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val repository = logic.getGlobalScope().sessionRepository.pauseLiveValidity(root, entered, finish)
            val provider = logic.userSessionScopeProvider.value
            val observer = launch {
                provider.observeValidSession<UserSessionScope>(userId, repository) {
                    error("Cancelled attachment opened a scope")
                }.collect()
            }
            val otherValues = Channel<UserSessionScope?>(Channel.UNLIMITED)
            val otherObserver = launch {
                logic.observeValidSession<UserSessionScope>(otherUser) {
                    val captured = this
                    flow { emit(captured); awaitCancellation() }
                }.collect { otherValues.send(it) }
            }
            try {
                entered.await() // The delegate checks the real native lease is already held.
                assertNull(provider.get(userId))
                assertEquals(otherUser, otherValues.nextAvailableScope().userId)
                observer.cancelAndJoin()
                assertTrue(observer.isCancelled)
                assertFalse(fileManager.fileExistsAtPath("${logic.rootPathsProvider.rootAccountPath(userId)}/storage"))
                assertIs<Either.Right<AccountRuntimeLease>>(AppleAccountRuntimeOwner(root).tryAcquire(userId)).value.release()
                assertEquals(Either.Left(AccountRuntimeFailure.BUSY), AppleAccountRuntimeOwner(root).tryAcquire(otherUser))
            } finally {
                observer.cancelAndJoin()
                otherObserver.cancelAndJoin()
                logic.releaseSessionScope(userId)
                logic.releaseSessionScope(otherUser)
                logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
            }
        }
    }

    @Test
    fun `SQL Invalid emission cancels pending live validation and frees its lease while subscribed`() = runTest {
        withContext(Dispatchers.Default) {
            val (root, logic) = fixture(prepared = true)
            val repository = logic.getGlobalScope().sessionRepository
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val values = Channel<UserSessionScope?>(Channel.UNLIMITED)
            val observer = launch {
                logic.userSessionScopeProvider.value.observeValidSession<UserSessionScope>(
                    userId, repository.pauseLiveValidity(root, entered, finish),
                ) { error("Invalidated attachment opened a scope") }.collect { values.send(it) }
            }
            try {
                assertNull(values.receive())
                entered.await()
                assertIs<Either.Right<Unit>>(repository.logout(userId, LogoutReason.SELF_SOFT_LOGOUT))
                // collectLatest joins the cancelled attachment (including lease cleanup) before
                // delivering this next unavailable value from the actual SQL Invalid emission.
                assertNull(values.receive())
                assertTrue(observer.isActive)
                assertNull(logic.userSessionScopeProvider.value.get(userId))
                assertIs<Either.Right<AccountRuntimeLease>>(AppleAccountRuntimeOwner(root).tryAcquire(userId)).value.release()
            } finally {
                observer.cancelAndJoin()
                logic.releaseSessionScope(userId)
                logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
            }
        }
    }

    @Test
    fun `cancelled live validation unwinds automatic resume of an empty background account`() = runTest {
        withContext(Dispatchers.Default) {
            val (root, logic) = fixture(prepared = true)
            val repository = logic.getGlobalScope().sessionRepository
            val provider = logic.userSessionScopeProvider.value
            assertIs<Either.Right<Unit>>(provider.storeSession(session(), repository))
            assertNull(provider.get(userId))
            logic.suspendSessionScope(userId)
            assertIs<Either.Right<AccountRuntimeLease>>(AppleAccountRuntimeOwner(root).tryAcquire(userId)).value.release()
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val observer = launch {
                provider.observeValidSession<UserSessionScope>(userId, repository.pauseLiveValidity(root, entered, finish)) {
                    error("Cancelled resumed attachment opened a scope")
                }.collect()
            }
            try {
                entered.await() // withOwnedAccount's automatic openResources reacquired first.
                observer.cancelAndJoin()
                assertNull(provider.get(userId))
                assertFalse(fileManager.fileExistsAtPath("${logic.rootPathsProvider.rootAccountPath(userId)}/storage"))
                assertIs<Either.Right<AccountRuntimeLease>>(AppleAccountRuntimeOwner(root).tryAcquire(userId)).value.release()
            } finally {
                observer.cancelAndJoin()
                logic.releaseSessionScope(userId)
                logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
            }
        }
    }

    @Test
    fun `cancelling live validation preserves a prior credential store lease without a scope`() = runTest {
        withContext(Dispatchers.Default) {
            val (root, logic) = fixture(prepared = true)
            val repository = logic.getGlobalScope().sessionRepository
            val provider = logic.userSessionScopeProvider.value
            assertIs<Either.Right<Unit>>(provider.storeSession(session(), repository))
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val observer = launch {
                provider.observeValidSession<UserSessionScope>(userId, repository.pauseLiveValidity(root, entered, finish)) {
                    error("Cancelled attachment opened a scope")
                }.collect()
            }
            try {
                entered.await()
                observer.cancelAndJoin()
                assertNull(provider.get(userId))
                assertEquals(Either.Left(AccountRuntimeFailure.BUSY), AppleAccountRuntimeOwner(root).tryAcquire(userId))
            } finally {
                observer.cancelAndJoin()
                logic.releaseSessionScope(userId)
                logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
            }
        }
    }

    @Test
    fun `cancelling an admitted observer keeps the successful session and its lease`() = runTest {
        withContext(Dispatchers.Default) {
            val (root, logic) = fixture(prepared = true)
            val values = Channel<UserSessionScope?>(Channel.UNLIMITED)
            val observer = launch {
                logic.observeValidSession<UserSessionScope>(userId) {
                    val captured = this
                    flow { emit(captured); awaitCancellation() }
                }.collect { values.send(it) }
            }
            try {
                val scope = values.nextAvailableScope()
                observer.cancelAndJoin()
                kotlin.test.assertSame(scope, logic.getSessionScope(userId))
                assertTrue(scope.coroutineContext.job.isActive)
                assertEquals(Either.Left(AccountRuntimeFailure.BUSY), AppleAccountRuntimeOwner(root).tryAcquire(userId))
            } finally {
                observer.cancelAndJoin()
                logic.releaseSessionScope(userId)
                logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
            }
        }
    }

    @Test
    fun `a credential store adopting the attachment lease prevents cancellation cleanup releasing it`() = runTest {
        withContext(Dispatchers.Default) {
            val (root, logic) = fixture(prepared = true)
            val repository = logic.getGlobalScope().sessionRepository
            val provider = logic.userSessionScopeProvider.value
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val observer = launch {
                provider.observeValidSession<UserSessionScope>(
                    userId, repository.pauseLiveValidity(root, entered, finish, finishOnCancellation = true),
                ) { error("Cancelled attachment opened a scope") }.collect()
            }
            val writeEntered = CompletableDeferred<Unit>()
            val writeFinish = CompletableDeferred<Unit>()
            val storingRepository = object : SessionRepository by repository {
                override suspend fun storeSession(session: StoreSessionParam): Either<StorageFailure, Unit> {
                    writeEntered.complete(Unit)
                    writeFinish.await()
                    return repository.storeSession(session)
                }
            }
            var storing: Job? = null
            try {
                entered.await()
                // Start undispatched until blocked on the existing account mutex behind validation.
                // Mutex FIFO puts this host operation ahead of the attachment's cleanup reacquisition.
                val store = async(start = CoroutineStart.UNDISPATCHED) { provider.storeSession(session(), storingRepository) }
                storing = store
                assertFalse(store.isCompleted)
                observer.cancel()
                finish.complete(Unit)
                writeEntered.await()
                assertNull(provider.get(userId))
                assertEquals(Either.Left(AccountRuntimeFailure.BUSY), AppleAccountRuntimeOwner(root).tryAcquire(userId))
                writeFinish.complete(Unit)
                assertIs<Either.Right<Unit>>(store.await())
                observer.join()
                assertNull(provider.get(userId))
                assertEquals(Either.Left(AccountRuntimeFailure.BUSY), AppleAccountRuntimeOwner(root).tryAcquire(userId))
            } finally {
                finish.complete(Unit)
                writeFinish.complete(Unit)
                observer.cancelAndJoin()
                storing?.cancelAndJoin()
                logic.releaseSessionScope(userId)
                logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
            }
        }
    }

    @Test
    fun `genuine live validation failure propagates after releasing its unused attachment lease`() = runTest {
        withContext(Dispatchers.Default) {
            val (root, logic) = fixture(prepared = true)
            val repository = logic.getGlobalScope().sessionRepository
            val failure = IllegalStateException("Live validation failed")
            val failingRepository = object : SessionRepository by repository {
                override suspend fun doesValidSessionExist(userId: UserId): Either<StorageFailure, Boolean> {
                    assertEquals(Either.Left(AccountRuntimeFailure.BUSY), AppleAccountRuntimeOwner(root).tryAcquire(userId))
                    throw failure
                }
            }
            try {
                val caught = assertFailsWith<IllegalStateException> {
                    logic.userSessionScopeProvider.value.observeValidSession<UserSessionScope>(userId, failingRepository) {
                        error("Failed validation opened a scope")
                    }.collect()
                }
                kotlin.test.assertSame(failure, caught)
                assertNull(logic.userSessionScopeProvider.value.get(userId))
                assertIs<Either.Right<AccountRuntimeLease>>(AppleAccountRuntimeOwner(root).tryAcquire(userId)).value.release()
            } finally {
                logic.releaseSessionScope(userId)
                logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
            }
        }
    }

    /** Delegate only the live read barrier; SQL metadata, validity and the native provider remain real. */
    private fun SessionRepository.pauseLiveValidity(
        root: String,
        entered: CompletableDeferred<Unit>,
        finish: CompletableDeferred<Unit>,
        finishOnCancellation: Boolean = false,
    ): SessionRepository {
        val repository = this
        val target = userId
        return object : SessionRepository by repository {
            override suspend fun doesValidSessionExist(userId: UserId): Either<StorageFailure, Boolean> {
                if (userId == target) {
                    val acquired = AppleAccountRuntimeOwner(root).tryAcquire(userId)
                    if (acquired is Either.Right) acquired.value.release()
                    assertEquals(Either.Left(AccountRuntimeFailure.BUSY), acquired)
                    entered.complete(Unit)
                    if (finishOnCancellation) withContext(NonCancellable) { finish.await() } else finish.await()
                }
                return repository.doesValidSessionExist(userId)
            }
        }
    }

    private suspend fun Channel<UserSessionScope?>.nextAvailableScope(): UserSessionScope {
        while (true) receive()?.let { return it }
    }

    private fun session() = StoreSessionParam(
        serverConfigId = "test-server", ssoId = null,
        accountTokens = AccountTokens(userId, "access", "refresh", "Bearer", "cookie"),
        proxyCredentials = null, managedBy = null, isPersistentWebSocketEnabled = false,
    )

    private suspend fun fixture(prepared: Boolean = false, otherUser: UserId? = null): Triple<String, CoreLogic, ApplePersistenceConfig> {
        val identity = NSUUID.UUID().UUIDString
        val root = "${NSTemporaryDirectory()}kalium-account-session-$identity"
        assertTrue(fileManager.createDirectoryAtPath(root, true, null, null))
        val keychainConfig = ApplePersistenceConfig("com.wire.kalium.test.$identity", keychainAccessibility = null)
        if (prepared) {
            val secret = databaseSecurityHelper(GlobalPrefProvider(keychainConfig).passphraseStorage)
                .globalDBKeyMaterial(databaseExists = false).rawKey
            val database = globalDatabaseProvider(
                PlatformDatabaseData(StorageData.FileBacked("$root/global-storage")), KaliumDispatcherImpl.io, secret, enableWAL = true
            )
            try {
                database.serverConfigurationDAO.insert(ServerConfigurationDAO.InsertData(
                    id = "test-server", apiBaseUrl = "https://example.invalid", accountBaseUrl = "https://example.invalid",
                    webSocketBaseUrl = "wss://example.invalid", blackListUrl = "https://example.invalid",
                    teamsUrl = "https://example.invalid", websiteUrl = "https://example.invalid", title = "Test",
                    isOnPremises = false, federation = false, domain = userId.domain, commonApiVersion = 8,
                    apiProxyHost = null, apiProxyNeedsAuthentication = null, apiProxyPort = null,
                ))
                listOfNotNull(userId, otherUser).forEach { account ->
                    database.accountsDAO.insertOrReplace(
                        UserIDEntity(account.value, account.domain), null, null, "test-server", false, null,
                    )
                }
            } finally {
                database.close()
            }
        }
        val logic = CoreLogic(
            rootPath = root,
            keychainConfig = keychainConfig,
            kaliumConfigs = KaliumConfigs(enableCalling = false, mockedRequests = emptyList()),
            userAgent = "kalium-native-account-test",
            useInMemoryStorage = !prepared,
        )
        return Triple(root, logic, keychainConfig)
    }
}

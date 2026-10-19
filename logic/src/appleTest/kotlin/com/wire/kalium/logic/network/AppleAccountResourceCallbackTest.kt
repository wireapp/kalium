/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.logic.network

import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.CryptoCredential
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.GroupInfoBundle
import com.wire.kalium.cryptography.GroupInfoEncryptionType
import com.wire.kalium.cryptography.MLSCiphersuite
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MLSEpochObserver
import com.wire.kalium.cryptography.MLSTransporter
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.PkiEnvironmentHooks
import com.wire.kalium.cryptography.RatchetTreeType
import com.wire.kalium.cryptography.ReopenableCoreCryptoCentral
import com.wire.kalium.cryptography.X509CredentialAcquisitionConfig
import com.wire.kalium.util.AccountResourceAccess
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/**
 * Regression tests for PR #188. The drain tests fail on 77e2aee with the handoff still pending.
 * The separate scope models UniFFI's GlobalScope callbacks. It shares the test scheduler,
 * but it cannot inherit the enclosing crypto operation's account admission context.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppleAccountResourceCallbackTest {
    @Test
    fun suspensionDrainsMlsTransportAuthenticationCallback() = runTest {
        val access = AccountResourceAccess()
        val callbacks = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob())
        val entered = CompletableDeferred<Unit>()
        val continueAuthentication = CompletableDeferred<Unit>()
        val native = mock<CoreCryptoCentral>()
        val nativeClient = mock<MLSClient>()
        val context = mock<MlsCoreCryptoContext>()
        val central = ReopenableCoreCryptoCentral(access) { native }
        val identity = CryptoQualifiedClientId("abc123", CryptoUserID("user", "wire.test"))
        val transport = object : MLSTransporter {
            override suspend fun sendCommitBundle(commitBundle: CommitBundle) {
                entered.complete(Unit)
                continueAuthentication.await()
                // Models SessionManager.session/updateToken in the authenticated HTTP client.
                access.withAccess { }
            }
        }
        val observer = mock<MLSEpochObserver>()
        lateinit var registeredTransport: MLSTransporter
        everySuspend { native.close() } returns Unit
        everySuspend { native.mlsClient(any(), any(), any(), any(), any()) } calls { invocation ->
            registeredTransport = invocation.arg(2)
            nativeClient
        }
        everySuspend { nativeClient.transaction<Unit>(any(), any()) } calls { invocation ->
            val block = invocation.arg<suspend (MlsCoreCryptoContext) -> Unit>(1)
            withContext(NonCancellable) { callbacks.async { block(context) }.await() }
        }
        everySuspend { context.updateKeyingMaterial(any()) } calls {
            val bundle = CommitBundle(
                byteArrayOf(), null,
                GroupInfoBundle(GroupInfoEncryptionType.PLAINTEXT, RatchetTreeType.FULL, byteArrayOf())
            )
            // Rust invokes transport in another callback, outside the restored transaction block.
            callbacks.async { registeredTransport.sendCommitBundle(bundle) }.await()
        }
        try {
            val client = central.mlsClient(identity, MLSCiphersuite.DEFAULT, transport, observer, backgroundScope)
            assertHandoffDrains(access, central, entered, continueAuthentication) {
                client.transaction("send-commit") { it.updateKeyingMaterial("group") }
            }
        } finally {
            callbacks.cancel()
        }
    }

    @Test
    fun suspensionDrainsPkiAuthenticationCallback() = runTest {
        val access = AccountResourceAccess()
        val callbacks = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob())
        val entered = CompletableDeferred<Unit>()
        val continueAuthentication = CompletableDeferred<Unit>()
        val native = mock<CoreCryptoCentral>()
        val hooks = mock<PkiEnvironmentHooks>()
        val credential = mock<CryptoCredential>()
        val central = ReopenableCoreCryptoCentral(access) { native }
        lateinit var registeredHooks: PkiEnvironmentHooks
        everySuspend { native.close() } returns Unit
        everySuspend { native.configurePkiEnvironment(any()) } calls { invocation ->
            registeredHooks = invocation.arg(0)
        }
        everySuspend { hooks.getBackendNonce() } calls {
            entered.complete(Unit)
            continueAuthentication.await()
            access.withAccess { "nonce" }
        }
        everySuspend { native.startX509CredentialAcquisition(any(), any()) } calls {
            callbacks.async { registeredHooks.getBackendNonce() }.await()
            credential
        }
        try {
            central.configurePkiEnvironment(hooks)
            val identity = CryptoQualifiedClientId("abc123", CryptoUserID("user", "wire.test"))
            val config = X509CredentialAcquisitionConfig(
                "https://acme.test",
                MLSCiphersuite.DEFAULT,
                "User",
                identity,
                "user",
                null,
                1.hours
            )
            assertHandoffDrains(access, central, entered, continueAuthentication) {
                central.startX509CredentialAcquisition(config)
            }
        } finally {
            callbacks.cancel()
        }
    }

    @Test
    fun pkiHooksRebindToReopenedNativeGeneration() = runTest {
        val access = AccountResourceAccess()
        val native = mock<CoreCryptoCentral>()
        val hooks = mock<PkiEnvironmentHooks>()
        val credential = mock<CryptoCredential>()
        val registered = mutableListOf<PkiEnvironmentHooks>()
        everySuspend { native.close() } returns Unit
        everySuspend { native.configurePkiEnvironment(any()) } calls { invocation -> registered += invocation.arg<PkiEnvironmentHooks>(0) }
        everySuspend { hooks.getBackendNonce() } calls { access.withAccess { "nonce" } }
        everySuspend { native.startX509CredentialAcquisition(any(), any()) } calls {
            if (registered.size > 1) {
                assertFailsWith<IllegalStateException> { registered.first().getBackendNonce() }
            }
            assertEquals("nonce", backgroundScope.async { registered.last().getBackendNonce() }.await())
            credential
        }
        val central = ReopenableCoreCryptoCentral(access) { native }
        val identity = CryptoQualifiedClientId("abc123", CryptoUserID("user", "wire.test"))
        val config = X509CredentialAcquisitionConfig(
            "https://acme.test", MLSCiphersuite.DEFAULT, "User", identity, "user", null, 1.hours
        )
        central.configurePkiEnvironment(hooks)
        central.startX509CredentialAcquisition(config)
        access.pause { central.suspendNativeResources() }
        access.resume {}
        central.startX509CredentialAcquisition(config)
        assertEquals(2, registered.size)
        central.close()
    }

    private suspend fun TestScope.assertHandoffDrains(
        access: AccountResourceAccess,
        central: ReopenableCoreCryptoCentral,
        entered: CompletableDeferred<Unit>,
        continueAuthentication: CompletableDeferred<Unit>,
        operation: suspend () -> Unit,
    ) {
        val worker = backgroundScope.async { operation() }
        runCurrent()
        assertTrue(entered.isCompleted, "The native callback must start before suspension")
        val handoff = backgroundScope.launch { access.pause { central.suspendNativeResources() } }
        var drained = false
        try {
            runCurrent()
            assertNull(access.tryAcquirePermit(), "Suspension must stop admission before token lookup")
            continueAuthentication.complete(Unit)
            runCurrent()
            drained = worker.isCompleted && handoff.isCompleted
        } finally {
            // Cancel suspension first to restore admission. A native transaction cannot be cancelled.
            handoff.cancelAndJoin()
            runCurrent()
            worker.await()
            access.resume { }
            central.close()
        }
        assertTrue(drained, "Suspension must drain the native authentication callback before closing resources")
    }
}

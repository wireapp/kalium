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
package com.wire.kalium.logic.feature.featureConfig.handler

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MLSConfigHandlerTest {
    @Test
    fun givenMlsIsEnabledAndMlsIsDefaultProtocol_whenSyncing_thenSetMlsAsDefault() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(Either.Right(setOf(SupportedProtocol.PROTEUS)))
            withSetSupportedProtocolsSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetMLSEnabledSuccessful()
        }

        handler.handle(
            MLS_CONFIG.copy(
                status = Status.ENABLED,
                defaultProtocol = SupportedProtocol.MLS
            ), duringSlowSync = false
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setDefaultProtocol(eq(SupportedProtocol.MLS))
        }
    }

    @Test
    fun givenMlsIsEnabledAndProteusIsDefaultProtocol_whenSyncing_thenSetProteusAsDefault() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(Either.Right(setOf(SupportedProtocol.PROTEUS)))
            withSetSupportedProtocolsSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetMLSEnabledSuccessful()
        }

        handler.handle(
            MLS_CONFIG.copy(
                status = Status.ENABLED,
                defaultProtocol = SupportedProtocol.PROTEUS
            ), duringSlowSync = false
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setDefaultProtocol(eq(SupportedProtocol.PROTEUS))
        }
    }

    @Test
    fun givenMlsIsDisabledAndMlsIsDefaultProtocol_whenSyncing_thenSetProteusAsDefault() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(Either.Right(setOf(SupportedProtocol.PROTEUS)))
            withSetSupportedProtocolsSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetMLSEnabledSuccessful()
        }

        handler.handle(
            MLS_CONFIG.copy(
                status = Status.DISABLED,
                defaultProtocol = SupportedProtocol.MLS
            ), duringSlowSync = false
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setDefaultProtocol(eq(SupportedProtocol.PROTEUS))
        }
    }

    @Test
    fun givenMlsIsEnabledAndMlsIsSupported_whenSyncing_thenSetMlsEnabled() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(Either.Right(setOf(SupportedProtocol.PROTEUS)))
            withSetSupportedProtocolsSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetMLSEnabledSuccessful()
            withUpdateSupportedProtocolsAndResolveOneOnOnesSuccessful()
        }

        handler.handle(
            MLS_CONFIG.copy(
                status = Status.ENABLED,
                supportedProtocols = setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS)
            ), duringSlowSync = false
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setMLSEnabled(eq(true))
        }
    }

    @Test
    fun givenMlsIsDisabled_whenSyncing_thenSetMlsDisabled() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(Either.Right(setOf(SupportedProtocol.PROTEUS)))
            withSetSupportedProtocolsSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetMLSEnabledSuccessful()
        }

        handler.handle(
            MLS_CONFIG.copy(
                status = Status.DISABLED

            ), duringSlowSync = false
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setMLSEnabled(eq(false))
        }
    }

    @Test
    fun givenSupportedProtocolsHasChangedInEvent_whenSyncing_thenUpdateSelfSupportedProtocols() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(Either.Right(setOf(SupportedProtocol.PROTEUS)))
            withUpdateSupportedProtocolsAndResolveOneOnOnesSuccessful()
            withSetSupportedProtocolsSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetMLSEnabledSuccessful()
        }

        handler.handle(
            MLS_CONFIG.copy(
                status = Status.ENABLED,
                supportedProtocols = setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS)
            ), duringSlowSync = false
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateSupportedProtocolsAndResolveOneOnOnes.invoke(any(), eq(true))
        }
    }

    @Test
    fun givenSupportedProtocolsHasChangedDuringSlowSync_whenSyncing_thenUpdateSelfSupportedProtocols() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(Either.Right(setOf(SupportedProtocol.PROTEUS)))
            withUpdateSupportedProtocolsAndResolveOneOnOnesSuccessful()
            withSetSupportedProtocolsSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetMLSEnabledSuccessful()
        }

        handler.handle(
            MLS_CONFIG.copy(
                status = Status.ENABLED,
                supportedProtocols = setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS)
            ), duringSlowSync = true
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateSupportedProtocolsAndResolveOneOnOnes.invoke(any(), eq(false))
        }
    }


    @Test
    fun givenSupportedProtocolsChangedWithTransactionContext_whenSyncing_thenUpdateProtocolsDirectlyWithoutNewTransaction() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(Either.Right(setOf(SupportedProtocol.PROTEUS)))
            withUpdateSupportedProtocolsAndResolveOneOnOnesSuccessful()
            withSetSupportedProtocolsSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetMLSEnabledSuccessful()
        }

        handler.handle(
            MLS_CONFIG.copy(
                status = Status.ENABLED,
                supportedProtocols = setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS)
            ),
            duringSlowSync = false,
            transactionContext = arrangement.transactionContext
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateSupportedProtocolsAndResolveOneOnOnes.invoke(eq(arrangement.transactionContext), eq(true))
        }
        verifySuspend(VerifyMode.not) {
            arrangement.cryptoTransactionProvider.transaction<Any>(any(), any())
        }
    }

    @Test
    fun givenSupportedCipherSuiteIsNotNull_whenHandlling_thenStoreTheSupportedCipherSuite() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS).right())
            withSetMLSEnabledSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetSupportedProtocolsSuccessful()
            withSetSupportedCipherSuite(Unit.right())
        }

        handler.handle(
            MLS_CONFIG.copy(
                status = Status.ENABLED,
                supportedProtocols = setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS),
                supportedCipherSuite = SupportedCipherSuite(
                    supported = listOf(
                        CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519,
                        CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
                    ),
                    default = CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
                )
            ),
            duringSlowSync = true,
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setSupportedCipherSuite(
                eq(
                    SupportedCipherSuite(
                        supported = listOf(
                            CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519,
                            CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
                        ),
                        default = CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
                    )
                )
            )
        }
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val userConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)
        val updateSupportedProtocolsAndResolveOneOnOnes = mock<UpdateSupportedProtocolsAndResolveOneOnOnesUseCase>(mode = MockMode.autoUnit)

        suspend fun arrange() = run {
            withTransactionReturning(Either.Right(Unit))
            runBlocking { block() }
            this@Arrangement to MLSConfigHandler(
                userConfigRepository = userConfigRepository,
                updateSupportedProtocolsAndResolveOneOnOnes = updateSupportedProtocolsAndResolveOneOnOnes,
                transactionProvider = cryptoTransactionProvider
            )
        }

        suspend fun withGetSupportedProtocolsReturning(result: Either<StorageFailure, Set<SupportedProtocol>>) {
            everySuspend { userConfigRepository.getSupportedProtocols() } returns result
        }

        suspend fun withSetSupportedProtocolsSuccessful() {
            everySuspend { userConfigRepository.setSupportedProtocols(any()) } returns Either.Right(Unit)
        }

        suspend fun withSetDefaultProtocolSuccessful() {
            everySuspend { userConfigRepository.setDefaultProtocol(any()) } returns Either.Right(Unit)
        }

        suspend fun withSetMLSEnabledSuccessful() {
            everySuspend { userConfigRepository.setMLSEnabled(any()) } returns Either.Right(Unit)
        }

        suspend fun withSetSupportedCipherSuite(result: Either<StorageFailure, Unit>) {
            everySuspend { userConfigRepository.setSupportedCipherSuite(any()) } returns result
        }

        suspend fun withUpdateSupportedProtocolsAndResolveOneOnOnesSuccessful() {
            everySuspend { updateSupportedProtocolsAndResolveOneOnOnes.invoke(any(), any()) } returns Either.Right(Unit)
        }
    }

    private companion object {
        suspend fun arrange(configuration: suspend Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        val SELF_USER_ID = TestUser.USER_ID
        val MLS_CONFIG = MLSModel(
            defaultProtocol = SupportedProtocol.MLS,
            supportedProtocols = setOf(SupportedProtocol.PROTEUS),
            status = Status.ENABLED,
            supportedCipherSuite = null
        )
    }
}

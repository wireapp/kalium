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
package com.wire.kalium.logic.feature.search

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.feature.conversation.GetConversationProtocolInfoUseCase
import com.wire.kalium.logic.feature.user.GetDefaultProtocolUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversation.PROTEUS_PROTOCOL_INFO
import com.wire.kalium.util.KaliumDispatcherImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IsFederationSearchAllowedUseCaseTest {

    @Test
    fun givenMLSIsNotConfigured_whenInvokingIsFederationSearchAllowed_thenReturnTrue() = runTest {
        val (arrangement, isFederationSearchAllowedUseCase) = Arrangement()
            .withMLSConfiguredForBackend(isConfigured = false)
            .arrange()

        val isAllowed = isFederationSearchAllowedUseCase(conversationId = null)

        assertEquals(true, isAllowed)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsPublicKeysRepository.getKeys() }
        verifySuspend(VerifyMode.not) { arrangement.getDefaultProtocol.invoke() }
        verifySuspend(VerifyMode.not) { arrangement.getConversationProtocolInfo.invoke(any()) }
    }

    @Test
    fun givenMLSIsConfiguredAndAMLSTeamWithEmptyKeys_whenInvokingIsFederationSearchAllowed_thenReturnTrue() = runTest {
        val (arrangement, isFederationSearchAllowedUseCase) = Arrangement()
            .withEmptyMlsKeys()
            .arrange()

        val isAllowed = isFederationSearchAllowedUseCase(conversationId = null)

        assertEquals(true, isAllowed)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsPublicKeysRepository.getKeys() }
        verifySuspend(VerifyMode.not) { arrangement.getDefaultProtocol.invoke() }
        verifySuspend(VerifyMode.not) { arrangement.getConversationProtocolInfo.invoke(any()) }
    }

    @Test
    fun givenMLSIsConfiguredAndAMLSTeam_whenInvokingIsFederationSearchAllowed_thenReturnTrue() = runTest {
        val (arrangement, isFederationSearchAllowedUseCase) = Arrangement()
            .withMLSConfiguredForBackend(isConfigured = true)
            .withDefaultProtocol(SupportedProtocol.MLS)
            .arrange()

        val isAllowed = isFederationSearchAllowedUseCase(conversationId = null)

        assertEquals(true, isAllowed)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsPublicKeysRepository.getKeys() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.getDefaultProtocol.invoke() }
        verifySuspend(VerifyMode.not) { arrangement.getConversationProtocolInfo.invoke(any()) }
    }

    @Test
    fun givenMLSIsConfiguredAndAMLSTeamAndProteusProtocol_whenInvokingIsFederationSearchAllowed_thenReturnFalse() = runTest {
        val (arrangement, isFederationSearchAllowedUseCase) = Arrangement()
            .withMLSConfiguredForBackend(isConfigured = true)
            .withDefaultProtocol(SupportedProtocol.MLS)
            .withConversationProtocolInfo(GetConversationProtocolInfoUseCase.Result.Success(PROTEUS_PROTOCOL_INFO))
            .arrange()

        val isAllowed = isFederationSearchAllowedUseCase(conversationId = TestConversation.ID)

        assertEquals(false, isAllowed)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsPublicKeysRepository.getKeys() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.getDefaultProtocol.invoke() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.getConversationProtocolInfo.invoke(any()) }
    }

    @Test
    fun givenMLSIsConfiguredAndAProteusTeamAndProteusProtocol_whenInvokingIsFederationSearchAllowed_thenReturnFalse() = runTest {
        val (arrangement, isFederationSearchAllowedUseCase) = Arrangement()
            .withMLSConfiguredForBackend(isConfigured = true)
            .withDefaultProtocol(SupportedProtocol.PROTEUS)
            .withConversationProtocolInfo(GetConversationProtocolInfoUseCase.Result.Success(PROTEUS_PROTOCOL_INFO))
            .arrange()

        val isAllowed = isFederationSearchAllowedUseCase(conversationId = TestConversation.ID)

        assertEquals(false, isAllowed)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsPublicKeysRepository.getKeys() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.getDefaultProtocol.invoke() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.getConversationProtocolInfo.invoke(any()) }
    }

    private class Arrangement {

        val mlsPublicKeysRepository = mock<MLSPublicKeysRepository>(mode = MockMode.autoUnit)
        val getDefaultProtocol = mock<GetDefaultProtocolUseCase>(mode = MockMode.autoUnit)
        val getConversationProtocolInfo = mock<GetConversationProtocolInfoUseCase>(mode = MockMode.autoUnit)

        private val MLS_PUBLIC_KEY = MLSPublicKeys(
            removal = mapOf(
                "ed25519" to "gRNvFYReriXbzsGu7zXiPtS8kaTvhU1gUJEV9rdFHVw="
            )
        )

        suspend fun withDefaultProtocol(protocol: SupportedProtocol) = apply {
            everySuspend { getDefaultProtocol.invoke() } returns protocol
        }

        suspend fun withConversationProtocolInfo(protocolInfo: GetConversationProtocolInfoUseCase.Result) = apply {
            everySuspend { getConversationProtocolInfo.invoke(any()) } returns protocolInfo
        }

        suspend fun withMLSConfiguredForBackend(isConfigured: Boolean = true) = apply {
            everySuspend { mlsPublicKeysRepository.getKeys() } returns (
                if (isConfigured) {
                    Either.Right(MLS_PUBLIC_KEY)
                } else {
                    Either.Left(CoreFailure.Unknown(RuntimeException("MLS is not configured")))
                }
            )
        }

        suspend fun withEmptyMlsKeys() = apply {
            everySuspend { mlsPublicKeysRepository.getKeys() } returns Either.Right(MLSPublicKeys(emptyMap()))
        }

        fun arrange() = this to IsFederationSearchAllowedUseCase(
            mlsPublicKeysRepository = mlsPublicKeysRepository,
            getDefaultProtocol = getDefaultProtocol,
            getConversationProtocolInfo = getConversationProtocolInfo,
            dispatcher = KaliumDispatcherImpl
        )
    }
}

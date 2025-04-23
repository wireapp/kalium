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

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.feature.conversation.GetConversationProtocolInfoUseCase
import com.wire.kalium.logic.feature.user.GetDefaultProtocolUseCase
import com.wire.kalium.common.functional.Either
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Check if FederatedSearchIsAllowed according to MLS configuration of the backend
 * and the conversation  protocol if a [ConversationId] is provided.
 */
interface IsFederationSearchAllowedUseCase {
    suspend operator fun invoke(conversationId: ConversationId?): Boolean
}

@Suppress("FunctionNaming")
internal fun IsFederationSearchAllowedUseCase(
    mlsPublicKeysRepository: MLSPublicKeysRepository,
    getDefaultProtocol: GetDefaultProtocolUseCase,
    getConversationProtocolInfo: GetConversationProtocolInfoUseCase,
    dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) = object : IsFederationSearchAllowedUseCase {

    override suspend operator fun invoke(conversationId: ConversationId?): Boolean = withContext(dispatcher.io) {
        val isMlsConfiguredForBackend = hasMLSKeysConfiguredForBackend()
        when (isMlsConfiguredForBackend) {
            true -> isConversationProtocolAbleToFederate(conversationId)
            false -> true
        }
    }

    private suspend fun hasMLSKeysConfiguredForBackend(): Boolean {
        return when (val mlsKeysResult = mlsPublicKeysRepository.getKeys()) {
            is Either.Left -> false
            is Either.Right -> {
                val mlsKeys: MLSPublicKeys = mlsKeysResult.value
                mlsKeys.removal != null && mlsKeys.removal?.isNotEmpty() == true
            }
        }
    }

    /**
     * MLS is enabled, then we need to check if the protocol for the conversation is able to federate.
     */
    private suspend fun isConversationProtocolAbleToFederate(conversationId: ConversationId?): Boolean {
        val isProteusTeam = getDefaultProtocol() == SupportedProtocol.PROTEUS
        val isOtherDomainAllowed: Boolean = conversationId?.let {
            when (val result = getConversationProtocolInfo(it)) {
                is GetConversationProtocolInfoUseCase.Result.Failure -> !isProteusTeam

                is GetConversationProtocolInfoUseCase.Result.Success ->
                    !isProteusTeam && result.protocolInfo !is Conversation.ProtocolInfo.Proteus
            }
        } ?: !isProteusTeam
        return isOtherDomainAllowed
    }

}

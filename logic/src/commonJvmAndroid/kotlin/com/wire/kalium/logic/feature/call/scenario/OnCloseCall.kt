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

@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.CallClosedReason
import com.wire.kalium.calling.callbacks.CloseCallHandler
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.call.CallMetadata
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCase
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Suppress("LongParameterList")
internal class OnCloseCall(
    private val callRepository: CallRepository,
    private val scope: CoroutineScope,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val networkStateObserver: NetworkStateObserver,
    private val createAndPersistRecentlyEndedCallMetadata: CreateAndPersistRecentlyEndedCallMetadataUseCase
) : CloseCallHandler {
    override fun onClosedCall(
        reason: Int,
        conversationId: String,
        messageTime: Uint32_t,
        userId: String,
        clientId: String?,
        arg: Pointer?
    ) {
        callingLogger.i(
            "[OnCloseCall] -> ConversationId: ${conversationId.obfuscateId()} |" +
                    " UserId: ${userId.obfuscateId()} | Reason: $reason"
        )

        val avsReason = CallClosedReason.fromInt(value = reason)

        val callStatus = getCallStatusFromCloseReason(avsReason)
        val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        scope.launch {
            val callMetadata = callRepository.getCallMetadata(conversationIdWithDomain)
            val isConnectedToInternet = networkStateObserver.observeNetworkState().value == NetworkState.ConnectedWithInternet

            if (isConnectedToInternet && shouldPersistMissedCall(callMetadata, callStatus)) {
                callRepository.persistMissedCall(conversationIdWithDomain)
            }

            val shouldUpdateCallStatus =
                if (callMetadata?.conversationType == Conversation.Type.OneOnOne) {
                    // For 1:1 calls handled as conference calls
                    // Do not switch to STILL_ONGOING or any other status when call was already closed
                    when (callMetadata.callStatus) {
                        CallStatus.MISSED,
                        CallStatus.REJECTED,
                        CallStatus.CLOSED -> false
                        else -> true
                    }
                } else {
                    true
                }

            if (shouldUpdateCallStatus) {
                callRepository.updateCallStatusById(
                    conversationId = conversationIdWithDomain,
                    status = callStatus
                )
            }

            if (callMetadata?.protocol is Conversation.ProtocolInfo.MLS) {
                callRepository.leaveMlsConference(conversationIdWithDomain)
            }
            callingLogger.i("[OnCloseCall] -> ConversationId: ${conversationId.obfuscateId()} | callStatus: $callStatus")
        }

        scope.launch {
            createAndPersistRecentlyEndedCallMetadata(conversationIdWithDomain, reason)
        }
    }

    private fun shouldPersistMissedCall(callMetadata: CallMetadata?, callStatus: CallStatus): Boolean =
        when (callStatus) {
            CallStatus.MISSED -> true
            CallStatus.CLOSED -> callMetadata?.callStatus?.let { currentCallStatus ->
                callMetadata.establishedTime.isNullOrEmpty() &&
                        currentCallStatus != CallStatus.CLOSED_INTERNALLY &&
                        currentCallStatus != CallStatus.REJECTED &&
                        currentCallStatus != CallStatus.STARTED
            } ?: false

            else -> false
        }.also {
            callingLogger.i(
                "[OnCloseCall] -> shouldPersistMissedCall: $it (callStatus: $callStatus, " +
                        "establishedTime: ${callMetadata?.establishedTime}, currentCallStatus: ${callMetadata?.callStatus})"
            )
        }

    private fun getCallStatusFromCloseReason(reason: CallClosedReason): CallStatus = when (reason) {
        CallClosedReason.STILL_ONGOING -> CallStatus.STILL_ONGOING
        CallClosedReason.CANCELLED -> CallStatus.MISSED
        CallClosedReason.REJECTED -> CallStatus.REJECTED
        else -> CallStatus.CLOSED
    }
}

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
package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.common.error.StorageFailure

sealed class MLSMessageFailureResolution {
    data object Ignore : MLSMessageFailureResolution()
    data object InformUser : MLSMessageFailureResolution()
    data object OutOfSync : MLSMessageFailureResolution()
}

internal object MLSMessageFailureHandler {
    fun handleFailure(failure: CoreFailure): MLSMessageFailureResolution {
        return when (failure) {
            // Received messages targeting a future epoch (outside epoch bounds), we might have lost messages.
            is MLSFailure.WrongEpoch -> MLSMessageFailureResolution.OutOfSync

            // Received already sent or received message, can safely be ignored.
            is MLSFailure.DuplicateMessage,
                // Received message was targeting a future epoch and been buffered, can safely be ignored.
            is MLSFailure.BufferedFutureMessage,
                // Received self commit, any unmerged group has know been when merged by CoreCrypto.
            is MLSFailure.SelfCommitIgnored,
                // Message arrive in an unmerged group, it has been buffered and will be consumed later.
            is MLSFailure.UnmergedPendingGroup,
            is MLSFailure.StaleProposal,
            is MLSFailure.StaleCommit,
            is MLSFailure.MessageEpochTooOld,
            is MLSFailure.InternalErrors,
            is MLSFailure.Disabled,
            MLSFailure.CommitForMissingProposal,
            MLSFailure.ConversationNotFound,
            MLSFailure.BufferedCommit,
            is MLSFailure.MessageRejected, // TODO should be ignored?
            MLSFailure.OrphanWelcome,
            is CoreFailure.DevelopmentAPINotAllowedOnProduction -> MLSMessageFailureResolution.Ignore

            MLSFailure.ConversationAlreadyExists,
            MLSFailure.ConversationDoesNotSupportMLS,
            is MLSFailure.Generic,
            is MLSFailure.Other,
            is E2EIFailure,
            is CoreFailure.FeatureFailure,
            CoreFailure.MissingClientRegistration,
            is CoreFailure.MissingKeyPackages,
            NetworkFailure.FeatureNotSupported,
            is NetworkFailure.FederatedBackendFailure.ConflictingBackends,
            is NetworkFailure.FederatedBackendFailure.FailedDomains,
            is NetworkFailure.FederatedBackendFailure.FederationDenied,
            is NetworkFailure.FederatedBackendFailure.FederationNotEnabled,
            is NetworkFailure.FederatedBackendFailure.General,
            is NetworkFailure.NoNetworkConnection,
            is NetworkFailure.ProxyError,
            is NetworkFailure.ServerMiscommunication,
            is ProteusFailure,
            StorageFailure.DataNotFound,
            is StorageFailure.Generic,
            is CoreFailure.Unknown -> MLSMessageFailureResolution.InformUser

        }
    }
}

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

package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.common.error.BackupFailure
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.network.api.model.MLSErrorResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class MLSMessageFailureHandlerTest {

    @Test
    fun givenOutOfSyncMLSFailures_whenHandling_thenReturnOutOfSync() {
        listOf(
            MLSFailure.WrongEpoch,
            MLSFailure.InvalidGroupId,
        ).forEach { failure ->
            assertResolution(failure, MLSMessageFailureResolution.OutOfSync)
        }
    }

    @Test
    fun givenIgnorableMLSAndCoreFailures_whenHandling_thenReturnIgnore() {
        listOf(
            MLSFailure.DuplicateMessage,
            MLSFailure.BufferedFutureMessage,
            MLSFailure.SelfCommitIgnored,
            MLSFailure.UnmergedPendingGroup,
            MLSFailure.StaleProposal,
            MLSFailure.StaleCommit,
            MLSFailure.MessageEpochTooOld,
            MLSFailure.InternalErrors,
            MLSFailure.Disabled,
            MLSFailure.CommitForMissingProposal,
            MLSFailure.ConversationNotFound,
            MLSFailure.BufferedCommit,
            MLSFailure.OrphanWelcome,
            CoreFailure.DevelopmentAPINotAllowedOnProduction,
            BackupFailure.NoCryptoStateAvailable,
        ).forEach { failure ->
            assertResolution(failure, MLSMessageFailureResolution.Ignore)
        }
    }

    @Test
    fun givenUserVisibleMLSAndCoreFailures_whenHandling_thenReturnInformUser() {
        listOf(
            MLSFailure.ConversationAlreadyExists,
            MLSFailure.ConversationDoesNotSupportMLS,
            MLSFailure.Generic(IllegalStateException("generic")),
            MLSFailure.Other("other"),
            E2EIFailure.Disabled,
            CoreFailure.InvalidEventSenderID,
            CoreFailure.MissingClientRegistration,
            CoreFailure.MissingKeyPackages(emptySet()),
            NetworkFailure.FeatureNotSupported,
            NetworkFailure.FederatedBackendFailure.ConflictingBackends(emptyList()),
            NetworkFailure.FederatedBackendFailure.ConflictingBackendsWithMissingUsers(emptyList()),
            NetworkFailure.FederatedBackendFailure.FailedDomains(),
            NetworkFailure.FederatedBackendFailure.FederationDenied("denied"),
            NetworkFailure.FederatedBackendFailure.FederationNotEnabled("disabled"),
            NetworkFailure.FederatedBackendFailure.FederationNotImplemented("not-implemented"),
            NetworkFailure.FederatedBackendFailure.General("general"),
            NetworkFailure.NoNetworkConnection(null),
            NetworkFailure.ProxyError(null),
            NetworkFailure.ServerMiscommunication(IllegalStateException("server")),
            StorageFailure.DataNotFound,
            StorageFailure.Generic(IllegalStateException("storage")),
            CoreFailure.Unknown(null),
        ).forEach { failure ->
            assertResolution(failure, MLSMessageFailureResolution.InformUser)
        }
    }

    @Test
    fun givenResetRejectionsDirectlyOrWrapped_whenHandling_thenReturnResetConversation() {
        listOf(
            NetworkFailure.MlsMessageRejectedFailure.GroupOutOfSync(emptyList()),
            NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeIndex,
            NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeSignature,
        ).forEach { failure ->
            assertRejectedResolution(failure, MLSMessageFailureResolution.ResetConversation)
        }
    }

    @Test
    fun givenUserVisibleRejectionsDirectlyOrWrapped_whenHandling_thenReturnInformUser() {
        listOf(
            NetworkFailure.MlsMessageRejectedFailure.ClientMismatch,
            NetworkFailure.MlsMessageRejectedFailure.CommitMissingReferences,
            NetworkFailure.MlsMessageRejectedFailure.MissingGroupInfo,
            NetworkFailure.MlsMessageRejectedFailure.Other(MLSErrorResponse.ProtocolError("other")),
            NetworkFailure.MlsMessageRejectedFailure.StaleMessage,
        ).forEach { failure ->
            assertRejectedResolution(failure, MLSMessageFailureResolution.InformUser)
        }
    }

    private fun assertRejectedResolution(
        failure: NetworkFailure.MlsMessageRejectedFailure,
        expected: MLSMessageFailureResolution,
    ) {
        assertResolution(failure, expected)
        assertResolution(MLSFailure.MessageRejected(failure), expected)
    }

    private fun assertResolution(failure: CoreFailure, expected: MLSMessageFailureResolution) {
        assertEquals(expected, MLSMessageFailureHandler.handleFailure(failure), "Unexpected resolution for $failure")
    }
}

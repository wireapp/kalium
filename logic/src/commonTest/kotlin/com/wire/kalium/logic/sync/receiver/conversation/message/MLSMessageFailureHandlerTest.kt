/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.common.error.BackupFailure
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.api.model.MLSErrorResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class MLSMessageFailureHandlerTest {

    @Test
    fun givenRejectedFailureThatRequiresReset_whenHandling_thenResetConversation() {
        val failures = listOf(
            NetworkFailure.MlsMessageRejectedFailure.GroupOutOfSync(listOf(TestUser.OTHER_USER_ID)),
            NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeIndex,
            NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeSignature,
        )

        failures.forEach {
            assertEquals(MLSMessageFailureResolution.ResetConversation, MLSMessageFailureHandler.handleFailure(it))
        }
    }

    @Test
    fun givenRejectedFailureThatCanBeShown_whenHandling_thenInformUser() {
        val failures = listOf(
            NetworkFailure.MlsMessageRejectedFailure.ClientMismatch,
            NetworkFailure.MlsMessageRejectedFailure.CommitMissingReferences,
            NetworkFailure.MlsMessageRejectedFailure.MissingGroupInfo,
            NetworkFailure.MlsMessageRejectedFailure.Other(MLSErrorResponse.ProtocolError("error")),
            NetworkFailure.MlsMessageRejectedFailure.StaleMessage,
        )

        failures.forEach {
            assertEquals(MLSMessageFailureResolution.InformUser, MLSMessageFailureHandler.handleFailure(it))
        }
    }

    @Test
    fun givenWrappedRejectedFailure_whenHandling_thenUseRejectedFailureResolution() {
        val failure = MLSFailure.MessageRejected(NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeSignature)

        assertEquals(MLSMessageFailureResolution.ResetConversation, MLSMessageFailureHandler.handleFailure(failure))
    }

    @Test
    fun givenEpochFailure_whenHandling_thenMarkConversationOutOfSync() {
        assertFailureResolutions(
            failures = listOf(MLSFailure.WrongEpoch, MLSFailure.InvalidGroupId),
            expected = MLSMessageFailureResolution.OutOfSync,
        )
    }

    @Test
    fun givenIgnorableMlsOrBackupFailure_whenHandling_thenIgnore() {
        assertFailureResolutions(
            failures = listOf(
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
            ),
            expected = MLSMessageFailureResolution.Ignore,
        )
    }

    @Test
    fun givenNonRecoverableFailure_whenHandling_thenInformUser() {
        assertFailureResolutions(
            failures = listOf(
                MLSFailure.ConversationAlreadyExists,
                MLSFailure.ConversationDoesNotSupportMLS,
                MLSFailure.FederatedBackendConflict(emptyList()),
                MLSFailure.Generic(IllegalStateException("generic MLS failure")),
                MLSFailure.Other("other MLS failure"),
                E2EIFailure.Disabled,
                CoreFailure.NotSupportedByProteus,
                CoreFailure.MissingClientRegistration,
                CoreFailure.MissingKeyPackages(emptySet()),
                NetworkFailure.FeatureNotSupported,
                NetworkFailure.FederatedBackendFailure.ConflictingBackends(emptyList()),
                NetworkFailure.FederatedBackendFailure.FailedDomains(),
                NetworkFailure.FederatedBackendFailure.FederationDenied("denied"),
                NetworkFailure.FederatedBackendFailure.FederationNotEnabled("disabled"),
                NetworkFailure.FederatedBackendFailure.FederationNotImplemented("unsupported"),
                NetworkFailure.FederatedBackendFailure.General("general"),
                NetworkFailure.NoNetworkConnection(null),
                NetworkFailure.ProxyError(null),
                NetworkFailure.ServerMiscommunication(IllegalStateException("server")),
                ProteusFailure(ProteusException("proteus", ProteusException.Code.INVALID_MESSAGE, 1)),
                StorageFailure.DataNotFound,
                StorageFailure.Generic(IllegalStateException("storage")),
                CoreFailure.Unknown(null),
            ),
            expected = MLSMessageFailureResolution.InformUser,
        )
    }

    private fun assertFailureResolutions(
        failures: List<CoreFailure>,
        expected: MLSMessageFailureResolution,
    ) {
        failures.forEach { failure ->
            assertEquals(expected, MLSMessageFailureHandler.handleFailure(failure), "Unexpected resolution for $failure")
        }
    }
}

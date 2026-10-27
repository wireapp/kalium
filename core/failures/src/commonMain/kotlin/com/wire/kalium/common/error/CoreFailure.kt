/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.common.error

import com.wire.kalium.logic.data.user.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface CoreFailure {

    val isInvalidRequestError: Boolean
        get() = this is NetworkFailure.ServerMiscommunication
                && this.reason is ServerError.InvalidRequest

    val hasUnreachableDomainsError: Boolean
        get() = this is NetworkFailure.FederatedBackendFailure.FailedDomains && this.domains.isNotEmpty()

    val hasConflictingDomainsError: Boolean
        get() = this is NetworkFailure.FederatedBackendFailure.ConflictingBackends && this.domains.isNotEmpty()

    val isRetryable: Boolean
        get() = when {
            this is NetworkFailure.FederatedBackendFailure.RetryableFailure -> true
            isMissingLegalHoldConsentError -> true
            else -> false
        }

    val isMissingLegalHoldConsentError: Boolean
        get() = this is NetworkFailure.ServerMiscommunication
                && this.reason is ServerError.InvalidRequest
                && this.reason.isMissingLegalHoldConsent()

    /**
     * The attempted operation requires that this client is registered.
     */
    data object MissingClientRegistration : CoreFailure

    /**
     * Represents a failure indicating that key packages are missing for user IDs.
     *
     * @property failedUserIds The set of user IDs for which key packages are missing.
     */
    data class MissingKeyPackages(
        val failedUserIds: Set<UserId>
    ) : CoreFailure

    /**
     * It's not allowed to run the application with development API enabled when
     * connecting to the production environment.
     */
    data object DevelopmentAPINotAllowedOnProduction : CoreFailure

    data class Unknown(val rootCause: Throwable?) : CoreFailure

    abstract class FeatureFailure : CoreFailure

    /**
     * It's only allowed to insert system messages as bulk for all conversations.
     */
    data object OnlySystemMessageAllowed : FeatureFailure()

    /**
     * The sender ID of the event is invalid.
     * usually happens with events that alter a message state [ButtonActionConfirmation]
     * when the sender ID is not the same are the original message sender id
     */
    data object InvalidEventSenderID : FeatureFailure()

    /**
     * This operation is not supported by proteus conversations
     */
    data object NotSupportedByProteus : FeatureFailure()

    /**
     * The desired event was not found when fetching pending events.
     * This can happen when this client has been offline for a long period of time,
     * and the backend has deleted old events.
     *
     * This is a recoverable error, the client should:
     * - Do a full slow sync
     * - Try incremental sync again using the oldest event ID available in the backend
     * - Warn the user that some events might have been missed.
     *
     * This could also mean that the client was deleted. In this case, SlowSync will fail.
     * The client should identify this scenario through other means and logout.
     */
    data object SyncEventOrClientNotFound : FeatureFailure()

    /**
     * No common Protocol found in order to establish a conversation between parties.
     * Could be, for example, that the desired user only supports Proteus, but we only support MLS.
     */
    sealed class NoCommonProtocolFound : FeatureFailure() {
        /**
         * SelfClient needs to update to support MLS
         */
        data object SelfNeedToUpdate : NoCommonProtocolFound()

        /**
         * Other User needs to update to support MLS
         */
        data object OtherNeedToUpdate : NoCommonProtocolFound()
    }
}

sealed class NetworkFailure : CoreFailure {
    /**
     * Failed to establish a connection with the necessary servers in order to pull/push data.
     *
     * Caused by weak – or complete lack of – internet connection:
     * - Device not connected at all
     * - Timeout due to slow connection
     * - Server is offline
     * - Unreachable due to ISP blocks
     * - _many_ others, we just can't say specifically, and we handle them all the same way
     *
     * [cause] can help to understand better what was caused and triggered this failure
     */
    class NoNetworkConnection(val cause: Throwable?) : NetworkFailure()

    class ProxyError(val cause: Throwable?) : NetworkFailure()

    /**
     * Server internal error, or we can't parse the response,
     * or anything API-related that is out of control from the user.
     * Either fix our app or our backend.
     */
    class ServerMiscommunication(
        val reason: ServerError,
        val rootCause: Throwable? = null
    ) : NetworkFailure() {
        override fun toString(): String = "ServerMiscommunication(reason = $reason, cause = $rootCause)"
    }

    /**
     * Failure due to a federated backend context
     */
    sealed class FederatedBackendFailure : NetworkFailure() {

        /**
         * Failure due to a federated backend context that can be retried
         */
        interface RetryableFailure {
            val domains: List<String>
        }

        data class General(val label: String) : FederatedBackendFailure()
        data class FederationDenied(val label: String) : FederatedBackendFailure()
        data class FederationNotEnabled(val label: String) : FederatedBackendFailure()
        data class FederationNotImplemented(val label: String) : FederatedBackendFailure()

        data class ConflictingBackends(override val domains: List<String>) : FederatedBackendFailure(), RetryableFailure

        data class FailedDomains(override val domains: List<String> = emptyList()) : FederatedBackendFailure(), RetryableFailure

    }

    /**
     * Result of requests rejected by the distribution service (API).
     */
    @Serializable
    sealed class MlsMessageRejectedFailure : NetworkFailure() {
        @Serializable
        data object ClientMismatch : MlsMessageRejectedFailure()

        @Serializable
        data object CommitMissingReferences : MlsMessageRejectedFailure()

        @Serializable
        data object StaleMessage : MlsMessageRejectedFailure()

        @Serializable
        data object MissingGroupInfo : MlsMessageRejectedFailure()

        @Serializable
        data object InvalidLeafNodeIndex : MlsMessageRejectedFailure()

        @Serializable
        data object InvalidLeafNodeSignature : MlsMessageRejectedFailure()

        @Serializable
        data class Other(@SerialName("remoteCause") val remoteCause: MlsErrorDetails) : MlsMessageRejectedFailure()

        @Serializable
        data class GroupOutOfSync(@SerialName("missingUsers") val missingUsers: List<UserId>) : MlsMessageRejectedFailure()
    }

    /**
     * Failure due to a feature not supported by the current client/backend.
     */
    data object FeatureNotSupported : NetworkFailure()
}

sealed interface MLSFailure : CoreFailure {

    data object WrongEpoch : MLSFailure

    data object DuplicateMessage : MLSFailure

    data object BufferedFutureMessage : MLSFailure

    data object SelfCommitIgnored : MLSFailure

    data object UnmergedPendingGroup : MLSFailure

    data object ConversationAlreadyExists : MLSFailure
    data object MessageEpochTooOld : MLSFailure
    data object ConversationDoesNotSupportMLS : MLSFailure
    data object StaleProposal : MLSFailure
    data object StaleCommit : MLSFailure
    data object InternalErrors : MLSFailure
    data object Disabled : MLSFailure
    data object CommitForMissingProposal : MLSFailure
    data object ConversationNotFound : MLSFailure
    data object OrphanWelcome : MLSFailure
    data object BufferedCommit : MLSFailure
    data object InvalidGroupId : MLSFailure

    data class MessageRejected(val cause: NetworkFailure.MlsMessageRejectedFailure) : MLSFailure
    data class FederatedBackendConflict(val domains: List<String>) : MLSFailure

    data class Other(val message: String) : MLSFailure
    data class Generic(val rootCause: Throwable) : MLSFailure
}

fun CoreFailure.wrapNetworkMlsFailureIfApplicable(): CoreFailure = if (this is NetworkFailure.MlsMessageRejectedFailure) {
    MLSFailure.MessageRejected(this)
} else this

fun CoreFailure.normalizeFederatedBackendConflict(): CoreFailure =
    if (this is MLSFailure.FederatedBackendConflict) {
        NetworkFailure.FederatedBackendFailure.ConflictingBackends(domains)
    } else {
        this
    }

interface E2EIFailure : CoreFailure {
    data object Disabled : E2EIFailure
    data object MissingDiscoveryUrl : E2EIFailure
    data class MissingMLSClient(internal val reason: CoreFailure) : E2EIFailure
    data class MissingE2EIClient(internal val reason: CoreFailure) : E2EIFailure
    data object MissingTeamSettings : E2EIFailure
    data class GettingE2EIClient(internal val reason: CoreFailure) : E2EIFailure
    data class TrustAnchors(internal val reason: CoreFailure) : E2EIFailure
    data class IntermediateCert(internal val reason: CoreFailure) : E2EIFailure
    data class CRL(internal val reason: CoreFailure) : E2EIFailure
    data class OAuthRefreshToken(internal val reason: CoreFailure) : E2EIFailure
    data class AcmeNonce(internal val reason: CoreFailure) : E2EIFailure
    data class AcmeNewAccount(internal val reason: CoreFailure) : E2EIFailure
    data class AcmeDirectories(internal val reason: CoreFailure) : E2EIFailure
    data class AcmeNewOrder(internal val reason: CoreFailure) : E2EIFailure
    data object AcmeAuthorizations : E2EIFailure
    data class OAuth(val reason: String) : E2EIFailure
    data class WireNonce(internal val reason: CoreFailure) : E2EIFailure
    data class DPoPToken(internal val reason: CoreFailure) : E2EIFailure
    data class WireAccessToken(internal val reason: CoreFailure) : E2EIFailure
    data class DPoPChallenge(internal val reason: CoreFailure) : E2EIFailure
    data class OIDCChallenge(internal val reason: CoreFailure) : E2EIFailure

    // returned as success from ChallengeResponse with status=invalid when user tries to login with different credentials
    object InvalidChallenge : E2EIFailure
    data class CheckOrderRequest(internal val reason: CoreFailure) : E2EIFailure
    data class FinalizeRequest(internal val reason: CoreFailure) : E2EIFailure
    data class RotationAndMigration(internal val reason: CoreFailure) : E2EIFailure
    data class InitMLSClient(internal val reason: CoreFailure) : E2EIFailure
    data class Certificate(internal val reason: CoreFailure) : E2EIFailure
    class Generic(internal val exception: Exception) : E2EIFailure {
        val rootCause: Throwable get() = exception
    }
}

/** Stable error classification; [rootCause] is retained only for diagnostics. */
class ProteusFailure(
    val code: Code,
    val intCode: Int? = null,
    val rootCause: Throwable? = null
) : CoreFailure {
    enum class Code {
        /**
         * A requested session was not found.
         */
        SESSION_NOT_FOUND,

        /**
         * The remote identity of a session changed.
         *
         *
         * Usually the user should be informed and the session reinitialised.
         * If the remote fingerprint was previously verified, it will need to be
         * verified anew in order to exclude any potential MITM.
         */
        REMOTE_IDENTITY_CHANGED,

        /**
         * The signature of a decrypted message is invalid.
         *
         *
         * The message being decrypted is incomplete or has otherwise been
         * tampered with.
         */
        INVALID_SIGNATURE,

        /**
         * A message is invalid.
         *
         *
         * The message is well-formed but cannot be decrypted, e.g.
         * because the message is used to initialise a session but does not
         * contain a a pre-key or the used session does not contain the
         * appropriate key material for decrypting the message. The problem
         * should be reported to the user, as it might be necessary for both
         * peers to re-initialise their sessions.
         */
        INVALID_MESSAGE,

        /**
         * A message is a duplicate.
         *
         *
         * The message being decrypted is a duplicate of a message that has
         * previously been decrypted with the same session. The message can
         * be safely discarded.
         */
        DUPLICATE_MESSAGE,

        /**
         * A message is too recent.
         *
         *
         * There is an unreasonably large gap between the last decrypted
         * message and the message being decrypted, i.e. there are too many
         * intermediate messages missing. The message should be dropped.
         */
        TOO_DISTANT_FUTURE,

        /**
         * A message is too old.
         *
         *
         * The message being decrypted is unreasonably old and cannot
         * be decrypted any longer due to the key material no longer being available.
         * The message should be dropped.
         */
        OUTDATED_MESSAGE,

        /**
         * A message or key could not be decoded.
         *
         *
         * The message or key being decoded is either malformed or
         * otherwise encoded in a way such it cannot be understood.
         */
        DECODE_ERROR,

        /**
         * An internal storage error occurred.
         *
         *
         * An error occurred while loading or persisting key material.
         * The operation may be retried a limited number of times.
         */
        STORAGE_ERROR,

        /**
         * A CBox has been opened with an incomplete or mismatching identity
         * using opening a crypto box.
         *
         *
         * This is typically a programmer error.
         */
        IDENTITY_ERROR,

        /**
         * An attempt was made to initialise a new session using session initialization
         * whereby the prekey corresponding to the prekey ID in the message could not be found.
         */
        PREKEY_NOT_FOUND,

        /**
         * A panic occurred. This is a last resort error raised form native code to
         * signal a severe problem, like a violation of a critical invariant, that
         * would otherwise have caused a crash. Client code can choose to handle
         * these errors more gracefully, preventing the application from crashing.
         *
         *
         * Note that any crypto sessions which might have been involved in a
         * computation leading to a panic must no longer be used as their in-memory
         * state may be corrupt. Such sessions should be closed and may be subsequently
         * reloaded to retry the operation(s).
         */
        PANIC,

        /**
         * An unspecified error occurred.
         */
        UNKNOWN_ERROR,

        /**
         * Local files were not found.
         */
        LOCAL_FILES_NOT_FOUND
    }
}

sealed class EncryptionFailure : CoreFailure.FeatureFailure() {
    data object GenericEncryptionError : EncryptionFailure()
    data object GenericDecryptionError : EncryptionFailure()
    data object WrongAssetHash : EncryptionFailure()
}

sealed class StorageFailure : CoreFailure {
    data object DataNotFound : StorageFailure()
    data class Generic(val rootCause: Throwable) : StorageFailure() {
        override fun toString(): String {
            return "Generic(rootCause = ${rootCause.stackTraceToString()})"
        }
    }
}

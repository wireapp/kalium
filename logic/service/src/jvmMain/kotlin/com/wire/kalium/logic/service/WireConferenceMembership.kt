@file:OptIn(
    com.wire.kalium.calling.runtime.ExperimentalCallingRuntimeApi::class,
    com.wire.kalium.conversation.ExperimentalConversationApi::class,
    com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi::class,
)
@file:Suppress("TooGenericExceptionCaught")

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
 */

package com.wire.kalium.logic.service

import com.wire.kalium.calling.runtime.ActiveCall
import com.wire.kalium.calling.runtime.CallEpoch
import com.wire.kalium.calling.runtime.CallEpochSecret
import com.wire.kalium.calling.runtime.CallingFailure
import com.wire.kalium.calling.runtime.CallingResult
import com.wire.kalium.calling.runtime.ConferenceMembership
import com.wire.kalium.conversation.CallClient
import com.wire.kalium.conversation.CallConversationContext
import com.wire.kalium.conversation.CallConversationProtocol
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi
import com.wire.kalium.network.api.authenticated.conversation.SubconversationDeleteRequest
import com.wire.kalium.network.api.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.model.QualifiedID as NetworkQualifiedId
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isAccessDenied
import com.wire.kalium.network.exceptions.isNotFound
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Handles newly discovered certificate-revocation distribution points before a call is joined. */
@ExperimentalKaliumServiceApi
public fun interface ServiceCrlDistributionPointHandler {
    public suspend fun handle(distributionPoints: List<String>): CallingResult
}

/** Durable parent/subgroup mapping needed for cleanup after restart or conversation access loss. */
@ExperimentalKaliumServiceApi
public data class ConferenceProtocolState(
    public val conversationId: ConversationId,
    public val parentGroupId: String,
    public val subgroupId: String,
)

/**
 * Durable join intent and CRL work created before a conference join may mutate CoreCrypto.
 * An empty [distributionPoints] list is still meaningful: the join has not yet recorded whether
 * crypto mutation produced CRL points, so restart reconciliation must clean it rather than admit it.
 */
@ExperimentalKaliumServiceApi
public data class PendingConferenceCrlState(
    public val conference: ConferenceProtocolState,
    public val distributionPoints: List<String>,
)

@ExperimentalKaliumServiceApi
public sealed interface ConferenceProtocolStateResult<out Value> {
    public data class Success<Value>(public val value: Value) : ConferenceProtocolStateResult<Value>

    public data class Failure(public val description: String, public val cause: Throwable? = null) :
        ConferenceProtocolStateResult<Nothing>
}

@ExperimentalKaliumServiceApi
public interface ConferenceProtocolStateStore {
    public suspend fun loadConference(conversationId: ConversationId): ConferenceProtocolStateResult<ConferenceProtocolState?>

    public suspend fun loadConferences(): ConferenceProtocolStateResult<List<ConferenceProtocolState>>

    public suspend fun saveConference(state: ConferenceProtocolState): ConferenceProtocolStateResult<Unit>

    public suspend fun removeConference(conversationId: ConversationId): ConferenceProtocolStateResult<Unit>

    public suspend fun loadPendingConferenceCrls(): ConferenceProtocolStateResult<List<PendingConferenceCrlState>>

    public suspend fun loadPendingConferenceCrl(
        conversationId: ConversationId,
    ): ConferenceProtocolStateResult<PendingConferenceCrlState?>

    public suspend fun savePendingConferenceCrl(state: PendingConferenceCrlState): ConferenceProtocolStateResult<Unit>

    public suspend fun removePendingConferenceCrl(conversationId: ConversationId): ConferenceProtocolStateResult<Unit>
}

/**
 * Identity-local MLS conference membership backed only by Wire APIs and durable CoreCrypto state.
 * It owns no conversation or call-history persistence.
 */
@ExperimentalKaliumServiceApi
@Suppress("CyclomaticComplexMethod", "LongMethod", "LongParameterList", "TooManyFunctions")
public class WireConferenceMembership(
    private val conversationApi: ConversationApi,
    private val cryptoRuntime: WireServiceCryptoRuntime,
    private val epochBus: WireMlsEpochBus,
    private val stateStore: ConferenceProtocolStateStore,
    private val crlHandler: ServiceCrlDistributionPointHandler,
    private val pendingProposalScheduler: WirePendingMlsCommitScheduler,
    private val now: () -> Instant = Instant::now,
) : ConferenceMembership {
    private val memberships = ConcurrentHashMap<ConversationId, Membership>()
    private val reconciliationMutex = Mutex()
    private val membershipMutex = Mutex()

    @Volatile
    private var startupReconciled = false

    override suspend fun join(context: CallConversationContext): CallingResult {
        when (val reconciled = reconcileStartup()) {
            is CallingResult.Failure -> return reconciled
            CallingResult.Success -> Unit
        }
        return membershipMutex.withLock { joinLocked(context) }
    }

    private suspend fun joinLocked(context: CallConversationContext): CallingResult = try {
        val parentGroupId = context.protocol.mlsGroupId()
            ?: return CallingResult.Failure(CallingFailure.Crypto("Conference membership requires MLS"))
        val details = when (
            val response = conversationApi.fetchSubconversationDetails(
                context.conversationId.toNetwork(),
                CONFERENCE_SUBCONVERSATION_ID,
            )
        ) {
            is NetworkResponse.Success -> response.value
            is NetworkResponse.Error -> return response.callingFailure("Unable to fetch conference subconversation")
        }
        val conference = ConferenceProtocolState(context.conversationId, parentGroupId, details.groupId)
        var durableConference = loadConferenceState(context.conversationId)
        val pending = loadPendingConferenceCrlState(context.conversationId)

        if (pending != null && pending.conference != conference) {
            cleanupDurableRecords(
                conversationId = context.conversationId,
                records = listOf(pending.conference, durableConference).filterNotNull(),
                leaveBackend = true,
            )
            return CallingResult.Failure(
                CallingFailure.Crypto("Stale pending conference state was cleaned; retry the join"),
            )
        }

        if (pending != null && pending.distributionPoints.isNotEmpty()) {
            when (val result = crlHandler.handle(pending.distributionPoints)) {
                is CallingResult.Failure -> return result
                CallingResult.Success -> Unit
            }
            saveConferenceState(pending.conference)
            removePendingConferenceCrlState(context.conversationId)
            durableConference = pending.conference
        }

        val existsBeforeJoin = cryptoRuntime.withMls("service-conference-check") { mls ->
            mls.conversationExists(conference.subgroupId)
        }
        if (durableConference != null) {
            check(durableConference == conference) { "Durable conference state does not match the Wire subgroup" }
            check(existsBeforeJoin) { "Durable conference state is missing from CoreCrypto" }
            memberships[context.conversationId] = Membership(parentGroupId, conference.subgroupId)
            return CallingResult.Success
        }

        if (pending != null && existsBeforeJoin) {
            cleanupDurableRecords(
                conversationId = context.conversationId,
                records = listOf(pending.conference),
                leaveBackend = true,
            )
            return CallingResult.Failure(
                CallingFailure.Crypto("An interrupted conference join was cleaned; retry the join"),
            )
        }
        check(!existsBeforeJoin) { "CoreCrypto conference state has no durable ownership record" }

        if (pending == null) {
            savePendingConferenceCrlState(PendingConferenceCrlState(conference, emptyList()))
        }
        var newCrlDistributionPoints: List<String> = emptyList()
        cryptoRuntime.withMls("service-conference-join") { mls ->
            check(!mls.conversationExists(conference.subgroupId)) {
                "Conference subgroup appeared after its durable join intent was written"
            }
            if (details.shouldJoinExisting()) {
                val groupInfo = when (
                    val response = conversationApi.fetchSubconversationGroupInfo(
                        details.parentId,
                        details.id,
                    )
                ) {
                    is NetworkResponse.Success -> response.value
                    is NetworkResponse.Error -> throw ConferenceNetworkException(
                        "Unable to fetch conference group info",
                        response.kException,
                    )
                }
                newCrlDistributionPoints = mls.joinByExternalCommit(groupInfo).crlNewDistributionPoints.orEmpty()
            } else {
                if (details.isStale()) {
                    when (
                        val response = conversationApi.deleteSubconversation(
                            details.parentId,
                            details.id,
                            SubconversationDeleteRequest(details.epoch, details.groupId),
                        )
                    ) {
                        is NetworkResponse.Success -> Unit
                        is NetworkResponse.Error -> throw ConferenceNetworkException(
                            "Unable to replace stale conference subconversation",
                            response.kException,
                        )
                    }
                }
                val externalSender = mls.getExternalSenders(parentGroupId)
                mls.createConversation(conference.subgroupId, externalSender.value)
                mls.updateKeyingMaterial(conference.subgroupId)
            }
        }
        if (newCrlDistributionPoints.isNotEmpty()) {
            savePendingConferenceCrlState(PendingConferenceCrlState(conference, newCrlDistributionPoints))
            when (val result = crlHandler.handle(newCrlDistributionPoints)) {
                is CallingResult.Failure -> return result
                CallingResult.Success -> Unit
            }
        }
        saveConferenceState(conference)
        removePendingConferenceCrlState(context.conversationId)
        memberships[context.conversationId] = Membership(parentGroupId, conference.subgroupId)
        CallingResult.Success
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        CallingResult.Failure(CallingFailure.Crypto("Unable to join conference subconversation", failure))
    }

    override suspend fun leave(call: ActiveCall): CallingResult = membershipMutex.withLock {
        try {
            val stored = listOfNotNull(
                loadConferenceState(call.conversationId),
                loadPendingConferenceCrlState(call.conversationId)?.conference,
            )
            val knownMembership = memberships[call.conversationId]
            when (
                val response = conversationApi.leaveSubconversation(
                    call.conversationId.toNetwork(),
                    CONFERENCE_SUBCONVERSATION_ID,
                )
            ) {
                is NetworkResponse.Success -> Unit
                is NetworkResponse.Error -> if (!response.isNotFoundOrAccessDenied()) {
                    return response.callingFailure("Unable to leave conference subconversation")
                }
            }
            val subgroupIds = (stored.map { it.subgroupId } + listOfNotNull(knownMembership?.subgroupId)).distinct()
            if (subgroupIds.isEmpty()) {
                fetchSubgroupId(call.conversationId)?.let { wipeSubgroups(listOf(it), "service-conference-leave") }
            } else {
                wipeSubgroups(subgroupIds, "service-conference-leave")
            }
            memberships.remove(call.conversationId)
            removePendingConferenceCrlState(call.conversationId)
            removeConferenceState(call.conversationId)
            CallingResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            CallingResult.Failure(CallingFailure.Crypto("Unable to leave conference subconversation", failure))
        }
    }

    override fun observeEpochs(conversationId: ConversationId): Flow<CallEpoch> = flow {
        val membership = memberships[conversationId]
            ?: throw IllegalStateException("Conference membership is not joined for $conversationId")
        epochBus.observeGroups(setOf(membership.parentGroupId, membership.subgroupId))
            .collect { emit(createEpoch(membership)) }
    }

    internal suspend fun currentEpoch(conversationId: ConversationId): CallEpoch? = membershipMutex.withLock {
        val membership = memberships[conversationId] ?: loadMembership(conversationId) ?: return@withLock null
        createEpoch(membership)
    }

    /** Advances the joined conference subgroup when AVS requests fresh key material. */
    public suspend fun advanceEpoch(conversationId: ConversationId): CallingResult = membershipMutex.withLock {
        try {
            val subgroupId = (memberships[conversationId] ?: loadMembership(conversationId))?.subgroupId
                ?: fetchSubgroupId(conversationId)
                ?: return CallingResult.Failure(CallingFailure.Crypto("Conference subconversation is not joined"))
            cryptoRuntime.withMls("service-conference-advance-epoch") { mls ->
                if (!mls.conversationExists(subgroupId)) {
                    throw IllegalStateException("Conference subgroup is absent from durable CoreCrypto state")
                }
                mls.updateKeyingMaterial(subgroupId)
            }
            CallingResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            CallingResult.Failure(CallingFailure.Crypto("Unable to advance conference epoch", failure))
        }
    }

    /**
     * Cleans conference joins left by an earlier process before this runtime admits a new call.
     * The composition root should await this after crypto startup and before reporting READY.
     */
    public suspend fun reconcileStartup(): CallingResult = reconciliationMutex.withLock {
        if (startupReconciled) return@withLock CallingResult.Success
        val result = membershipMutex.withLock {
            try {
                val records = loadConferenceStates() + loadPendingConferenceCrlStates().map { it.conference }
                records.groupBy { it.conversationId }.forEach { (conversationId, states) ->
                    cleanupDurableRecords(conversationId, states.distinct(), leaveBackend = true)
                }
                CallingResult.Success
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                CallingResult.Failure(CallingFailure.Crypto("Unable to reconcile conference state after restart", failure))
            }
        }
        if (result == CallingResult.Success) startupReconciled = true
        result
    }

    /**
     * Wipes locally owned subgroup crypto and mappings after conversation access is lost.
     * No backend request is made because the authenticated identity may no longer have access.
     */
    public suspend fun cleanupForAccessLoss(conversationId: ConversationId): CallingResult = membershipMutex.withLock {
        try {
            val records = listOfNotNull(
                loadConferenceState(conversationId),
                loadPendingConferenceCrlState(conversationId)?.conference,
            )
            cleanupDurableRecords(conversationId, records, leaveBackend = false)
            CallingResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            CallingResult.Failure(CallingFailure.Crypto("Unable to clean conference state after access loss", failure))
        }
    }

    private suspend fun cleanupDurableRecords(
        conversationId: ConversationId,
        records: List<ConferenceProtocolState>,
        leaveBackend: Boolean,
    ) {
        if (leaveBackend) {
            when (
                val response = conversationApi.leaveSubconversation(
                    conversationId.toNetwork(),
                    CONFERENCE_SUBCONVERSATION_ID,
                )
            ) {
                is NetworkResponse.Success -> Unit
                is NetworkResponse.Error -> if (!response.isNotFoundOrAccessDenied()) {
                    throw ConferenceNetworkException("Unable to reconcile conference subconversation", response.kException)
                }
            }
        }
        val subgroupIds = (
                records.map { it.subgroupId } +
                        listOfNotNull(memberships[conversationId]?.subgroupId)
                ).distinct()
        wipeSubgroups(subgroupIds, "service-conference-cleanup")
        memberships.remove(conversationId)
        removePendingConferenceCrlState(conversationId)
        removeConferenceState(conversationId)
    }

    private suspend fun wipeSubgroups(subgroupIds: List<String>, transactionName: String) {
        if (subgroupIds.isEmpty()) return
        subgroupIds.forEach { subgroupId ->
            when (val cancelled = pendingProposalScheduler.cancel(subgroupId)) {
                is EncryptedServiceStateResult.Failure ->
                    throw cancelled.cause ?: IllegalStateException(cancelled.description)
                is EncryptedServiceStateResult.Success -> Unit
            }
        }
        cryptoRuntime.withMls(transactionName) { mls ->
            subgroupIds.forEach { subgroupId ->
                if (mls.conversationExists(subgroupId)) mls.wipeConversation(subgroupId)
            }
        }
    }

    private suspend fun loadConferenceState(conversationId: ConversationId): ConferenceProtocolState? =
        stateStore.loadConference(conversationId).valueOrThrow()

    private suspend fun loadConferenceStates(): List<ConferenceProtocolState> =
        stateStore.loadConferences().valueOrThrow()

    private suspend fun loadPendingConferenceCrlState(conversationId: ConversationId): PendingConferenceCrlState? =
        stateStore.loadPendingConferenceCrl(conversationId).valueOrThrow()

    private suspend fun loadPendingConferenceCrlStates(): List<PendingConferenceCrlState> =
        stateStore.loadPendingConferenceCrls().valueOrThrow()

    private suspend fun saveConferenceState(state: ConferenceProtocolState) {
        stateStore.saveConference(state).valueOrThrow()
    }

    private suspend fun removeConferenceState(conversationId: ConversationId) {
        stateStore.removeConference(conversationId).valueOrThrow()
    }

    private suspend fun savePendingConferenceCrlState(state: PendingConferenceCrlState) {
        stateStore.savePendingConferenceCrl(state).valueOrThrow()
    }

    private suspend fun removePendingConferenceCrlState(conversationId: ConversationId) {
        stateStore.removePendingConferenceCrl(conversationId).valueOrThrow()
    }

    private fun <Value> ConferenceProtocolStateResult<Value>.valueOrThrow(): Value = when (this) {
        is ConferenceProtocolStateResult.Failure -> throw cause ?: IllegalStateException(description)
        is ConferenceProtocolStateResult.Success -> value
    }

    private suspend fun createEpoch(membership: Membership): CallEpoch =
        cryptoRuntime.withMls("service-conference-epoch") { mls ->
            val subgroupMembers = mls.members(membership.subgroupId).toSet()
            val clients = mls.members(membership.parentGroupId)
                .sortedWith(compareBy({ it.userId.domain }, { it.userId.value }, CryptoQualifiedClientId::value))
                .map { client ->
                    CallClient(
                        userId = UserId(client.userId.value, client.userId.domain),
                        clientId = client.value,
                        isMemberOfSubconversation = client in subgroupMembers,
                    )
                }
            CallEpoch(
                epoch = mls.conversationEpoch(membership.subgroupId),
                secret = CallEpochSecret.fromBytes(mls.deriveSecret(membership.subgroupId, EPOCH_SECRET_SIZE)),
                clients = clients,
            )
        }

    private suspend fun fetchSubgroupId(conversationId: ConversationId): String? =
        when (
            val response = conversationApi.fetchSubconversationDetails(
                conversationId.toNetwork(),
                CONFERENCE_SUBCONVERSATION_ID,
            )
        ) {
            is NetworkResponse.Success -> response.value.groupId
            is NetworkResponse.Error -> if (response.isNotFound()) null else throw ConferenceNetworkException(
                "Unable to resolve conference subconversation while leaving",
                response.kException,
            )
        }

    private suspend fun loadMembership(conversationId: ConversationId): Membership? = when (
        val result = stateStore.loadConference(conversationId)
    ) {
        is ConferenceProtocolStateResult.Failure -> throw result.cause ?: IllegalStateException(result.description)
        is ConferenceProtocolStateResult.Success -> result.value?.let { Membership(it.parentGroupId, it.subgroupId) }
    }

    private fun SubconversationResponse.shouldJoinExisting(): Boolean = epoch > INITIAL_EPOCH && !isStale()

    private fun SubconversationResponse.isStale(): Boolean {
        val lastChange = epochTimestamp?.let { timestamp ->
            runCatching { Instant.parse(timestamp) }.getOrNull()
        } ?: return false
        return Duration.between(lastChange, now()).toHours() > STALE_EPOCH_HOURS
    }

    private data class Membership(val parentGroupId: String, val subgroupId: String)

    private class ConferenceNetworkException(message: String, cause: Throwable) : RuntimeException(message, cause)

    private companion object {
        const val CONFERENCE_SUBCONVERSATION_ID = "conference"
        const val INITIAL_EPOCH = 0UL
        const val STALE_EPOCH_HOURS = 24
        const val EPOCH_SECRET_SIZE = 32U
    }
}

private fun CallConversationProtocol.mlsGroupId(): String? = when (this) {
    is CallConversationProtocol.Mls -> groupId.value
    is CallConversationProtocol.Mixed -> groupId.value
    CallConversationProtocol.Proteus -> null
}

private fun ConversationId.toNetwork(): NetworkQualifiedId = NetworkQualifiedId(value, domain)

private fun NetworkResponse.Error.isNotFound(): Boolean =
    (kException as? KaliumException.InvalidRequestError)?.isNotFound() == true

private fun NetworkResponse.Error.isNotFoundOrAccessDenied(): Boolean =
    (kException as? KaliumException.InvalidRequestError)?.let { it.isNotFound() || it.isAccessDenied() } == true

private fun NetworkResponse.Error.callingFailure(description: String): CallingResult.Failure =
    CallingResult.Failure(CallingFailure.Transport(description, kException))

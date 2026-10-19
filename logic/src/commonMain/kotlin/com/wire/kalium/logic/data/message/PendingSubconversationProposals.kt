/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.persistence.dao.conversation.PendingSubconversationProposalDAO
import com.wire.kalium.persistence.dao.conversation.PendingSubconversationProposalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal data class PendingSubconversationProposal(
    val group: GroupID,
    val deadline: Instant,
    val token: String,
    val leaveRequested: Boolean = false,
)

internal interface PendingSubconversationProposals {
    suspend fun schedule(group: GroupID, deadline: Instant)
    fun observe(): Flow<List<PendingSubconversationProposal>>
    suspend fun current(group: GroupID): PendingSubconversationProposal?
    suspend fun isCurrent(record: PendingSubconversationProposal): Boolean
    suspend fun markForRetirement(record: PendingSubconversationProposal): PendingSubconversationProposal?
    suspend fun acknowledge(record: PendingSubconversationProposal)
}

/** Durable proposal work outlives the subgroup mapping removed by leave. */
internal class PendingSubconversationProposalsImpl(
    private val proposals: PendingSubconversationProposalDAO,
) : PendingSubconversationProposals {
    override suspend fun schedule(group: GroupID, deadline: Instant) =
        proposals.schedule(PendingSubconversationProposalEntity(group.value, deadline, Uuid.random().toString()))

    override fun observe(): Flow<List<PendingSubconversationProposal>> =
        proposals.observe().map { records -> records.map { it.toModel() } }

    override suspend fun current(group: GroupID): PendingSubconversationProposal? = proposals.current(group.value)?.toModel()

    override suspend fun isCurrent(record: PendingSubconversationProposal): Boolean = proposals.isCurrent(record.toEntity())

    override suspend fun markForRetirement(record: PendingSubconversationProposal): PendingSubconversationProposal? =
        proposals.markForRetirement(record.toEntity())?.toModel()

    override suspend fun acknowledge(record: PendingSubconversationProposal) = proposals.acknowledge(record.toEntity())
}

private fun PendingSubconversationProposal.toEntity() = PendingSubconversationProposalEntity(group.value, deadline, token, leaveRequested)

private fun PendingSubconversationProposalEntity.toModel() = PendingSubconversationProposal(
    GroupID(groupId),
    deadline,
    token,
    leaveRequested,
)

/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.dao.conversation

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/** Durable subgroup proposal work, independent of the subgroup routing mapping. */
data class PendingSubconversationProposalEntity(
    val groupId: String,
    val deadline: Instant,
    val token: String,
    val leaveRequested: Boolean = false,
)

interface PendingSubconversationProposalDAO {
    /** Replace the token and reset retirement, preserving the earliest outstanding deadline. */
    suspend fun schedule(record: PendingSubconversationProposalEntity)
    fun observe(): Flow<List<PendingSubconversationProposalEntity>>
    suspend fun current(groupId: String): PendingSubconversationProposalEntity?
    suspend fun isCurrent(record: PendingSubconversationProposalEntity): Boolean

    /** Mark only the exact observed record; return null when another owner has replaced it. */
    suspend fun markForRetirement(record: PendingSubconversationProposalEntity): PendingSubconversationProposalEntity?

    /** Remove only the exact observed record, including its retirement marker. */
    suspend fun acknowledge(record: PendingSubconversationProposalEntity)

    /** Clear client-owned work and its legacy source without importing it. */
    suspend fun clear()
}

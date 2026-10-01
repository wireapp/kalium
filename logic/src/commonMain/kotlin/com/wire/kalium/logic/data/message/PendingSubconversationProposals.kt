/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
internal data class PendingSubconversationProposal(
    @SerialName("group") val group: GroupID,
    @SerialName("deadline") val deadline: Instant,
    @SerialName("token") val token: String,
)

internal interface PendingSubconversationProposals {
    suspend fun schedule(group: GroupID, deadline: Instant)
    fun observe(): Flow<List<PendingSubconversationProposal>>
    suspend fun isCurrent(record: PendingSubconversationProposal): Boolean
    suspend fun acknowledge(record: PendingSubconversationProposal)
}

/** One instance per existing account owner; no native or transport work outside that owner. */
internal class PendingSubconversationProposalsImpl(private val metadata: MetadataDAO) : PendingSubconversationProposals {
    private val mutex = Mutex()
    private suspend fun read(): List<PendingSubconversationProposal> = decode(metadata.valueByKey(KEY))
    private fun decode(
        value: String?
    ): List<PendingSubconversationProposal> = value?.let {
        Json.decodeFromString<List<PendingSubconversationProposal>>(it)
    }.orEmpty()

    override suspend fun schedule(group: GroupID, deadline: Instant) = mutex.withLock {
        val records = read()
        val earliest = records.firstOrNull { it.group == group }?.deadline?.let { minOf(it, deadline) } ?: deadline
        // Every scheduling effect gets a new token, even when its deadline is unchanged.
        // An older native transaction's acknowledgement cannot erase newer proposal work.
        val updated = records.filterNot {
            it.group == group
        } + PendingSubconversationProposal(group, earliest, Uuid.random().toString())
        metadata.insertValue(Json.encodeToString(updated), KEY)
    }

    override fun observe(): Flow<List<PendingSubconversationProposal>> = metadata.valueByKeyFlow(KEY).map(::decode)

    override suspend fun isCurrent(record: PendingSubconversationProposal): Boolean = mutex.withLock { record in read() }

    override suspend fun acknowledge(record: PendingSubconversationProposal) = mutex.withLock {
        val records = read()
        if (record in records) metadata.insertValue(Json.encodeToString(records - record), KEY)
    }

    private companion object { const val KEY = "pending-subconversation-proposals-v1" }
}

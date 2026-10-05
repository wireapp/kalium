/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiver.handler.legalhold

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Access is serialized by the account's legal hold handler. */
internal class LegalHoldUserTransitionJournal(private val metadata: MetadataDAO) {
    @Serializable
    data class Transition(
        val key: String,
        val user: QualifiedID,
        val enabled: Boolean,
        val noticeNeeded: Boolean,
        val timestamp: Instant,
        val conversations: List<QualifiedID>,
        val noticeId: String = Uuid.random().toString(),
        val conversationPlan: List<ConversationPlan>? = null,
        val notices: List<Notice>? = null,
    )

    @Serializable
    data class ConversationPlan(val id: QualifiedID, val enabled: Boolean)

    @Serializable
    data class Notice(val conversation: QualifiedID, val messageId: String, val members: List<QualifiedID>, val update: Boolean)

    suspend fun replace(previous: Transition, updated: Transition): Transition {
        val records = pending()
        check(previous in records && updated.key == previous.key)
        metadata.insertValue(Json.encodeToString(records.map { if (it == previous) updated else it }), KEY)
        return updated
    }

    suspend fun pending(): List<Transition> = metadata.valueByKey(KEY)?.let {
        Json.decodeFromString<List<Transition>>(it)
    }.orEmpty()

    suspend fun begin(key: String, prepare: suspend () -> Transition): Transition {
        val records = pending()
        return records.firstOrNull { it.key == key } ?: prepare().also {
            check(it.key == key)
            metadata.insertValue(Json.encodeToString(records + it), KEY)
        }
    }

    suspend fun acknowledge(record: Transition) {
        val records = pending()
        if (record in records) metadata.insertValue(Json.encodeToString(records - record), KEY)
    }

    companion object {
        private const val KEY = "legal-hold-user-transitions-v1"
        fun eventKey(event: String, user: QualifiedID, enabled: Boolean) =
            Json.encodeToString(listOf(event, user.value, user.domain, enabled.toString()))
    }
}

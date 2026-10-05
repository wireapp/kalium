/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiver.handler.legalhold

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** One instance per account owner. Intent precedes canonical mutation; ACK follows required history writes. */
internal class LegalHoldTransitionJournal(private val metadata: MetadataDAO) {
    @Serializable
    data class Transition(
        val key: String,
        val subject: QualifiedID,
        val enabled: Boolean,
        val timestamp: Instant,
        val noticeId: String = Uuid.random().toString(),
    )

    private val mutex = Mutex()
    private suspend fun read(): List<Transition> = metadata.valueByKey(KEY)?.let {
        Json.decodeFromString<List<Transition>>(it)
    }.orEmpty()
    private suspend fun write(records: List<Transition>) = metadata.insertValue(Json.encodeToString(records), KEY)

    suspend fun pending(): List<Transition> = mutex.withLock { read() }
    suspend fun find(key: String): Transition? = mutex.withLock { read().firstOrNull { it.key == key } }
    suspend fun begin(key: String, prepare: suspend () -> Transition): Transition = mutex.withLock {
        val records = read()
        records.firstOrNull { it.key == key } ?: prepare().also {
            check(it.key == key)
            write(records + it)
        }
    }
    suspend fun acknowledge(record: Transition) = mutex.withLock {
        val records = read()
        if (record in records) write(records - record)
    }

    companion object {
        private const val KEY = "legal-hold-conversation-transitions-v1"
        fun conversationKey(id: QualifiedID) = "conversation:" + Json.encodeToString(id)
    }
}

/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.dao.conversation

import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class SubconversationDAOImpl(
    private val database: UserDatabase,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : SubconversationDAO {
    private val migrationMutex = Mutex()
    private var importedLegacyMapping = false

    override suspend fun upsert(conversationId: QualifiedIDEntity, subconversationId: String, groupId: String) {
        importLegacyMapping()
        withContext(writeDispatcher.value) {
            database.subconversationQueries.upsert(conversationId.value, conversationId.domain, subconversationId, groupId)
        }
    }

    override suspend fun getGroupId(conversationId: QualifiedIDEntity, subconversationId: String): String? {
        importLegacyMapping()
        return withContext(readDispatcher.value) {
            database.subconversationQueries.selectGroup(conversationId.value, conversationId.domain, subconversationId).awaitAsOneOrNull()
        }
    }

    override suspend fun containsGroup(groupId: String): Boolean {
        importLegacyMapping()
        return withContext(readDispatcher.value) { database.subconversationQueries.containsGroup(groupId).awaitAsOne() }
    }

    override suspend fun delete(conversationId: QualifiedIDEntity, subconversationId: String) {
        importLegacyMapping()
        withContext(writeDispatcher.value) {
            database.subconversationQueries.deleteMapping(conversationId.value, conversationId.domain, subconversationId)
        }
    }

    /** Compatibility with the unpublished JSON format. Never delete it after a partial or failed import. */
    private suspend fun importLegacyMapping() = migrationMutex.withLock {
        if (!importedLegacyMapping) {
            withContext(writeDispatcher.value) {
                database.transaction {
                    database.metadataQueries.selectValueByKey(LEGACY_KEY).awaitAsOneOrNull()?.let { serialized ->
                        val entries = Json.decodeFromString<List<LegacyGroup>>(serialized)
                        check(entries.distinctBy { Triple(it.conversation.id, it.conversation.domain, it.subId) }.size == entries.size) {
                            "Duplicate legacy subconversation mapping"
                        }
                        entries.forEach { entry ->
                            val existing = database.subconversationQueries.selectGroup(
                                entry.conversation.id,
                                entry.conversation.domain,
                                entry.subId
                            ).awaitAsOneOrNull()
                            check(existing == null || existing == entry.group) { "Conflicting legacy subconversation mapping" }
                            database.subconversationQueries.upsert(
                                entry.conversation.id,
                                entry.conversation.domain,
                                entry.subId,
                                entry.group
                            )
                        }
                        database.metadataQueries.deleteValue(LEGACY_KEY)
                    }
                }
            }
            // Publish only after the transaction has committed; failed/cancelled imports remain retryable.
            importedLegacyMapping = true
        }
    }

    @Serializable
    private data class LegacyGroup(
        @SerialName("conversation") val conversation: LegacyConversation,
        @SerialName("subId") val subId: String,
        @SerialName("group") val group: String,
    )

    @Serializable
    private data class LegacyConversation(@SerialName("id") val id: String, @SerialName("domain") val domain: String)

    private companion object {
        const val LEGACY_KEY = "calling-subconversation-groups-v1"
    }
}

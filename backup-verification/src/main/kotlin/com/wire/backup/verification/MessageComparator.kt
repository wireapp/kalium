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
package com.wire.backup.verification

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupUser
import java.io.File

/**
 * A generic class that compares items from multiple sources and categorizes them
 * based on equality, presence, and differences.
 */
abstract class Comparator<T> {
    /**
     * Represents the result of comparing items across different sources
     *
     * @param equalItems Items that are exactly the same across sources
     * @param missingItems Items that are missing in some sources
     * @param differentItems Items that exist in multiple sources but have differences
     */
    data class ComparisonResult<T>(
        val equalItems: List<ItemMatch<T>> = emptyList(),
        val missingItems: List<ItemMissing<T>> = emptyList(),
        val differentItems: List<ItemDifference<T>> = emptyList()
    )

    /**
     * Represents an item that matches exactly across sources
     *
     * @param itemId The unique identifier of the item
     * @param item The item object itself
     * @param sources The sources where this exact item was found
     */
    data class ItemMatch<T>(
        val itemId: String,
        val item: T,
        val sources: Set<BackupId>
    )

    /**
     * Represents an item that is missing in some sources
     *
     * @param itemId The unique identifier of the item
     * @param presentIn Map of sources to the item found in that source
     * @param missingFrom List of sources where the item is missing
     */
    data class ItemMissing<T>(
        val itemId: String,
        val presentIn: Map<BackupId, T>,
        val missingFrom: List<BackupId>
    )

    /**
     * Represents an item that exists in multiple sources but has differences
     *
     * @param itemId The unique identifier of the item
     * @param itemsBySource Map of sources to their version of the item
     */
    data class ItemDifference<T>(
        val itemId: String,
        val itemsBySource: Map<BackupId, T>
    )

    /**
     * Gets the ID of an item
     */
    protected abstract fun getItemId(item: T): String

    /**
     * Gets all items from a backup import
     */
    protected abstract fun getAllItems(backupImport: BackupImport): List<T>

    /**
     * Checks if all items are equal
     */
    private fun areAllItemsEqual(items: List<T>): Boolean {
        if (items.isEmpty()) return true
        val first = items.first()
        return items.all { it == first }
    }

    /**
     * Compares items from different sources and categorizes them
     *
     * @return A ComparisonResult containing categorized items
     */
    fun compare(backupImports: List<BackupImport>): ComparisonResult<T> {
        val allSources = backupImports.map { it.backupId }
        val allItemIds = backupImports
            .flatMap { getAllItems(it) }
            .map { getItemId(it) }
            .toSet()

        val equalItems = mutableListOf<ItemMatch<T>>()
        val missingItems = mutableListOf<ItemMissing<T>>()
        val differentItems = mutableListOf<ItemDifference<T>>()

        for (itemId in allItemIds) {
            val matchingItemsForId = backupImports.associate { import ->
                import.backupId to getAllItems(import).filter { item ->
                    getItemId(item) == itemId
                }
            }

            val sourcesWithItem = matchingItemsForId.filter { it.value.isNotEmpty() }
            val sourcesWithoutItem = allSources - sourcesWithItem.keys

            when {
                // Item is present in all sources and identical
                sourcesWithItem.size == backupImports.size &&
                        areAllItemsEqual(sourcesWithItem.values.map { it.first() }) -> {
                    equalItems.add(
                        ItemMatch(
                            itemId = itemId,
                            item = sourcesWithItem.values.first().first(),
                            sources = sourcesWithItem.keys.toSet()
                        )
                    )
                }
                // Item is missing in some sources
                sourcesWithoutItem.isNotEmpty() -> {
                    missingItems.add(
                        ItemMissing(
                            itemId = itemId,
                            presentIn = sourcesWithItem.mapValues { it.value.first() },
                            missingFrom = sourcesWithoutItem.toList()
                        )
                    )
                }
                // Item exists in multiple sources but has differences
                else -> {
                    differentItems.add(
                        ItemDifference(
                            itemId = itemId,
                            itemsBySource = sourcesWithItem.mapValues { it.value.first() }
                        )
                    )
                }
            }
        }

        return ComparisonResult(
            equalItems = equalItems,
            missingItems = missingItems,
            differentItems = differentItems
        )
    }
}

class MessageComparator : Comparator<BackupMessage>() {
    override fun getItemId(item: BackupMessage): String = item.id

    override fun getAllItems(backupImport: BackupImport): List<BackupMessage> = backupImport.allMessages
}

class UserComparator : Comparator<BackupUser>() {
    override fun getItemId(item: BackupUser): String = item.id.toString()

    override fun getAllItems(backupImport: BackupImport): List<BackupUser> = backupImport.allUsers
}

class ConversationComparator : Comparator<BackupConversation>() {
    override fun getItemId(item: BackupConversation): String = item.id.toString()

    override fun getAllItems(backupImport: BackupImport): List<BackupConversation> = backupImport.allConversations
}

sealed interface CompleteBackupComparisonResult {
    data class Success(
        val messages: Comparator.ComparisonResult<BackupMessage>,
        val users: Comparator.ComparisonResult<BackupUser>,
        val conversations: Comparator.ComparisonResult<BackupConversation>
    ) : CompleteBackupComparisonResult

    data class Failure(
        val errors: List<File>
    ) : CompleteBackupComparisonResult
}

fun compareBackupMessages(
    messagesFromSources: List<BackupImport>
): CompleteBackupComparisonResult.Success {
    val messageComparator = MessageComparator()
    val userComparator = UserComparator()
    val conversationComparator = ConversationComparator()

    return CompleteBackupComparisonResult.Success(
        messages = messageComparator.compare(messagesFromSources),
        users = userComparator.compare(messagesFromSources),
        conversations = conversationComparator.compare(messagesFromSources)
    )
}

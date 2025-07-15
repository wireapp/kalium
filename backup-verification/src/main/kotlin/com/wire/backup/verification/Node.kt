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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

data class Node(
    val key: String = "",
    val presentInSources: Set<BackupId> = emptySet(),
    val children: Map<String, Node> = emptyMap(),
    val textValue: Map<BackupId, String?> = emptyMap(),
    val allSourcesAgree: Boolean = false
) {
    val hasDisplayableTextValue = textValue.isNotEmpty()
}

fun buildTree(backupMessage: BackupMessage, sourceId: BackupId): Node {
    val jsonElement = Json.encodeToJsonElement(backupMessage)
    return jsonElementToNodeValue(jsonElement, sourceId = sourceId)
}

fun buildTree(backupUser: BackupUser, sourceId: BackupId): Node {
    val jsonElement = Json.encodeToJsonElement(backupUser)
    return jsonElementToNodeValue(jsonElement, sourceId = sourceId)
}

fun buildTree(backupConversation: BackupConversation, sourceId: BackupId): Node {
    val jsonElement = Json.encodeToJsonElement(backupConversation)
    return jsonElementToNodeValue(jsonElement, sourceId = sourceId)
}

private fun jsonElementToNodeValue(
    element: JsonElement,
    key: String = "",
    sourceId: BackupId
): Node {
    val textValue = when {
        element is JsonPrimitive -> {
            // For primitive values, store the string representation in textValue
            mapOf(sourceId to element.toString())
        }

        else -> mapOf(sourceId to null)
    }

    val presentInSources = setOf(sourceId)

    return when (element) {
        is JsonPrimitive -> Node(
            key = key,
            textValue = textValue,
            presentInSources = presentInSources,
            children = mapOf(),
        )

        is JsonObject -> Node(
            key = key,
            presentInSources = presentInSources,
            children = element.entries.associate { (k, v) ->
                k to jsonElementToNodeValue(v, k, sourceId)
            },
        )

        is JsonArray -> Node(
            key = key,
            presentInSources = presentInSources,
            children = element.mapIndexed { index, jsonElement ->
                index.toString() to jsonElementToNodeValue(jsonElement, index.toString(), sourceId)
            }.toMap(),
        )
    }
}

fun compareMessageAcrossSources(
    data: Map<BackupId, BackupMessage>
): Node {
    val trees = data.mapValues { (sourceId, message) -> buildTree(message, sourceId) }.toMap()
    return buildMergedTree(trees)
}

fun compareUserAcrossSources(
    data: Map<BackupId, BackupUser>
): Node {
    val trees = data.mapValues { (sourceId, user) -> buildTree(user, sourceId) }.toMap()
    return buildMergedTree(trees)
}

fun compareConversationAcrossSources(
    data: Map<BackupId, BackupConversation>
): Node {
    val trees = data.mapValues { (sourceId, conversation) -> buildTree(conversation, sourceId) }.toMap()
    return buildMergedTree(trees)
}

private fun markAllNodesAsAgreed(node: Node): Node {
    return Node(
        key = node.key,
        presentInSources = node.presentInSources,
        textValue = node.textValue,
        children = node.children.mapValues { (_, child) -> markAllNodesAsAgreed(child) },
        allSourcesAgree = true
    )
}

private fun buildMergedTree(trees: Map<BackupId, Node>): Node = when (trees.size) {
    0 -> Node()
    1 -> markAllNodesAsAgreed(trees.values.first())
    else -> {
        // Initialize merged tree with the first tree
        var mergedTree = trees.values.first()

        // Merge other trees
        trees.values.drop(1).forEach { sourceTree ->
            mergedTree = mergeNodes(trees.keys, mergedTree, sourceTree)
        }
        mergedTree
    }
}

private fun mergeNodes(allSources: Set<BackupId>, node1: Node, node2: Node): Node {
    // Merge presence information
    val mergedPresentInSources = node1.presentInSources + node2.presentInSources

    // Merge text values
    val mergedTextValue = node1.textValue.toMutableMap().apply {
        putAll(node2.textValue)
    }

    // Calculate if all sources agree on the text value
    val allSourcesPresent = mergedTextValue.keys.all { allSources.contains(it) }
    val allValuesEqual = mergedTextValue.values.distinct().size == 1
    val allSourcesAgree = allSourcesPresent && allValuesEqual

    // Merge children recursively
    val allChildKeys = node1.children.keys + node2.children.keys
    val mergedChildren = allChildKeys.associateWith { key ->
        val child1 = node1.children[key]
        val child2 = node2.children[key]

        when {
            child1 == null -> child2!!
            child2 == null -> child1
            else -> mergeNodes(allSources, child1, child2)
        }
    }

    return Node(
        key = node1.key,
        presentInSources = mergedPresentInSources,
        textValue = mergedTextValue,
        children = mergedChildren,
        allSourcesAgree = allSourcesAgree
    )
}

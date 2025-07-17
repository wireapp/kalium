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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DifferentItemsTab(result: CompleteBackupComparisonResult.Success) = LazyColumn {
    item { Text("Conversations", fontSize = 20.sp, modifier = Modifier.padding(vertical = 8.dp)) }
    items(result.conversations.differentItems) { conversation ->
        Column {
            Text("Different conversation: ${conversation.itemId}", fontSize = 20.sp)
            DiffItem(compareConversationAcrossSources(conversation.itemsBySource))
        }
        Divider(modifier = Modifier.padding(vertical = 8.dp))
    }
    item { Text("Users", fontSize = 20.sp, modifier = Modifier.padding(vertical = 8.dp)) }
    items(result.users.differentItems) { user ->
        Column {
            Text("Different user: ${user.itemId}", fontSize = 20.sp)
            DiffItem(compareUserAcrossSources(user.itemsBySource))
        }
        Divider(modifier = Modifier.padding(vertical = 8.dp))
    }
    item { Text("Messages", fontSize = 20.sp, modifier = Modifier.padding(vertical = 8.dp)) }
    items(result.messages.differentItems) { message ->
        Column {
            Text("Different message: ${message.itemId}", fontSize = 20.sp)
            DiffItem(compareMessageAcrossSources(message.itemsBySource))
        }
        Divider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
fun DiffItem(node: Node, level: Int = 0) {
    val padding = (level * 16).dp

    // Determine background colour based on agreement status
    val backgroundColor = if (!node.allSourcesAgree && node.textValue.isNotEmpty()) {
        Color.Red.copy(alpha = 0.2f)
    } else {
        Color.Transparent
    }

    Column(modifier = Modifier.padding(start = padding)) {
        // Display the node key and any text values
        Column(
            modifier = Modifier
                .background(backgroundColor)
                .padding(vertical = 2.dp)
        ) {
            // Display text values from different sources
            if (node.allSourcesAgree && node.hasDisplayableTextValue) {
                Text(
                    text = "${node.key}: ${node.textValue.values.first()}",
                    fontSize = 16.sp,
                    color = Color.Black
                )
            } else {
                Text(
                    text = node.key,
                    fontSize = 16.sp,
                    color = Color.Black
                )
                // If sources disagree, show each source's value
                node.textValue.forEach { (sourceId, value) ->
                    Text(
                        text = "${sourceId.id}: $value",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }

        // Recursively display all child nodes with increased indentation
        node.children.forEach { (_, childNode) ->
            DiffItem(childNode, level + 1)
        }
    }
}

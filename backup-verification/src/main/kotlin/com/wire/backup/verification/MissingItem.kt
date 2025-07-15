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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupUser

@Composable
fun MissingItemsTab(result: CompleteBackupComparisonResult.Success) = LazyColumn {
    item { Text("Conversations", fontSize = 20.sp, modifier = Modifier.padding(vertical = 8.dp)) }
    items(result.conversations.missingItems) { conversation ->
        MissingConversationRow(conversation)
    }
    item { Text("Users", fontSize = 20.sp, modifier = Modifier.padding(vertical = 8.dp)) }
    items(result.users.missingItems) { user ->
        MissingUserRow(user)
    }
    item { Text("Messages", fontSize = 20.sp, modifier = Modifier.padding(vertical = 8.dp)) }
    items(result.messages.missingItems) { message ->
        MissingMessageRow(message)
    }
}

@Composable
fun MissingConversationRow(
    item: Comparator.ItemMissing<BackupConversation>,
) {
    Column {
        Text("Missing conversation: ${item.itemId}")
        Text("Missing from: ${item.missingFrom.joinToString { it.id }}")
        Text("Present in other sources: ${item.presentIn.keys.joinToString { it.id }}")
        DiffItem(compareConversationAcrossSources(item.presentIn))
    }
    Spacer(Modifier.size(8.dp))
}

@Composable
fun MissingMessageRow(
    item: Comparator.ItemMissing<BackupMessage>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Missing message: ${item.itemId}")
        Text("Missing from: ${item.missingFrom.joinToString { it.id }}")
        Text("Present in other sources: ${item.presentIn.keys.joinToString { it.id }}")
        DiffItem(compareMessageAcrossSources(item.presentIn))
    }
    Spacer(Modifier.size(8.dp))
}

@Composable
fun MissingUserRow(
    item: Comparator.ItemMissing<BackupUser>,
) {
    Column {
        Text("Missing user: ${item.itemId}")
        Text("Missing from: ${item.missingFrom.joinToString { it.id }}")
        Text("Present in other sources: ${item.presentIn.keys.joinToString { it.id }}")
        DiffItem(compareUserAcrossSources(item.presentIn))
    }
    Spacer(Modifier.size(8.dp))
}

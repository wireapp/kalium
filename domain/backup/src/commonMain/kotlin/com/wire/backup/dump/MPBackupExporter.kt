/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.backup.dump

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupReaction
import com.wire.backup.data.BackupUser
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Entity able to serialize [BackupData] entities, like [BackupMessage], [BackupConversation], [BackupUser]
 * into a cross-platform backup format.
 */
@JsExport
public expect class MPBackupExporter {

    /**
     * Add a user to the backup.
     */
    @JsName("addUser")
    public fun add(user: BackupUser)

    /**
     * Add a conversation to the backup.
     */
    @JsName("addConversation")
    public fun add(conversation: BackupConversation)

    /**
     * Add a message to the backup.
     */
    @JsName("addMessage")
    public fun add(message: BackupMessage)

    /**
     * Add a reaction to the backup.
     */
    @JsName("addReaction")
    public fun add(reaction: BackupReaction)
}

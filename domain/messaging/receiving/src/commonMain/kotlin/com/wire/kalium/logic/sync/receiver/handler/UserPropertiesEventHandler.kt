/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.logic.data.conversation.folders.toDao
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.persistence.config.UserConfigStorage
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderDAO

class UserPropertiesEventHandler(
    private val userConfigStorage: UserConfigStorage,
    private val conversationFolderDAO: ConversationFolderDAO,
) {
    suspend fun handle(event: Event.UserProperty) = wrapStorageRequest {
        when (event) {
            is Event.UserProperty.ReadReceiptModeSet -> userConfigStorage.persistReadReceipts(event.value)
            is Event.UserProperty.TypingIndicatorModeSet -> userConfigStorage.persistTypingIndicator(event.value)
            is Event.UserProperty.FoldersUpdate -> conversationFolderDAO.updateConversationFolders(event.folders.map { it.toDao() })
        }
    }
}

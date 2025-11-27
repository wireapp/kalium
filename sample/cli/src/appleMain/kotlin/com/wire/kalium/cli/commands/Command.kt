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
package com.wire.kalium.cli.commands

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.message.UnreadEventType
import com.wire.kalium.logic.feature.UserSessionScope
import kotlinx.coroutines.flow.first

sealed class Command(
    val name: String,
    open var query: String
) {
    abstract fun nextResult()
    abstract fun resultDescription(): String

    class Jump(query: String, private val userSession: UserSessionScope) : Command(NAME, query) {
        private var index: Int = 0
        private var conversations: List<ConversationDetails> = emptyList()
        private var filteredConversations: List<ConversationDetails> = emptyList()

        val selection: ConversationDetails?
            get() = if (filteredConversations.isEmpty()) null else filteredConversations[index]

        override var query: String
            get() = super.query
            set(value) {
                super.query = value
                updateFilter()
            }

        suspend fun prepare() {
            conversations = userSession.conversations.observeConversationListDetails(includeArchived = true).first()
            updateFilter()
        }

        private fun updateFilter() {
            filteredConversations = conversations
                .filter { it.conversation.name?.contains(query, ignoreCase = true) ?: false }
            index = 0
        }

        override fun nextResult() {
            index += 1

            if (index >= filteredConversations.count()) {
                index = 0
            }
        }

        override fun resultDescription(): String =
            selection?.let {
                val unreadCount = when (it) {
                    is ConversationDetails.Group -> it.unreadEventCount[UnreadEventType.MESSAGE]
                    is ConversationDetails.OneOne -> it.unreadEventCount[UnreadEventType.MESSAGE]
                    else -> null
                }

                "[${indexDescription()}] ${nameDescription()} ${unreadCountDescription()}"
            } ?: "no result"

        private fun indexDescription() = "$index/${filteredConversations.size}"

        private fun nameDescription() = selection?.conversation?.name ?: "no name"

        private fun unreadCountDescription(): String {
            val unreadCount = selection?.let {
                when (it) {
                    is ConversationDetails.Group -> it.unreadEventCount[UnreadEventType.MESSAGE]
                    is ConversationDetails.OneOne -> it.unreadEventCount[UnreadEventType.MESSAGE]
                    else -> null
                }
            }

            return (unreadCount?.let { " unread = ($it)" }) ?: ""
        }

        companion object {
            const val NAME = "jump"
        }
    }

    companion object {
        suspend fun find(name: String, query: String, userSessionScope: UserSessionScope): Command? =
            when (name) {
                Jump.NAME -> Jump(query, userSessionScope).also { it.prepare() }
                else -> null
            }
    }
}

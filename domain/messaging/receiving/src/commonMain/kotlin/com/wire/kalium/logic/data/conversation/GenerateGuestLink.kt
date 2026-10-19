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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.network.api.authenticated.conversation.guestroomlink.ConversationInviteLinkResponse
import io.ktor.http.URLBuilder

private fun generateGuestLink(
    key: String,
    code: String,
    accountUrl: String
): String = URLBuilder(accountUrl).apply {
    parameters.apply {
        append("key", key)
        append("code", code)
    }.build()
}.buildString()

fun Event.Conversation.CodeUpdated.link(accountUrl: String): String = uri ?: generateGuestLink(key, code, accountUrl)

fun ConversationInviteLinkResponse.link(accountUrl: String): String = uri ?: generateGuestLink(key, code, accountUrl)

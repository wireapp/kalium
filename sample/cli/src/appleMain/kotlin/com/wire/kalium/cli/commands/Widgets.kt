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

import com.github.ajalt.mordant.rendering.Lines
import com.github.ajalt.mordant.rendering.OverflowWrap
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.VerticalAlign
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.rendering.WidthRange
import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.horizontalLayout
import com.github.ajalt.mordant.table.verticalLayout
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.HorizontalRule
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Text
import com.github.ajalt.mordant.widgets.withPadding
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import kotlin.math.ceil
import kotlin.math.floor

class CustomScrollRegion(
    private val content: Widget,
    private val height: Int,
    private val contentAlign: VerticalAlign
) : Widget {
    override fun measure(t: Terminal, width: Int): WidthRange {
        return content.measure(t, width)
    }

    override fun render(t: Terminal, width: Int): Lines {
        val measuredHeight = content.render(t, width).lines.size
        val heightToAdd = maxOf(height - measuredHeight, 0)

        return if (measuredHeight < height) {
            val padding = when (contentAlign) {
                VerticalAlign.TOP -> Padding { bottom = heightToAdd }
                VerticalAlign.BOTTOM -> Padding { top = heightToAdd }
                VerticalAlign.MIDDLE -> Padding {
                    top = ceil(heightToAdd / 2.0).toInt()
                    bottom = floor(heightToAdd / 2.0).toInt()
                }
            }

            content.withPadding(padding).render(t, width)
        } else {
            content.render(t, width).clip(height, contentAlign)
        }
    }
}

internal fun Lines.clip(
    newHeight: Int = lines.size,
    verticalAlign: VerticalAlign = VerticalAlign.TOP,
): Lines {
    return if (newHeight < lines.size) {
        if (verticalAlign == VerticalAlign.BOTTOM) {
            Lines(lines.takeLast(newHeight))
        } else {
            Lines(lines.take(newHeight))
        }
    } else {
        Lines(lines)
    }
}

@Suppress("MagicNumber")
internal fun conversation(input: String, aux: String?, name: String, messages: List<Message>, height: Int): Widget =
    verticalLayout {
        cell(Text(name, overflowWrap = OverflowWrap.ELLIPSES, whitespace = Whitespace.NOWRAP)) {
            style = TextColors.white on TextColors.gray
        }
        cell(
            CustomScrollRegion(
                messageList(messages),
                height = height - 3,
                contentAlign = VerticalAlign.BOTTOM
            )
        )
        cell(HorizontalRule())
        cell("> $input${aux?.let { " ($it)" } ?: ""}") {
            whitespace = Whitespace.PRE
        }
        align = TextAlign.LEFT
    }

private fun messageList(messages: List<Message>): Widget =
    verticalLayout {
        cellsFrom(messages.map(::message))
    }

private fun message(message: Message): Widget =
    when (message) {
        is Message.Regular -> regularContent(message)
        is Message.System -> systemContent(message)
        else -> textMessage("sender", "<unknown content>")
    }

private fun regularContent(message: Message.Regular) =
    when (val content = message.content) {
        is MessageContent.Text -> textMessage(message.senderUserName, content.value)
        is MessageContent.Asset -> textMessage(message.senderUserName, "Shared an asset")
        is MessageContent.FailedDecryption -> systemMessage(
            message.senderUserName,
            "Decryption error (${content.clientId})"
        )
        is MessageContent.Knock -> textMessage(message.senderUserName, "<ping>")
        is MessageContent.RestrictedAsset -> textMessage(message.senderUserName, "Shared an asset")
        is MessageContent.Unknown -> systemMessage(message.senderUserName, "Unknown message")
    }

private fun systemContent(message: Message.System) =
    when (val content = message.content) {
        is MessageContent.ConversationRenamed ->
            systemMessage(message.senderUserName, "Conversation was renamed to ${content.conversationName}")
        is MessageContent.ConversationReceiptModeChanged ->
            systemMessage(
                message.senderUserName,
                "Read receipts was ${if (content.receiptMode) "enabled" else "disabled" }"
            )
        MessageContent.CryptoSessionReset ->
            systemMessage(message.senderUserName, "Proteus session as reset")
        MessageContent.HistoryLost ->
            systemMessage(null, "You've been offline for a long time and may have lost history")
        is MessageContent.MemberChange.Added ->
            systemMessage(message.senderUserName, "${content.members.count()} user(s) was added")
        is MessageContent.MemberChange.Removed ->
            systemMessage(message.senderUserName, "${content.members.count()} user(s) were removed")
        MessageContent.MissedCall ->
            systemMessage(message.senderUserName, "missed call")
        is MessageContent.NewConversationReceiptMode ->
            systemMessage(null, "Read receipts are ${if (content.receiptMode) "enabled" else "disabled" }")
        is MessageContent.TeamMemberRemoved ->
            systemMessage(null, "${content.userName} was removed from the team")
    }

@Suppress("MagicNumber")
private fun textMessage(author: String?, message: String): Widget =
    horizontalLayout {
        column(0) {
            width = ColumnWidth.Fixed(20)
            align = TextAlign.RIGHT
            whitespace = Whitespace.NOWRAP
        }
        column(1) {
            width = ColumnWidth.Expand()
        }
        cell("<${author?.take(18) ?: "unknown"}>") {
            style = TextColors.blue
            whitespace = Whitespace.NOWRAP
        }
        cell(Text(message, whitespace = Whitespace.NORMAL)) {
        }
    }

private fun systemMessage(author: String?, message: String): Widget =
    horizontalLayout {
        column(0) {
            width = ColumnWidth.Expand()
        }
        author?.let { author ->
            cell("${TextColors.red("!")} $message by ${TextColors.blue(author)}")
        } ?: run {
            cell("${TextColors.red("!")} $message")
        }
    }

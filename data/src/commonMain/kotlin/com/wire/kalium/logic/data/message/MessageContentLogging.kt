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
package com.wire.kalium.logic.data.message

@Suppress("CyclomaticComplexMethod")
inline fun MessageContent.FromProto.typeDescription(): String = when (this) {
    is MessageContent.Asset -> "Asset"
    is MessageContent.Composite -> "Composite"
    is MessageContent.FailedDecryption -> "FailedDecryption"
    is MessageContent.Knock -> "Knock"
    is MessageContent.Location -> "Location"
    is MessageContent.RestrictedAsset -> "RestrictedAsset"
    is MessageContent.Text -> "Text"
    is MessageContent.Unknown -> "Unknown"
    is MessageContent.Availability -> "Availability"
    is MessageContent.ButtonAction -> "ButtonAction"
    is MessageContent.ButtonActionConfirmation -> "ButtonActionConfirmation"
    is MessageContent.Calling -> "Calling"
    is MessageContent.Cleared -> "Cleared"
    MessageContent.ClientAction -> "ClientAction"
    is MessageContent.DeleteForMe -> "DeleteForMe"
    is MessageContent.DeleteMessage -> "DeleteMessage"
    MessageContent.Ignored -> "Ignored"
    is MessageContent.LastRead -> "LastRead"
    is MessageContent.Reaction -> "Reaction"
    is MessageContent.Receipt -> "Receipt"
    is MessageContent.TextEdited -> "TextEdited"
    is MessageContent.DataTransfer -> "DataTransfer"
    is MessageContent.InCallEmoji -> "InCallEmoji"
}

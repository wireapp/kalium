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

import kotlinx.serialization.Serializable

@Serializable
sealed class Filter {
    @Serializable
    sealed class Conversation : Filter() {
        @Serializable
        data object All : Conversation()

        @Serializable
        data object Favorites : Conversation()

        @Serializable
        data object Groups : Conversation()

        @Serializable
        data object OneOnOne : Conversation()

        @Serializable
        data object Channels : Conversation()

        @Serializable
        data class Folder(val folderName: String, val folderId: String) : Conversation()
    }

    @Serializable
    sealed class Cells : Filter() {
        @Serializable
        data object Tags : Cells()
    }
}

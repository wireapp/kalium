//
// Wire
// Copyright (C) 2016 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//
package com.wire.kalium.models.backend


// TODO: rename file
import kotlinx.serialization.Serializable

@Serializable
sealed class ConversationMember {
    abstract val userId: String
}

@Serializable
data class OtherMember(
    override val userId: String,
    val service: ServiceReferenceResponse?
) : ConversationMember()

@Serializable
data class SelfMember(
        override val userId: String,
        val service: ServiceReferenceResponse?,
        val hiddenReference: String?,
        val otrMutedReference: String?,
        val hidden: Boolean?,
        val otrArchived: Boolean?,
        val otrMuted: Boolean?,
        val otrArchiveReference: String?
) : ConversationMember()

@Serializable
data class ServiceReferenceResponse(
    val id: String,
    val provider: String
)

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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ConversationMember {
    @SerialName("userId")
    abstract val userId: String
}

@Serializable
data class OtherMember(
    @SerialName("userId") override val userId: String,
    @SerialName("service") val service: ServiceReferenceResponse?
) : ConversationMember()

@Serializable
data class SelfMember(
    @SerialName("userId") override val userId: String,
    @SerialName("service") val service: ServiceReferenceResponse?,
    @SerialName("hiddenReference") val hiddenReference: String?,
    @SerialName("otrMutedReference") val otrMutedReference: String?,
    @SerialName("hidden") val hidden: Boolean?,
    @SerialName("otrArchived") val otrArchived: Boolean?,
    @SerialName("otrMuted") val otrMuted: Boolean?,
    @SerialName("otrArchiveReference") val otrArchiveReference: String?
) : ConversationMember()

@Serializable
data class ServiceReferenceResponse(
    @SerialName("id") val id: String,
    @SerialName("provider") val provider: String
)

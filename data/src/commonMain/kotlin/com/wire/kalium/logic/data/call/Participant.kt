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

package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.type.UserType

data class Participant(
    val id: QualifiedID,
    val clientId: String,
    val name: String? = null,
    val isMuted: Boolean,
    val isCameraOn: Boolean,
    val isSpeaking: Boolean = false,
    val isSharingScreen: Boolean = false,
    val hasEstablishedAudio: Boolean,
    val avatarAssetId: UserAssetId? = null,
    val userType: UserType = UserType.NONE,
)

data class ParticipantMinimized(
    val id: QualifiedID,
    val clientId: String,
    val isMuted: Boolean,
    val isCameraOn: Boolean,
    val isSharingScreen: Boolean = false,
    val hasEstablishedAudio: Boolean,
)

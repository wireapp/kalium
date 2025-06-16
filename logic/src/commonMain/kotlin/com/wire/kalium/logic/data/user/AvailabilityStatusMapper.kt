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

package com.wire.kalium.logic.data.user

import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.protobuf.messages.Availability
import io.mockative.Mockable

@Mockable
interface AvailabilityStatusMapper {
    fun fromDaoAvailabilityStatusToModel(status: UserAvailabilityStatusEntity?): UserAvailabilityStatus
    fun fromModelAvailabilityStatusToDao(status: UserAvailabilityStatus): UserAvailabilityStatusEntity
    fun fromProtoAvailabilityToModel(status: Availability): UserAvailabilityStatus
    fun fromModelAvailabilityToProto(status: UserAvailabilityStatus): Availability
}

internal class AvailabilityStatusMapperImpl : AvailabilityStatusMapper {
    override fun fromDaoAvailabilityStatusToModel(status: UserAvailabilityStatusEntity?): UserAvailabilityStatus =
        when (status) {
            UserAvailabilityStatusEntity.AVAILABLE -> UserAvailabilityStatus.AVAILABLE
            UserAvailabilityStatusEntity.BUSY -> UserAvailabilityStatus.BUSY
            UserAvailabilityStatusEntity.AWAY -> UserAvailabilityStatus.AWAY
            UserAvailabilityStatusEntity.NONE -> UserAvailabilityStatus.NONE
            null -> UserAvailabilityStatus.NONE
        }

    override fun fromModelAvailabilityStatusToDao(status: UserAvailabilityStatus): UserAvailabilityStatusEntity =
        when (status) {
            UserAvailabilityStatus.AVAILABLE -> UserAvailabilityStatusEntity.AVAILABLE
            UserAvailabilityStatus.BUSY -> UserAvailabilityStatusEntity.BUSY
            UserAvailabilityStatus.AWAY -> UserAvailabilityStatusEntity.AWAY
            UserAvailabilityStatus.NONE -> UserAvailabilityStatusEntity.NONE
        }

    override fun fromProtoAvailabilityToModel(status: Availability): UserAvailabilityStatus =
        when (status.type) {
            Availability.Type.AVAILABLE -> UserAvailabilityStatus.AVAILABLE
            Availability.Type.BUSY -> UserAvailabilityStatus.BUSY
            Availability.Type.AWAY -> UserAvailabilityStatus.AWAY
            Availability.Type.NONE -> UserAvailabilityStatus.NONE
            is Availability.Type.UNRECOGNIZED -> UserAvailabilityStatus.NONE
        }

    override fun fromModelAvailabilityToProto(status: UserAvailabilityStatus): Availability {
        val type = when (status) {
            UserAvailabilityStatus.AVAILABLE -> Availability.Type.AVAILABLE
            UserAvailabilityStatus.BUSY -> Availability.Type.BUSY
            UserAvailabilityStatus.AWAY -> Availability.Type.AWAY
            UserAvailabilityStatus.NONE -> Availability.Type.NONE
        }

        return Availability(type)
    }
}

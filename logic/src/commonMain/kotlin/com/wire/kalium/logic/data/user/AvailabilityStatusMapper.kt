package com.wire.kalium.logic.data.user

import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity

interface AvailabilityStatusMapper {
    fun fromDaoAvailabilityStatusToUser(status: UserAvailabilityStatusEntity): UserAvailabilityStatus
    fun fromUserAvailabilityStatusToDao(status: UserAvailabilityStatus): UserAvailabilityStatusEntity
}

internal class AvailabilityStatusMapperImpl : AvailabilityStatusMapper {
    override fun fromDaoAvailabilityStatusToUser(status: UserAvailabilityStatusEntity): UserAvailabilityStatus =
        when (status) {
            UserAvailabilityStatusEntity.AVAILABLE -> UserAvailabilityStatus.AVAILABLE
            UserAvailabilityStatusEntity.BUSY -> UserAvailabilityStatus.BUSY
            UserAvailabilityStatusEntity.AWAY -> UserAvailabilityStatus.AWAY
            UserAvailabilityStatusEntity.NONE -> UserAvailabilityStatus.NONE
        }

    override fun fromUserAvailabilityStatusToDao(status: UserAvailabilityStatus): UserAvailabilityStatusEntity =
        when (status) {
            UserAvailabilityStatus.AVAILABLE -> UserAvailabilityStatusEntity.AVAILABLE
            UserAvailabilityStatus.BUSY -> UserAvailabilityStatusEntity.BUSY
            UserAvailabilityStatus.AWAY -> UserAvailabilityStatusEntity.AWAY
            UserAvailabilityStatus.NONE -> UserAvailabilityStatusEntity.NONE
        }
}

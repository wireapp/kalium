/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.BotService
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.OtherUserMinimized
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.base.model.UserProfileDTO
import com.wire.kalium.network.api.base.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.base.model.getPreviewAssetOrNull
import com.wire.kalium.persistence.dao.BotIdEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserEntityMinimized
import com.wire.kalium.persistence.dao.UserTypeEntity

interface PublicUserMapper {
    fun fromDaoModelToPublicUser(userEntity: UserEntity): OtherUser
    fun fromPublicUserToDaoModel(otherUser: OtherUser): UserEntity
    fun fromDaoModelToPublicUserMinimized(userEntity: UserEntityMinimized): OtherUserMinimized
    fun fromUserDetailResponseWithUsertype(
        userDetailResponse: UserProfileDTO,
        // UserProfileDTO has no info about userType, we need to pass it explicitly
        userType: UserType
    ): OtherUser

    // TODO:I think we are making too complicated parsers,
    // maybe a good solution will be fetching self user when we are saving other users to db?
    fun fromUserApiToEntityWithConnectionStateAndUserTypeEntity(
        userDetailResponse: UserProfileDTO,
        connectionState: ConnectionEntity.State,
        // UserProfileDTO has no info about userType, we need to pass it explicitly
        userTypeEntity: UserTypeEntity
    ): UserEntity
}

class PublicUserMapperImpl(
    private val idMapper: IdMapper,
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val connectionStateMapper: ConnectionStateMapper = MapperProvider.connectionStateMapper(),
    private val domainUserTypeMapper: DomainUserTypeMapper = MapperProvider.userTypeMapper(),
    private val userEntityTypeMapper: UserEntityTypeMapper = MapperProvider.userTypeEntityMapper()
) : PublicUserMapper {

    override fun fromDaoModelToPublicUser(userEntity: UserEntity) = OtherUser(
        id = userEntity.id.toModel(),
        name = userEntity.name,
        handle = userEntity.handle,
        email = userEntity.email,
        phone = userEntity.phone,
        accentId = userEntity.accentId,
        teamId = userEntity.team?.let { TeamId(it) },
        connectionStatus = connectionStateMapper.fromDaoConnectionStateToUser(connectionState = userEntity.connectionStatus),
        previewPicture = userEntity.previewAssetId?.toModel(),
        completePicture = userEntity.completeAssetId?.toModel(),
        availabilityStatus = availabilityStatusMapper.fromDaoAvailabilityStatusToModel(userEntity.availabilityStatus),
        userType = domainUserTypeMapper.fromUserTypeEntity(userEntity.userType),
        botService = userEntity.botService?.let { BotService(it.id, it.provider) },
        deleted = userEntity.deleted
    )

    override fun fromPublicUserToDaoModel(otherUser: OtherUser): UserEntity = with(otherUser) {
        UserEntity(
            id = id.toDao(),
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            accentId = accentId,
            team = teamId?.value,
            connectionStatus = connectionStateMapper.fromUserConnectionStateToDao(connectionStatus),
            previewAssetId = previewPicture?.toDao(),
            completeAssetId = completePicture?.toDao(),
            availabilityStatus = availabilityStatusMapper.fromModelAvailabilityStatusToDao(availabilityStatus),
            userType = userEntityTypeMapper.fromUserType(userType),
            botService = botService?.let { BotIdEntity(it.id, it.provider) },
            deleted = deleted
        )
    }

    override fun fromDaoModelToPublicUserMinimized(userEntity: UserEntityMinimized): OtherUserMinimized =
        OtherUserMinimized(
            id = userEntity.id.toModel(),
            name = userEntity.name,
            completePicture = userEntity.completeAssetId?.toModel(),
            userType = domainUserTypeMapper.fromUserTypeEntity(userEntity.userType),
        )

    override fun fromUserDetailResponseWithUsertype(
        userDetailResponse: UserProfileDTO,
        userType: UserType
    ) = OtherUser(
        id = UserId(userDetailResponse.id.value, userDetailResponse.id.domain),
        name = userDetailResponse.name,
        handle = userDetailResponse.handle,
        accentId = userDetailResponse.accentId,
        teamId = userDetailResponse.teamId?.let { TeamId(it) },
        connectionStatus = ConnectionState.NOT_CONNECTED,
        previewPicture = userDetailResponse.assets.getPreviewAssetOrNull()
            ?.let { QualifiedID(it.key, userDetailResponse.id.domain) },
        completePicture = userDetailResponse.assets.getCompleteAssetOrNull()
            ?.let { QualifiedID(it.key, userDetailResponse.id.domain) },
        availabilityStatus = UserAvailabilityStatus.NONE,
        userType = userType,
        botService = userDetailResponse.service?.let { BotService(it.id, it.provider) },
        deleted = userDetailResponse.deleted ?: false
    )

    override fun fromUserApiToEntityWithConnectionStateAndUserTypeEntity(
        userDetailResponse: UserProfileDTO,
        connectionState: ConnectionEntity.State,
        userTypeEntity: UserTypeEntity
    ) = UserEntity(
        id = idMapper.fromApiToDao(userDetailResponse.id),
        name = userDetailResponse.name,
        handle = userDetailResponse.handle,
        email = userDetailResponse.email,
        phone = null,
        accentId = userDetailResponse.accentId,
        team = userDetailResponse.teamId,
        previewAssetId = userDetailResponse.assets.getPreviewAssetOrNull()
            ?.let { QualifiedIDEntity(it.key, userDetailResponse.id.domain) },
        completeAssetId = userDetailResponse.assets.getCompleteAssetOrNull()
            ?.let { QualifiedIDEntity(it.key, userDetailResponse.id.domain) },
        connectionStatus = connectionState,
        availabilityStatus = UserAvailabilityStatusEntity.NONE,
        userType = userTypeEntity,
        botService = userDetailResponse.service?.let { BotIdEntity(it.id, it.provider) },
        deleted = userDetailResponse.deleted ?: false
    )

}

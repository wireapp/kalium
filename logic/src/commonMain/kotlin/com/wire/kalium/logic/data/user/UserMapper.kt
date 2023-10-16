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

package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.NetworkQualifiedId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.base.authenticated.self.UserUpdateRequest
import com.wire.kalium.network.api.base.model.AssetSizeDTO
import com.wire.kalium.network.api.base.model.SelfUserDTO
import com.wire.kalium.network.api.base.model.SupportedProtocolDTO
import com.wire.kalium.network.api.base.model.UserAssetDTO
import com.wire.kalium.network.api.base.model.UserAssetTypeDTO
import com.wire.kalium.network.api.base.model.UserProfileDTO
import com.wire.kalium.network.api.base.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.base.model.getPreviewAssetOrNull
import com.wire.kalium.persistence.dao.BotIdEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.PartialUserEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserDetailsEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import kotlinx.datetime.toInstant

@Suppress("TooManyFunctions")
interface UserMapper {
    fun fromSelfUserDtoToUserEntity(userDTO: SelfUserDTO): UserEntity
    fun fromUserEntityToSelfUser(userEntity: UserEntity): SelfUser
    fun fromUserDetailsEntityToSelfUser(userEntity: UserDetailsEntity): SelfUser
    fun fromSelfUserToUserEntity(selfUser: SelfUser): UserEntity

    /**
     * Maps the user data to be updated. if the parameters [newName] [newAccent] [newAssetId] are nulls,
     * it indicates that not updation should be made.
     *
     *  TODO(assets): handle deletion of assets references, emptyAssetList
     */
    fun fromModelToUpdateApiModel(
        user: SelfUser,
        newName: String?,
        newAccent: Int?,
        newAssetId: String?
    ): UserUpdateRequest

    fun fromUpdateRequestToDaoModel(
        user: SelfUser,
        updateRequest: UserUpdateRequest
    ): UserEntity

    fun fromUserUpdateEventToPartialUserEntity(event: Event.User.Update): PartialUserEntity

    fun fromUserProfileDtoToUserEntity(
        userProfile: UserProfileDTO,
        connectionState: ConnectionEntity.State,
        userTypeEntity: UserTypeEntity
    ): UserEntity

    fun fromFailedUserToEntity(userId: NetworkQualifiedId): UserEntity
    fun fromSelfUserToUserDetailsEntity(selfUser: SelfUser): UserDetailsEntity
    fun fromSelfUserDtoToUserDetailsEntity(userDTO: SelfUserDTO): UserDetailsEntity

    fun fromUserProfileDtoToUserDetailsEntity(
        userProfile: UserProfileDTO,
        connectionState: ConnectionEntity.State,
        userTypeEntity: UserTypeEntity
    ): UserDetailsEntity

    fun fromFailedUserToDetailsEntity(userId: NetworkQualifiedId): UserDetailsEntity
}

@Suppress("TooManyFunctions")
internal class UserMapperImpl(
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val connectionStateMapper: ConnectionStateMapper = MapperProvider.connectionStateMapper()
) : UserMapper {

    override fun fromUserEntityToSelfUser(userEntity: UserEntity) = with(userEntity) {
        SelfUser(
            id.toModel(),
            name,
            handle,
            email,
            phone,
            accentId,
            team?.let { TeamId(it) },
            connectionStateMapper.fromDaoConnectionStateToUser(connectionState = connectionStatus),
            previewAssetId?.toModel(),
            completeAssetId?.toModel(),
            availabilityStatusMapper.fromDaoAvailabilityStatusToModel(availabilityStatus),
            expiresAt = expiresAt,
            supportedProtocols?.toModel()
        )
    }

    override fun fromUserDetailsEntityToSelfUser(userEntity: UserDetailsEntity): SelfUser = with(userEntity) {
        SelfUser(
            id.toModel(),
            name,
            handle,
            email,
            phone,
            accentId,
            team?.let { TeamId(it) },
            connectionStateMapper.fromDaoConnectionStateToUser(connectionState = connectionStatus),
            previewAssetId?.toModel(),
            completeAssetId?.toModel(),
            availabilityStatusMapper.fromDaoAvailabilityStatusToModel(availabilityStatus),
            expiresAt = expiresAt,
            supportedProtocols?.toModel()
        )
    }

    override fun fromSelfUserToUserEntity(selfUser: SelfUser): UserEntity = with(selfUser) {
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
            userType = UserTypeEntity.STANDARD,
            botService = null,
            deleted = false,
            expiresAt = expiresAt,
            defederated = false,
            supportedProtocols = supportedProtocols?.toDao() ?: setOf(SupportedProtocolEntity.PROTEUS),
            activeOneOnOneConversationId = null
        )
    }

    override fun fromSelfUserToUserDetailsEntity(selfUser: SelfUser): UserDetailsEntity = with(selfUser) {
        UserDetailsEntity(
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
            userType = UserTypeEntity.STANDARD,
            botService = null,
            deleted = false,
            expiresAt = expiresAt,
            defederated = false,
            isProteusVerified = false,
            supportedProtocols = supportedProtocols?.toDao() ?: setOf(SupportedProtocolEntity.PROTEUS),
            activeOneOnOneConversationId = null
        )
    }

    override fun fromSelfUserDtoToUserEntity(userDTO: SelfUserDTO): UserEntity = with(userDTO) {
        UserEntity(
            id = idMapper.fromApiToDao(id),
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            accentId = accentId,
            team = teamId,
            previewAssetId = assets.getPreviewAssetOrNull()?.let { QualifiedIDEntity(it.key, id.domain) },
            completeAssetId = assets.getCompleteAssetOrNull()?.let { QualifiedIDEntity(it.key, id.domain) },
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userType = UserTypeEntity.STANDARD,
            botService = null,
            deleted = userDTO.deleted ?: false,
            expiresAt = expiresAt?.toInstant(),
            defederated = false,
            supportedProtocols = supportedProtocols?.toDao() ?: setOf(SupportedProtocolEntity.PROTEUS),
            activeOneOnOneConversationId = null
        )
    }

    override fun fromSelfUserDtoToUserDetailsEntity(userDTO: SelfUserDTO): UserDetailsEntity = with(userDTO) {
        UserDetailsEntity(
            id = idMapper.fromApiToDao(id),
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            accentId = accentId,
            team = teamId,
            previewAssetId = assets.getPreviewAssetOrNull()?.let { QualifiedIDEntity(it.key, id.domain) },
            completeAssetId = assets.getCompleteAssetOrNull()?.let { QualifiedIDEntity(it.key, id.domain) },
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userType = UserTypeEntity.STANDARD,
            botService = null,
            deleted = userDTO.deleted ?: false,
            expiresAt = expiresAt?.toInstant(),
            defederated = false,
            isProteusVerified = false,
            supportedProtocols = supportedProtocols?.toDao() ?: setOf(SupportedProtocolEntity.PROTEUS),
            activeOneOnOneConversationId = null
        )
    }

    override fun fromModelToUpdateApiModel(
        user: SelfUser,
        newName: String?,
        newAccent: Int?,
        newAssetId: String?
    ): UserUpdateRequest {
        return UserUpdateRequest(
            name = newName, accentId = newAccent, assets = if (newAssetId != null) {
                listOf(
                    UserAssetDTO(newAssetId, AssetSizeDTO.COMPLETE, UserAssetTypeDTO.IMAGE),
                    UserAssetDTO(newAssetId, AssetSizeDTO.PREVIEW, UserAssetTypeDTO.IMAGE)
                )
            } else {
                null
            }
        )
    }

    override fun fromUpdateRequestToDaoModel(
        user: SelfUser,
        updateRequest: UserUpdateRequest
    ): UserEntity =
        fromSelfUserToUserEntity(
            user.copy(
                previewPicture = updateRequest.assets.getPreviewAssetOrNull()?.toModel(user.id.domain),
                completePicture = updateRequest.assets.getCompleteAssetOrNull()?.toModel(user.id.domain)
            )
        )

    override fun fromUserProfileDtoToUserEntity(
        userProfile: UserProfileDTO,
        connectionState: ConnectionEntity.State,
        userTypeEntity: UserTypeEntity
    ) = UserEntity(
        id = idMapper.fromApiToDao(userProfile.id),
        name = userProfile.name,
        handle = userProfile.handle,
        email = userProfile.email,
        phone = null,
        accentId = userProfile.accentId,
        team = userProfile.teamId,
        previewAssetId = userProfile.assets.getPreviewAssetOrNull()
            ?.let { QualifiedIDEntity(it.key, userProfile.id.domain) },
        completeAssetId = userProfile.assets.getCompleteAssetOrNull()
            ?.let { QualifiedIDEntity(it.key, userProfile.id.domain) },
        connectionStatus = connectionState,
        availabilityStatus = UserAvailabilityStatusEntity.NONE,
        userType = userTypeEntity,
        botService = userProfile.service?.let { BotIdEntity(it.id, it.provider) },
        deleted = userProfile.deleted ?: false,
        expiresAt = userProfile.expiresAt?.toInstant(),
        defederated = false,
        supportedProtocols = userProfile.supportedProtocols?.toDao() ?: setOf(SupportedProtocolEntity.PROTEUS),
        activeOneOnOneConversationId = null
    )

    override fun fromUserProfileDtoToUserDetailsEntity(
        userProfile: UserProfileDTO,
        connectionState: ConnectionEntity.State,
        userTypeEntity: UserTypeEntity
    ) = UserDetailsEntity(
        id = idMapper.fromApiToDao(userProfile.id),
        name = userProfile.name,
        handle = userProfile.handle,
        email = userProfile.email,
        phone = null,
        accentId = userProfile.accentId,
        team = userProfile.teamId,
        previewAssetId = userProfile.assets.getPreviewAssetOrNull()
            ?.let { QualifiedIDEntity(it.key, userProfile.id.domain) },
        completeAssetId = userProfile.assets.getCompleteAssetOrNull()
            ?.let { QualifiedIDEntity(it.key, userProfile.id.domain) },
        connectionStatus = connectionState,
        availabilityStatus = UserAvailabilityStatusEntity.NONE,
        userType = userTypeEntity,
        botService = userProfile.service?.let { BotIdEntity(it.id, it.provider) },
        deleted = userProfile.deleted ?: false,
        expiresAt = userProfile.expiresAt?.toInstant(),
        defederated = false,
        isProteusVerified = false,
        supportedProtocols = userProfile.supportedProtocols?.toDao() ?: setOf(SupportedProtocolEntity.PROTEUS),
        activeOneOnOneConversationId = null
    )

    override fun fromUserUpdateEventToPartialUserEntity(event: Event.User.Update): PartialUserEntity =
        PartialUserEntity(
            email = event.email,
            name = event.name,
            handle = event.handle,
            accentId = event.accentId,
            previewAssetId = event.previewAssetId?.let { QualifiedIDEntity(it, event.userId.domain) },
            completeAssetId = event.completeAssetId?.let { QualifiedIDEntity(it, event.userId.domain) },
            supportedProtocols = event.supportedProtocols?.toDao()
        )

    /**
     * Default values and marked as [UserEntity.hasIncompleteMetadata] = true.
     * So later we can re-fetch them.
     */
    override fun fromFailedUserToEntity(userId: NetworkQualifiedId): UserEntity {
        return UserEntity(
            id = userId.toDao(),
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 1,
            team = null,
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            previewAssetId = null,
            completeAssetId = null,
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userType = UserTypeEntity.STANDARD,
            botService = null,
            deleted = false,
            hasIncompleteMetadata = true,
            expiresAt = null,
            defederated = false,
            supportedProtocols = null,
            activeOneOnOneConversationId = null
        )
    }

    override fun fromFailedUserToDetailsEntity(userId: NetworkQualifiedId): UserDetailsEntity {
        return UserDetailsEntity(
            id = userId.toDao(),
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 1,
            team = null,
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            previewAssetId = null,
            completeAssetId = null,
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userType = UserTypeEntity.STANDARD,
            botService = null,
            deleted = false,
            hasIncompleteMetadata = true,
            expiresAt = null,
            defederated = false,
            isProteusVerified = false,
            supportedProtocols = null,
            activeOneOnOneConversationId = null
        )
    }
}

fun SupportedProtocol.toApi() = when (this) {
    SupportedProtocol.MLS -> SupportedProtocolDTO.MLS
    SupportedProtocol.PROTEUS -> SupportedProtocolDTO.PROTEUS
}

fun SupportedProtocol.toDao() = when (this) {
    SupportedProtocol.MLS -> SupportedProtocolEntity.MLS
    SupportedProtocol.PROTEUS -> SupportedProtocolEntity.PROTEUS
}

fun SupportedProtocolDTO.toModel() = when (this) {
    SupportedProtocolDTO.MLS -> SupportedProtocol.MLS
    SupportedProtocolDTO.PROTEUS -> SupportedProtocol.PROTEUS
}

fun SupportedProtocolDTO.toDao() = when (this) {
    SupportedProtocolDTO.MLS -> SupportedProtocolEntity.MLS
    SupportedProtocolDTO.PROTEUS -> SupportedProtocolEntity.PROTEUS
}

fun SupportedProtocolEntity.toModel() = when (this) {
    SupportedProtocolEntity.MLS -> SupportedProtocol.MLS
    SupportedProtocolEntity.PROTEUS -> SupportedProtocol.PROTEUS
}

fun List<SupportedProtocolDTO>.toDao() = this.map { it.toDao() }.toSet()
fun List<SupportedProtocolDTO>.toModel() = this.map { it.toModel() }.toSet()
fun Set<SupportedProtocol>.toDao() = this.map { it.toDao() }.toSet()
fun Set<SupportedProtocolEntity>.toModel() = this.map { it.toModel() }.toSet()

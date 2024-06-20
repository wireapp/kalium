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

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.NetworkQualifiedId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.UserSummary
import com.wire.kalium.logic.data.publicuser.model.UserSearchDetails
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.self.UserUpdateRequest
import com.wire.kalium.network.api.model.AssetSizeDTO
import com.wire.kalium.network.api.model.SelfUserDTO
import com.wire.kalium.network.api.model.SupportedProtocolDTO
import com.wire.kalium.network.api.model.UserAssetDTO
import com.wire.kalium.network.api.model.UserAssetTypeDTO
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.api.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.model.getPreviewAssetOrNull
import com.wire.kalium.persistence.dao.BotIdEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.PartialUserEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserDetailsEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserEntityMinimized
import com.wire.kalium.persistence.dao.UserSearchEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import kotlinx.datetime.toInstant

@Suppress("TooManyFunctions")
interface UserMapper {
    fun fromSelfUserToUserEntity(selfUser: SelfUser): UserEntity
    fun fromOtherToUserEntity(otherUser: OtherUser): UserEntity
    fun fromUserEntityToSelfUser(userEntity: UserEntity): SelfUser
    fun fromUserDetailsEntityToSelfUser(userEntity: UserDetailsEntity): SelfUser
    fun fromUserEntityToOtherUser(userEntity: UserEntity): OtherUser
    fun fromUserDetailsEntityToOtherUser(userEntity: UserDetailsEntity): OtherUser
    fun fromUserEntityToOtherUserMinimized(userEntity: UserEntityMinimized): OtherUserMinimized
    fun fromEntityToUserSummary(userEntity: UserEntity): UserSummary

    /**
     * Maps the user data to be updated. if the parameters [newName] [newAccent] [newAssetId] are nulls,
     * it indicates that not updation should be made.
     *
     *  TODO(assets): handle deletion of assets references, emptyAssetList
     */
    fun fromModelToUpdateApiModel(
        newName: String?,
        newAccent: Int?,
        newAssetId: String?
    ): UserUpdateRequest

    fun fromUpdateRequestToPartialUserEntity(
        updateRequest: UserUpdateRequest,
        selfUserId: UserId,
    ): PartialUserEntity

    fun fromUserUpdateEventToPartialUserEntity(event: Event.User.Update): PartialUserEntity

    fun fromSelfUserDtoToUserEntity(
        userDTO: SelfUserDTO,
        connectionState: ConnectionEntity.State,
        userTypeEntity: UserTypeEntity
    ): UserEntity

    fun fromUserProfileDtoToUserEntity(
        userProfile: UserProfileDTO,
        connectionState: ConnectionEntity.State,
        userTypeEntity: UserTypeEntity
    ): UserEntity

    fun fromUserProfileDtoToOtherUser(userProfile: UserProfileDTO, selfUserId: UserId, selfTeamId: TeamId?): OtherUser

    fun fromFailedUserToEntity(userId: NetworkQualifiedId): UserEntity
    fun fromSearchEntityToUserSearchDetails(searchEntity: UserSearchEntity): UserSearchDetails
}

@Suppress("TooManyFunctions")
internal class UserMapperImpl(
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val domainUserTypeMapper: DomainUserTypeMapper = MapperProvider.userTypeMapper(),
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val connectionStateMapper: ConnectionStateMapper = MapperProvider.connectionStateMapper(),
    private val userEntityTypeMapper: UserEntityTypeMapper = MapperProvider.userTypeEntityMapper()
) : UserMapper {

    override fun fromUserEntityToSelfUser(userEntity: UserEntity) = with(userEntity) {
        SelfUser(
            id = id.toModel(),
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            accentId = accentId,
            teamId = team?.let { TeamId(it) },
            connectionStatus = connectionStateMapper.fromDaoConnectionStateToUser(connectionState = connectionStatus),
            previewPicture = previewAssetId?.toModel(),
            completePicture = completeAssetId?.toModel(),
            userType = domainUserTypeMapper.fromUserTypeEntity(userEntity.userType),
            availabilityStatus = availabilityStatusMapper.fromDaoAvailabilityStatusToModel(availabilityStatus),
            expiresAt = expiresAt,
            supportedProtocols = supportedProtocols?.toModel()
        )
    }

    override fun fromUserDetailsEntityToSelfUser(userEntity: UserDetailsEntity): SelfUser = with(userEntity) {
        SelfUser(
            id = id.toModel(),
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            accentId = accentId,
            teamId = team?.let { TeamId(it) },
            connectionStatus = connectionStateMapper.fromDaoConnectionStateToUser(connectionState = connectionStatus),
            previewPicture = previewAssetId?.toModel(),
            completePicture = completeAssetId?.toModel(),
            userType = domainUserTypeMapper.fromUserTypeEntity(userEntity.userType),
            availabilityStatus = availabilityStatusMapper.fromDaoAvailabilityStatusToModel(availabilityStatus),
            expiresAt = expiresAt,
            supportedProtocols = supportedProtocols?.toModel(),
            isUnderLegalHold = userEntity.isUnderLegalHold,
        )
    }

    override fun fromUserEntityToOtherUser(userEntity: UserEntity) = OtherUser(
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
        deleted = userEntity.deleted,
        expiresAt = userEntity.expiresAt,
        defederated = userEntity.defederated,
        isProteusVerified = false,
        supportedProtocols = userEntity.supportedProtocols?.toModel(),
        activeOneOnOneConversationId = userEntity.activeOneOnOneConversationId?.toModel()
    )

    override fun fromUserDetailsEntityToOtherUser(userEntity: UserDetailsEntity): OtherUser = OtherUser(
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
        deleted = userEntity.deleted,
        expiresAt = userEntity.expiresAt,
        defederated = userEntity.defederated,
        isProteusVerified = userEntity.isProteusVerified,
        supportedProtocols = userEntity.supportedProtocols?.toModel(),
        activeOneOnOneConversationId = userEntity.activeOneOnOneConversationId?.toModel(),
        isUnderLegalHold = userEntity.isUnderLegalHold,
    )

    override fun fromUserEntityToOtherUserMinimized(userEntity: UserEntityMinimized): OtherUserMinimized =
        OtherUserMinimized(
            id = userEntity.id.toModel(),
            name = userEntity.name,
            completePicture = userEntity.completeAssetId?.toModel(),
            userType = domainUserTypeMapper.fromUserTypeEntity(userEntity.userType),
        )

    override fun fromEntityToUserSummary(userEntity: UserEntity) = with(userEntity) {
        UserSummary(
            userId = UserId(id.value, id.domain),
            userHandle = handle,
            userName = name,
            userPreviewAssetId = previewAssetId?.toModel(),
            userType = domainUserTypeMapper.fromUserTypeEntity(userType),
            isUserDeleted = deleted,
            availabilityStatus = availabilityStatusMapper.fromDaoAvailabilityStatusToModel(availabilityStatus),
            connectionStatus = connectionStateMapper.fromDaoConnectionStateToUser(connectionStatus)
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

    override fun fromOtherToUserEntity(otherUser: OtherUser): UserEntity = with(otherUser) {
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
            deleted = deleted,
            expiresAt = expiresAt,
            hasIncompleteMetadata = false,
            defederated = defederated,
            supportedProtocols = supportedProtocols?.toDao(),
            activeOneOnOneConversationId = activeOneOnOneConversationId?.toDao()
        )
    }

    override fun fromSelfUserDtoToUserEntity(
        userDTO: SelfUserDTO,
        connectionState: ConnectionEntity.State,
        userTypeEntity: UserTypeEntity
    ): UserEntity = with(userDTO) {
        UserEntity(
            id = idMapper.fromApiToDao(id),
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            accentId = accentId,
            team = teamId,
            connectionStatus = connectionState,
            previewAssetId = assets.getPreviewAssetOrNull()?.let { QualifiedIDEntity(it.key, id.domain) },
            completeAssetId = assets.getCompleteAssetOrNull()?.let { QualifiedIDEntity(it.key, id.domain) },
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userType = userTypeEntity,
            botService = null,
            deleted = userDTO.deleted ?: false,
            expiresAt = expiresAt?.toInstant(),
            defederated = false,
            supportedProtocols = supportedProtocols?.toDao() ?: setOf(SupportedProtocolEntity.PROTEUS),
            activeOneOnOneConversationId = null,
        )
    }

    override fun fromModelToUpdateApiModel(
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

    override fun fromUpdateRequestToPartialUserEntity(
        updateRequest: UserUpdateRequest,
        selfUserId: UserId,
    ): PartialUserEntity = PartialUserEntity(
        previewAssetId = updateRequest.assets.getPreviewAssetOrNull()?.toDao(selfUserId.domain),
        completeAssetId = updateRequest.assets.getCompleteAssetOrNull()?.toDao(selfUserId.domain),
        id = selfUserId.toDao()
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

    override fun fromUserProfileDtoToOtherUser(userProfile: UserProfileDTO, selfUserId: UserId, selfTeamId: TeamId?): OtherUser =
        OtherUser(
            id = userProfile.id.toModel(),
            name = userProfile.name,
            handle = userProfile.handle,
            email = userProfile.email,
            phone = null,
            accentId = userProfile.accentId,
            teamId = userProfile.teamId?.let { TeamId(it) },
            connectionStatus = if (selfTeamId != null && selfTeamId.value == userProfile.teamId) {
                ConnectionState.ACCEPTED
            } else {
                ConnectionState.NOT_CONNECTED
            },
            previewPicture = userProfile.assets.getPreviewAssetOrNull()
                ?.let { QualifiedIDEntity(it.key, userProfile.id.domain) }?.toModel(),
            completePicture = userProfile.assets.getCompleteAssetOrNull()
                ?.let { QualifiedIDEntity(it.key, userProfile.id.domain) }?.toModel(),
            availabilityStatus = UserAvailabilityStatus.NONE,
            userType = domainUserTypeMapper.fromTeamAndDomain(
                otherUserDomain = userProfile.id.domain,
                selfUserTeamId = selfTeamId?.value,
                selfUserDomain = selfUserId.domain,
                otherUserTeamId = userProfile.teamId,
                isService = userProfile.service != null
            ),
            botService = userProfile.service?.let { BotService(it.id, it.provider) },
            deleted = userProfile.deleted ?: false,
            expiresAt = userProfile.expiresAt?.toInstant(),
            defederated = false,
            isProteusVerified = false,
            supportedProtocols = userProfile.supportedProtocols?.toModel() ?: setOf(SupportedProtocol.PROTEUS),
        )

    override fun fromUserUpdateEventToPartialUserEntity(event: Event.User.Update): PartialUserEntity =
        PartialUserEntity(
            email = event.email,
            name = event.name,
            handle = event.handle,
            accentId = event.accentId,
            previewAssetId = event.previewAssetId?.let { QualifiedIDEntity(it, event.userId.domain) },
            completeAssetId = event.completeAssetId?.let { QualifiedIDEntity(it, event.userId.domain) },
            supportedProtocols = event.supportedProtocols?.toDao(),
            id = event.userId.toDao()
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

    override fun fromSearchEntityToUserSearchDetails(searchEntity: UserSearchEntity) = UserSearchDetails(
        id = searchEntity.id.toModel(),
        name = searchEntity.name,
        completeAssetId = searchEntity.completeAssetId?.toModel(),
        previewAssetId = searchEntity.previewAssetId?.toModel(),
        type = domainUserTypeMapper.fromUserTypeEntity(searchEntity.type),
        connectionStatus = connectionStateMapper.fromDaoConnectionStateToUser(searchEntity.connectionStatus),
        handle = searchEntity.handle
    )
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

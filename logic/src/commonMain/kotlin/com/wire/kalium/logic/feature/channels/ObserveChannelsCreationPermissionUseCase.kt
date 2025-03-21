/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.channels

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.combine
import com.wire.kalium.logic.configuration.ChannelsConfigurationStorage
import com.wire.kalium.logic.data.featureConfig.ChannelFeatureConfiguration
import com.wire.kalium.logic.data.user.SelfUserObservationProvider
import com.wire.kalium.logic.data.user.type.UserType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Use case for observing the channel configuration status
 * based on persisted settings and user permissions.
 *
 * This use case combines channel feature configuration with user permissions to determine
 * whether channels are enabled and what operations the current user can perform.
 */
class ObserveChannelsCreationPermissionUseCase internal constructor(
    private val channelsConfigStorage: ChannelsConfigurationStorage,
    private val selfUserObservationProvider: SelfUserObservationProvider
) {
    /**
     * Retrieves the current channels configuration status.
     *
     * @return Either a [CoreFailure] if the operation fails, or a [ChannelCreationPermission]
     * indicating whether channels are enabled and what operations the current user can perform
     */
    suspend operator fun invoke(): Flow<ChannelCreationPermission> = channelsConfigStorage.observePersistedChannelsConfiguration()
        .combine(selfUserObservationProvider.observeSelfUser())
        .map { (channelFeatureConfig, selfUser) ->
            when (channelFeatureConfig) {
                null, ChannelFeatureConfiguration.Disabled -> ChannelCreationPermission.Forbidden
                is ChannelFeatureConfiguration.Enabled -> {
                    val canUserCreateChannels = isUserAllowed(
                        selfUser.userType,
                        channelFeatureConfig.createChannelsRequirement
                    )
                    if (!canUserCreateChannels) {
                        ChannelCreationPermission.Forbidden
                    } else {
                        ChannelCreationPermission.Allowed(
                            isUserAllowed(
                                selfUser.userType,
                                channelFeatureConfig.createPublicChannelsRequirement
                            )
                        )
                    }
                }
            }
        }.distinctUntilChanged()

    /**
     * Determines if a user is allowed to perform channel operations based on their type and requirements.
     *
     * @param userType The type of the user being checked
     * @param requirement The required team user type for the operation
     * @return true if the user is allowed, false otherwise
     */
    private fun isUserAllowed(userType: UserType, requirement: ChannelFeatureConfiguration.TeamUserType): Boolean {
        val admins = setOf(UserType.ADMIN, UserType.OWNER)
        val regularTeamMembers = admins + UserType.INTERNAL
        val wholeTeam = regularTeamMembers + UserType.EXTERNAL
        return when (requirement) {
            ChannelFeatureConfiguration.TeamUserType.ADMINS_ONLY -> userType in admins
            ChannelFeatureConfiguration.TeamUserType.ADMINS_AND_REGULAR_MEMBERS -> userType in regularTeamMembers
            ChannelFeatureConfiguration.TeamUserType.EVERYONE_IN_THE_TEAM -> userType in wholeTeam
        }
    }
}

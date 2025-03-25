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

import com.wire.kalium.logic.configuration.ChannelsConfigurationStorage
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.SelfUserObservationProvider
import com.wire.kalium.logic.feature.conversation.channel.UpdateChannelPermissionUseCase
import com.wire.kalium.logic.feature.conversation.channel.UpdateChannelPermissionUseCaseImpl
import com.wire.kalium.persistence.dao.MetadataDAO

class ChannelsScope(
    val conversationRepository: () -> ConversationRepository,
    val metadataDaoProvider: () -> MetadataDAO,
    val selfUserObservationProvider: () -> SelfUserObservationProvider
) {
    internal val channelsConfigStorage: ChannelsConfigurationStorage
        get() = ChannelsConfigurationStorage(metadataDaoProvider())

    internal val channelsFeatureConfigHandler: ChannelsFeatureConfigurationHandler
        get() = ChannelsFeatureConfigurationHandler(channelsConfigStorage)

    val observeChannelsCreationPermissionUseCase: ObserveChannelsCreationPermissionUseCase
        get() = ObserveChannelsCreationPermissionUseCase(channelsConfigStorage, selfUserObservationProvider())

    val updateChannelPermission: UpdateChannelPermissionUseCase
        get() = UpdateChannelPermissionUseCaseImpl(conversationRepository())
}

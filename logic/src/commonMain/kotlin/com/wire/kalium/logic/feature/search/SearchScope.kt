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
package com.wire.kalium.logic.feature.search

import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.GetConversationProtocolInfoUseCase
import com.wire.kalium.logic.feature.user.GetDefaultProtocolUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs

@Suppress("LongParameterList")
class SearchScope internal constructor(
    private val mlsPublicKeysRepository: MLSPublicKeysRepository,
    private val getDefaultProtocol: GetDefaultProtocolUseCase,
    private val getConversationProtocolInfo: GetConversationProtocolInfoUseCase,
    private val searchUserRepository: SearchUserRepository,
    private val sessionRepository: SessionRepository,
    private val selfUserId: UserId,
    private val kaliumConfigs: KaliumConfigs
) {
    val searchUsers: SearchUsersUseCase
        get() = SearchUsersUseCaseImpl(
            searchUserRepository,
            selfUserId,
            kaliumConfigs.maxRemoteSearchResultCount
        )

    val searchByHandle: SearchByHandleUseCase
        get() = SearchByHandleUseCase(
            searchUserRepository,
            selfUserId,
            kaliumConfigs.maxRemoteSearchResultCount
        )
    val federatedSearchParser: FederatedSearchParser get() = FederatedSearchParser(sessionRepository, selfUserId)

    val isFederationSearchAllowedUseCase: IsFederationSearchAllowedUseCase
        get() = IsFederationSearchAllowedUseCase(mlsPublicKeysRepository, getDefaultProtocol, getConversationProtocolInfo)
}

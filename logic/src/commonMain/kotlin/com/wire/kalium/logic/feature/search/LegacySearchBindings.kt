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
import com.wire.kalium.logic.di.KaliumSearchApi
import com.wire.kalium.logic.di.UserLifetime
import com.wire.kalium.logic.feature.conversation.GetConversationProtocolInfoUseCase
import com.wire.kalium.logic.feature.user.GetDefaultProtocolUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn

/** Separates cached facade captures from other user factories and same-typed captures. */
@Qualifier
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.VALUE_PARAMETER)
internal annotation class LegacySearchBinding(val value: String)

/**
 * Adapts session accessors without reading them eagerly. Each capture is retained independently.
 * The protocol-info input is one captured conversation binding; default protocol is a separate session factory capture.
 *
 * Session adapter mapping (all are getters, not eagerly evaluated adapter fields):
 * `mlsPublicKeysRepository -> this@UserSessionScope.mlsPublicKeysRepository`;
 * `getDefaultProtocol -> this@UserSessionScope.getDefaultProtocol` (separate factory capture);
 * `getConversationProtocolInfo -> kaliumUserGraph.conversationGetConversationProtocolInfo`;
 * `searchUserRepository -> this@UserSessionScope.searchUserRepository`;
 * `sessionRepository -> globalScope.sessionRepository`; `selfUserId -> userId`; `kaliumConfigs -> kaliumConfigs`.
 */
internal interface LegacySearchDependencies {
    val mlsPublicKeysRepository: MLSPublicKeysRepository
    val getDefaultProtocol: GetDefaultProtocolUseCase
    val getConversationProtocolInfo: GetConversationProtocolInfoUseCase
    val searchUserRepository: SearchUserRepository
    val sessionRepository: SessionRepository
    val selfUserId: UserId
    val kaliumConfigs: KaliumConfigs
}

/** Individual entry points for the compatibility facade and user runtime. */
internal interface LegacySearchApi : KaliumSearchApi {
    @get:LegacySearchBinding("mlsPublicKeysRepository")
    val searchMlsPublicKeysRepository: MLSPublicKeysRepository

    @get:LegacySearchBinding("getDefaultProtocol")
    val searchGetDefaultProtocol: GetDefaultProtocolUseCase

    @get:LegacySearchBinding("getConversationProtocolInfo")
    val searchGetConversationProtocolInfo: GetConversationProtocolInfoUseCase

    @get:LegacySearchBinding("searchUserRepository")
    val searchSearchUserRepository: SearchUserRepository

    @get:LegacySearchBinding("sessionRepository")
    val searchSessionRepository: SessionRepository

    @get:LegacySearchBinding("selfUserId")
    val searchSelfUserId: UserId

    @get:LegacySearchBinding("kaliumConfigs")
    val searchKaliumConfigs: KaliumConfigs

    @get:LegacySearchBinding("searchUsersByName")
    override val searchSearchUsersByName: SearchUsersByNameUseCase

    @get:LegacySearchBinding("searchUsersByHandle")
    override val searchSearchUsersByHandle: SearchUsersByHandleUseCase

    @get:LegacySearchBinding("federatedSearchParser")
    override val searchFederatedSearchParser: FederatedSearchParser

    @get:LegacySearchBinding("isFederationSearchAllowedUseCase")
    override val searchIsFederationSearchAllowedUseCase: IsFederationSearchAllowedUseCase
}

/**
 * Mixed into the user graph: captures and formerly retained values use the existing user lifetime.
 * All original factory getters remain unscoped. No feature graph or broad facade constructor is used.
 */
internal interface LegacySearchBindings : LegacySearchApi {
    @Provides
    @SingleIn(UserLifetime::class)
    @LegacySearchBinding("mlsPublicKeysRepository")
    fun provideSearchMlsPublicKeysRepository(dependencies: LegacySearchDependencies): MLSPublicKeysRepository =
        dependencies.mlsPublicKeysRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacySearchBinding("getDefaultProtocol")
    fun provideSearchGetDefaultProtocol(dependencies: LegacySearchDependencies): GetDefaultProtocolUseCase =
        dependencies.getDefaultProtocol

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacySearchBinding("getConversationProtocolInfo")
    fun provideSearchGetConversationProtocolInfo(dependencies: LegacySearchDependencies): GetConversationProtocolInfoUseCase =
        dependencies.getConversationProtocolInfo

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacySearchBinding("searchUserRepository")
    fun provideSearchSearchUserRepository(dependencies: LegacySearchDependencies): SearchUserRepository =
        dependencies.searchUserRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacySearchBinding("sessionRepository")
    fun provideSearchSessionRepository(dependencies: LegacySearchDependencies): SessionRepository =
        dependencies.sessionRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacySearchBinding("selfUserId")
    fun provideSearchSelfUserId(dependencies: LegacySearchDependencies): UserId =
        dependencies.selfUserId

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacySearchBinding("kaliumConfigs")
    fun provideSearchKaliumConfigs(dependencies: LegacySearchDependencies): KaliumConfigs =
        dependencies.kaliumConfigs

    @Provides
    @LegacySearchBinding("searchUsersByName")
    fun provideSearchSearchUsersByName(
        @LegacySearchBinding("searchUserRepository") searchUserRepository: SearchUserRepository,
        @LegacySearchBinding("selfUserId") selfUserId: UserId,
        @LegacySearchBinding("kaliumConfigs") kaliumConfigs: KaliumConfigs,
    ): SearchUsersByNameUseCase = SearchUsersByNameUseCaseImpl(
        searchUserRepository,
        selfUserId,
        kaliumConfigs.maxRemoteSearchResultCount
    )

    @Provides
    @LegacySearchBinding("searchUsersByHandle")
    fun provideSearchSearchUsersByHandle(
        @LegacySearchBinding("searchUserRepository") searchUserRepository: SearchUserRepository,
        @LegacySearchBinding("selfUserId") selfUserId: UserId,
        @LegacySearchBinding("kaliumConfigs") kaliumConfigs: KaliumConfigs,
    ): SearchUsersByHandleUseCase = SearchUsersByHandleUseCaseImpl(
        searchUserRepository,
        selfUserId,
        kaliumConfigs.maxRemoteSearchResultCount
    )

    @Provides
    @LegacySearchBinding("federatedSearchParser")
    fun provideSearchFederatedSearchParser(
        @LegacySearchBinding("sessionRepository") sessionRepository: SessionRepository,
        @LegacySearchBinding("selfUserId") selfUserId: UserId,
    ): FederatedSearchParser = FederatedSearchParser(sessionRepository, selfUserId)

    @Provides
    @LegacySearchBinding("isFederationSearchAllowedUseCase")
    fun provideSearchIsFederationSearchAllowedUseCase(
        @LegacySearchBinding("mlsPublicKeysRepository") mlsPublicKeysRepository: MLSPublicKeysRepository,
        @LegacySearchBinding("getDefaultProtocol") getDefaultProtocol: GetDefaultProtocolUseCase,
        @LegacySearchBinding("getConversationProtocolInfo") getConversationProtocolInfo: GetConversationProtocolInfoUseCase,
    ): IsFederationSearchAllowedUseCase =
        IsFederationSearchAllowedUseCase(mlsPublicKeysRepository, getDefaultProtocol, getConversationProtocolInfo)
}

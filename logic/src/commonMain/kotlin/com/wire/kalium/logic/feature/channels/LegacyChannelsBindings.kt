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

// Composition supplies the metadata port; use-case implementations retain their layer checks.
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature.channels

import com.wire.kalium.logic.configuration.ChannelsConfigurationStorage
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.SelfUserObservationProvider
import com.wire.kalium.logic.di.UserLifetime
import com.wire.kalium.logic.feature.conversation.channel.UpdateChannelAddPermissionUseCase
import com.wire.kalium.logic.feature.conversation.channel.UpdateChannelAddPermissionUseCaseImpl
import com.wire.kalium.logic.feature.user.GetSelfUserUseCase
import com.wire.kalium.persistence.dao.MetadataDAO
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn

/** Separates cached facade captures from other user factories and same-typed captures. */
@Qualifier
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.VALUE_PARAMETER)
internal annotation class LegacyChannelsBinding(val value: String)

/**
 * Adapts session accessors without reading them eagerly. Each capture is retained independently.
 * These four inputs are raw function values. Capture each function once, but never memoize its result.
 *
 * Session adapter getters return the original functions:
 * `selfUser -> { users.getSelfUser }`; `conversationRepository -> { this@UserSessionScope.conversationRepository }`;
 * `metadataDaoProvider -> { userStorage.database.metadataDAO }`;
 * `selfUserObservationProvider -> { this@UserSessionScope.userRepository }`.
 * Configuration sync can request `channelsChannelsFeatureConfigHandler` directly, without a facade.
 */
internal interface LegacyChannelsDependencies {
    val selfUser: () -> GetSelfUserUseCase
    val conversationRepository: () -> ConversationRepository
    val metadataDaoProvider: () -> MetadataDAO
    val selfUserObservationProvider: () -> SelfUserObservationProvider
}

/** Keeps an original function as a port value instead of Metro deferred injection. */
internal class LegacyChannelsCapture<out T>(val value: T)

/** Individual entry points for the compatibility facade and user runtime. */
internal interface LegacyChannelsApi {
    @get:LegacyChannelsBinding("selfUser")
    val channelsSelfUser: LegacyChannelsCapture<() -> GetSelfUserUseCase>

    @get:LegacyChannelsBinding("conversationRepository")
    val channelsConversationRepository: LegacyChannelsCapture<() -> ConversationRepository>

    @get:LegacyChannelsBinding("metadataDaoProvider")
    val channelsMetadataDaoProvider: LegacyChannelsCapture<() -> MetadataDAO>

    @get:LegacyChannelsBinding("selfUserObservationProvider")
    val channelsSelfUserObservationProvider: LegacyChannelsCapture<() -> SelfUserObservationProvider>

    @get:LegacyChannelsBinding("channelsConfigStorage")
    val channelsChannelsConfigStorage: ChannelsConfigurationStorage

    @get:LegacyChannelsBinding("channelsFeatureConfigHandler")
    val channelsChannelsFeatureConfigHandler: ChannelsFeatureConfigurationHandler

    @get:LegacyChannelsBinding("observeChannelsCreationPermissionUseCase")
    val channelsObserveChannelsCreationPermissionUseCase: ObserveChannelsCreationPermissionUseCase

    @get:LegacyChannelsBinding("updateChannelAddPermission")
    val channelsUpdateChannelAddPermission: UpdateChannelAddPermissionUseCase
}

/**
 * Mixed into the user graph: captures and formerly retained values use the existing user lifetime.
 * All original factory getters remain unscoped. No feature graph or broad facade constructor is used.
 */
internal interface LegacyChannelsBindings : LegacyChannelsApi {
    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyChannelsBinding("selfUser")
    fun provideChannelsSelfUser(dependencies: LegacyChannelsDependencies): LegacyChannelsCapture<() -> GetSelfUserUseCase> =
        LegacyChannelsCapture(dependencies.selfUser)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyChannelsBinding("conversationRepository")
    fun provideChannelsConversationRepository(
        dependencies: LegacyChannelsDependencies,
    ): LegacyChannelsCapture<() -> ConversationRepository> =
        LegacyChannelsCapture(dependencies.conversationRepository)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyChannelsBinding("metadataDaoProvider")
    fun provideChannelsMetadataDaoProvider(dependencies: LegacyChannelsDependencies): LegacyChannelsCapture<() -> MetadataDAO> =
        LegacyChannelsCapture(dependencies.metadataDaoProvider)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyChannelsBinding("selfUserObservationProvider")
    fun provideChannelsSelfUserObservationProvider(
        dependencies: LegacyChannelsDependencies,
    ): LegacyChannelsCapture<() -> SelfUserObservationProvider> =
        LegacyChannelsCapture(dependencies.selfUserObservationProvider)

    @Provides
    @LegacyChannelsBinding("channelsConfigStorage")
    fun provideChannelsChannelsConfigStorage(
        @LegacyChannelsBinding("metadataDaoProvider") metadataDaoProvider: LegacyChannelsCapture<() -> MetadataDAO>,
    ): ChannelsConfigurationStorage = ChannelsConfigurationStorage(metadataDaoProvider.value())

    @Provides
    @LegacyChannelsBinding("channelsFeatureConfigHandler")
    fun provideChannelsChannelsFeatureConfigHandler(
        @LegacyChannelsBinding("channelsConfigStorage") channelsConfigStorage: ChannelsConfigurationStorage,
    ): ChannelsFeatureConfigurationHandler = ChannelsFeatureConfigurationHandler(channelsConfigStorage)

    @Provides
    @LegacyChannelsBinding("observeChannelsCreationPermissionUseCase")
    fun provideChannelsObserveChannelsCreationPermissionUseCase(
        @LegacyChannelsBinding("selfUserObservationProvider")
        selfUserObservationProvider: LegacyChannelsCapture<() -> SelfUserObservationProvider>,
        @LegacyChannelsBinding("channelsConfigStorage") channelsConfigStorage: ChannelsConfigurationStorage,
    ): ObserveChannelsCreationPermissionUseCase =
        ObserveChannelsCreationPermissionUseCase(channelsConfigStorage, selfUserObservationProvider.value())

    @Provides
    @LegacyChannelsBinding("updateChannelAddPermission")
    fun provideChannelsUpdateChannelAddPermission(
        @LegacyChannelsBinding("conversationRepository") conversationRepository: LegacyChannelsCapture<() -> ConversationRepository>,
    ): UpdateChannelAddPermissionUseCase = UpdateChannelAddPermissionUseCaseImpl(conversationRepository.value())
}

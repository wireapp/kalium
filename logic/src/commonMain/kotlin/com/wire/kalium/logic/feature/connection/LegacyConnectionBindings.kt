/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.KaliumConnectionApi
import com.wire.kalium.logic.feature.LegacyFacadeLifetime
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn

/** Distinguishes this facade's captures from same-typed bindings in the parent and sibling facades. */
@Qualifier
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.VALUE_PARAMETER)
internal annotation class LegacyConnectionBinding(val value: String)

/**
 * Factory ports are read independently by the facade instance, never captured in UserLifetime.
 * Session mapping: names -> the matching session getters; transactionProvider -> cryptoTransactionProvider.
 * Keep each resolver/creator/fetch capture independent of cached conversation captures and of each other
 * (their own nested repository factory reads must not be replaced by this facade's repository).
 */
internal interface LegacyConnectionDependencies {
    val connectionRepository: ConnectionRepository
    val conversationRepository: ConversationRepository
    val userRepository: UserRepository
    val oneOnOneResolver: OneOnOneResolver
    val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator
    val fetchConversationUseCase: FetchConversationUseCase
    val transactionProvider: CryptoTransactionProvider
}

/** Individual entry points; capture accessors also preserve explicit legacy aliases. */
internal interface LegacyConnectionApi : KaliumConnectionApi {
    @get:LegacyConnectionBinding("connectionRepository")
    val connectionConnectionRepository: ConnectionRepository

    @get:LegacyConnectionBinding("conversationRepository")
    val connectionConversationRepository: ConversationRepository

    @get:LegacyConnectionBinding("userRepository")
    val connectionUserRepository: UserRepository

    @get:LegacyConnectionBinding("oneOnOneResolver")
    val connectionOneOnOneResolver: OneOnOneResolver

    @get:LegacyConnectionBinding("newGroupConversationSystemMessagesCreator")
    val connectionNewGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator

    @get:LegacyConnectionBinding("fetchConversationUseCase")
    val connectionFetchConversationUseCase: FetchConversationUseCase

    @get:LegacyConnectionBinding("transactionProvider")
    val connectionTransactionProvider: CryptoTransactionProvider

    @get:LegacyConnectionBinding("sendConnectionRequest")
    override val connectionSendConnectionRequest: SendConnectionRequestUseCase

    @get:LegacyConnectionBinding("acceptConnectionRequest")
    override val connectionAcceptConnectionRequest: AcceptConnectionRequestUseCase

    @get:LegacyConnectionBinding("cancelConnectionRequest")
    override val connectionCancelConnectionRequest: CancelConnectionRequestUseCase

    @get:LegacyConnectionBinding("ignoreConnectionRequest")
    override val connectionIgnoreConnectionRequest: IgnoreConnectionRequestUseCase

    @get:LegacyConnectionBinding("markConnectionRequestAsNotified")
    val connectionMarkConnectionRequestAsNotified: MarkConnectionRequestAsNotifiedUseCase

    @get:LegacyConnectionBinding("blockUser")
    override val connectionBlockUser: BlockUserUseCase

    @get:LegacyConnectionBinding("unblockUser")
    override val connectionUnblockUser: UnblockUserUseCase
}

/** Per-instance capture providers and individual, unscoped use-case constructors. */
@Suppress("TooManyFunctions", "LongParameterList") // Declarative capture and use-case providers.
internal interface LegacyConnectionBindings : LegacyConnectionApi {
    @Provides
    @SingleIn(LegacyFacadeLifetime::class)
    @LegacyConnectionBinding("connectionRepository")
    fun provideConnectionConnectionRepository(dependencies: LegacyConnectionDependencies): ConnectionRepository =
        dependencies.connectionRepository

    @Provides
    @SingleIn(LegacyFacadeLifetime::class)
    @LegacyConnectionBinding("conversationRepository")
    fun provideConnectionConversationRepository(dependencies: LegacyConnectionDependencies): ConversationRepository =
        dependencies.conversationRepository

    @Provides
    @SingleIn(LegacyFacadeLifetime::class)
    @LegacyConnectionBinding("userRepository")
    fun provideConnectionUserRepository(dependencies: LegacyConnectionDependencies): UserRepository =
        dependencies.userRepository

    @Provides
    @SingleIn(LegacyFacadeLifetime::class)
    @LegacyConnectionBinding("oneOnOneResolver")
    fun provideConnectionOneOnOneResolver(dependencies: LegacyConnectionDependencies): OneOnOneResolver =
        dependencies.oneOnOneResolver

    @Provides
    @SingleIn(LegacyFacadeLifetime::class)
    @LegacyConnectionBinding("newGroupConversationSystemMessagesCreator")
    fun provideConnectionNewGroupConversationSystemMessagesCreator(
        dependencies: LegacyConnectionDependencies,
    ): NewGroupConversationSystemMessagesCreator =
        dependencies.newGroupConversationSystemMessagesCreator

    @Provides
    @SingleIn(LegacyFacadeLifetime::class)
    @LegacyConnectionBinding("fetchConversationUseCase")
    fun provideConnectionFetchConversationUseCase(dependencies: LegacyConnectionDependencies): FetchConversationUseCase =
        dependencies.fetchConversationUseCase

    @Provides
    @SingleIn(LegacyFacadeLifetime::class)
    @LegacyConnectionBinding("transactionProvider")
    fun provideConnectionTransactionProvider(dependencies: LegacyConnectionDependencies): CryptoTransactionProvider =
        dependencies.transactionProvider

    @Provides
    @LegacyConnectionBinding("sendConnectionRequest")
    fun provideConnectionSendConnectionRequest(
        @LegacyConnectionBinding("connectionRepository") connectionRepository: ConnectionRepository,
        @LegacyConnectionBinding("userRepository") userRepository: UserRepository,
        @LegacyConnectionBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): SendConnectionRequestUseCase = SendConnectionRequestUseCaseImpl(
        connectionRepository,
        userRepository,
        transactionProvider,
    )

    @Provides
    @LegacyConnectionBinding("acceptConnectionRequest")
    fun provideConnectionAcceptConnectionRequest(
        @LegacyConnectionBinding("connectionRepository") connectionRepository: ConnectionRepository,
        @LegacyConnectionBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConnectionBinding("oneOnOneResolver") oneOnOneResolver: OneOnOneResolver,
        @LegacyConnectionBinding("newGroupConversationSystemMessagesCreator")
        newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator,
        @LegacyConnectionBinding("fetchConversationUseCase") fetchConversationUseCase: FetchConversationUseCase,
        @LegacyConnectionBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): AcceptConnectionRequestUseCase = AcceptConnectionRequestUseCaseImpl(
        connectionRepository,
        conversationRepository,
        oneOnOneResolver,
        newGroupConversationSystemMessagesCreator,
        fetchConversationUseCase,
        transactionProvider,
    )

    @Provides
    @LegacyConnectionBinding("cancelConnectionRequest")
    fun provideConnectionCancelConnectionRequest(
        @LegacyConnectionBinding("connectionRepository") connectionRepository: ConnectionRepository,
        @LegacyConnectionBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): CancelConnectionRequestUseCase = CancelConnectionRequestUseCaseImpl(
        connectionRepository,
        transactionProvider,
    )

    @Provides
    @LegacyConnectionBinding("ignoreConnectionRequest")
    fun provideConnectionIgnoreConnectionRequest(
        @LegacyConnectionBinding("connectionRepository") connectionRepository: ConnectionRepository,
        @LegacyConnectionBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): IgnoreConnectionRequestUseCase = IgnoreConnectionRequestUseCaseImpl(
        connectionRepository,
        transactionProvider,
    )

    @Provides
    @LegacyConnectionBinding("markConnectionRequestAsNotified")
    fun provideConnectionMarkConnectionRequestAsNotified(
        @LegacyConnectionBinding("connectionRepository") connectionRepository: ConnectionRepository,
    ): MarkConnectionRequestAsNotifiedUseCase = MarkConnectionRequestAsNotifiedUseCaseImpl(
        connectionRepository,
    )

    @Provides
    @LegacyConnectionBinding("blockUser")
    fun provideConnectionBlockUser(
        @LegacyConnectionBinding("connectionRepository") connectionRepository: ConnectionRepository,
        @LegacyConnectionBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): BlockUserUseCase = BlockUserUseCaseImpl(
        connectionRepository,
        transactionProvider,
    )

    @Provides
    @LegacyConnectionBinding("unblockUser")
    fun provideConnectionUnblockUser(
        @LegacyConnectionBinding("connectionRepository") connectionRepository: ConnectionRepository,
        @LegacyConnectionBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): UnblockUserUseCase = UnblockUserUseCaseImpl(
        connectionRepository,
        transactionProvider,
    )
}

/**
 * One extension per ConnectionScope instance, generated at the final platform root.
 * Expose only this factory from the user graph; create a new extension on every facade getter.
 */
@GraphExtension(LegacyFacadeLifetime::class)
internal interface LegacyConnectionFacadeGraph : LegacyConnectionBindings {
    @GraphExtension.Factory
    interface Factory {
        fun create(@Provides dependencies: LegacyConnectionDependencies): LegacyConnectionFacadeGraph
    }
}

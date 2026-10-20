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

package com.wire.kalium.logic.feature.meeting

import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.UserLifetime
import com.wire.kalium.logic.feature.conversation.LeaveConversationUseCase
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.util.KaliumDispatcher
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn

/** Separates cached facade captures from other user factories and same-typed captures. */
@Qualifier
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.VALUE_PARAMETER)
internal annotation class LegacyMeetingBinding(val value: String)

/**
 * Adapts session accessors without reading them eagerly. Each capture is retained independently.
 * The facade repository captures differ from the repositories captured by startup workers and event handlers.
 *
 * Session adapter mapping (getter inputs are captured separately from worker inputs):
 * `dispatcher -> KaliumDispatcherImpl`; repository names -> the matching UserSessionScope getters;
 * `refreshUsersWithoutMetadata -> this@UserSessionScope.refreshUsersWithoutMetadata`;
 * `resetMLSConversation -> resetMlsConversation`; `joinExistingMLSConversation -> joinExistingMLSConversationUseCase`;
 * `leaveConversation -> kaliumUserGraph.conversationLeaveConversation`;
 * `deleteConversation -> deleteConversationUseCase`; `transactionProvider -> cryptoTransactionProvider`.
 * Keep `meetingOccurrencesSyncWorker`, sync-meetings and event-handler repository factories separate.
 */
internal interface LegacyMeetingDependencies {
    val dispatcher: KaliumDispatcher
    val meetingRepository: MeetingRepository
    val userRepository: UserRepository
    val conversationRepository: ConversationRepository
    val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase
    val resetMLSConversation: ResetMLSConversationUseCase
    val joinExistingMLSConversation: JoinExistingMLSConversationUseCase
    val leaveConversation: LeaveConversationUseCase
    val deleteConversation: DeleteConversationUseCase
    val transactionProvider: CryptoTransactionProvider
}

/** Individual entry points for the compatibility facade and user runtime. */
internal interface LegacyMeetingApi {
    @get:LegacyMeetingBinding("dispatcher")
    val meetingDispatcher: KaliumDispatcher

    @get:LegacyMeetingBinding("meetingRepository")
    val meetingMeetingRepository: MeetingRepository

    @get:LegacyMeetingBinding("userRepository")
    val meetingUserRepository: UserRepository

    @get:LegacyMeetingBinding("conversationRepository")
    val meetingConversationRepository: ConversationRepository

    @get:LegacyMeetingBinding("refreshUsersWithoutMetadata")
    val meetingRefreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase

    @get:LegacyMeetingBinding("resetMLSConversation")
    val meetingResetMLSConversation: ResetMLSConversationUseCase

    @get:LegacyMeetingBinding("joinExistingMLSConversation")
    val meetingJoinExistingMLSConversation: JoinExistingMLSConversationUseCase

    @get:LegacyMeetingBinding("leaveConversation")
    val meetingLeaveConversation: LeaveConversationUseCase

    @get:LegacyMeetingBinding("deleteConversation")
    val meetingDeleteConversation: DeleteConversationUseCase

    @get:LegacyMeetingBinding("transactionProvider")
    val meetingTransactionProvider: CryptoTransactionProvider

    @get:LegacyMeetingBinding("maintainMeetingOccurrences")
    val meetingMaintainMeetingOccurrences: MaintainMeetingOccurrencesUseCase

    @get:LegacyMeetingBinding("getPaginatedMeetingOccurrenceDetails")
    val meetingGetPaginatedMeetingOccurrenceDetails: GetPaginatedMeetingOccurrencesUseCase

    @get:LegacyMeetingBinding("observeMeetingOccurrence")
    val meetingObserveMeetingOccurrence: ObserveMeetingOccurrenceUseCase

    @get:LegacyMeetingBinding("deleteMeetingForEveryone")
    val meetingDeleteMeetingForEveryone: DeleteMeetingForEveryoneUseCase

    @get:LegacyMeetingBinding("deleteMeetingForMe")
    val meetingDeleteMeetingForMe: DeleteMeetingForMeUseCase

    @get:LegacyMeetingBinding("getNextUnfinishedMeetingOccurrence")
    val meetingGetNextUnfinishedMeetingOccurrence: GetNextUnfinishedMeetingOccurrenceUseCase

    @get:LegacyMeetingBinding("ensureMeetingIsMLSEstablished")
    val meetingEnsureMeetingIsMLSEstablished: EnsureMeetingIsMLSEstablishedUseCase

    @get:LegacyMeetingBinding("createNewMeeting")
    val meetingCreateNewMeeting: CreateNewMeetingUseCase

    @get:LegacyMeetingBinding("updateMeeting")
    val meetingUpdateMeeting: UpdateMeetingUseCase

    @get:LegacyMeetingBinding("meetingReminders")
    val meetingMeetingReminders: MeetingRemindersUseCase
}

/**
 * Mixed into the user graph: captures and formerly retained values use the existing user lifetime.
 * All original factory getters remain unscoped. No feature graph or broad facade constructor is used.
 */
@Suppress("TooManyFunctions") // One declarative provider for each original capture and member.
internal interface LegacyMeetingBindings : LegacyMeetingApi {
    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMeetingBinding("dispatcher")
    fun provideMeetingDispatcher(dependencies: LegacyMeetingDependencies): KaliumDispatcher =
        dependencies.dispatcher

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMeetingBinding("meetingRepository")
    fun provideMeetingMeetingRepository(dependencies: LegacyMeetingDependencies): MeetingRepository =
        dependencies.meetingRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMeetingBinding("userRepository")
    fun provideMeetingUserRepository(dependencies: LegacyMeetingDependencies): UserRepository =
        dependencies.userRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMeetingBinding("conversationRepository")
    fun provideMeetingConversationRepository(dependencies: LegacyMeetingDependencies): ConversationRepository =
        dependencies.conversationRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMeetingBinding("refreshUsersWithoutMetadata")
    fun provideMeetingRefreshUsersWithoutMetadata(dependencies: LegacyMeetingDependencies): RefreshUsersWithoutMetadataUseCase =
        dependencies.refreshUsersWithoutMetadata

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMeetingBinding("resetMLSConversation")
    fun provideMeetingResetMLSConversation(dependencies: LegacyMeetingDependencies): ResetMLSConversationUseCase =
        dependencies.resetMLSConversation

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMeetingBinding("joinExistingMLSConversation")
    fun provideMeetingJoinExistingMLSConversation(dependencies: LegacyMeetingDependencies): JoinExistingMLSConversationUseCase =
        dependencies.joinExistingMLSConversation

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMeetingBinding("leaveConversation")
    fun provideMeetingLeaveConversation(dependencies: LegacyMeetingDependencies): LeaveConversationUseCase =
        dependencies.leaveConversation

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMeetingBinding("deleteConversation")
    fun provideMeetingDeleteConversation(dependencies: LegacyMeetingDependencies): DeleteConversationUseCase =
        dependencies.deleteConversation

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMeetingBinding("transactionProvider")
    fun provideMeetingTransactionProvider(dependencies: LegacyMeetingDependencies): CryptoTransactionProvider =
        dependencies.transactionProvider

    @Provides
    @LegacyMeetingBinding("maintainMeetingOccurrences")
    fun provideMeetingMaintainMeetingOccurrences(
        @LegacyMeetingBinding("dispatcher") dispatcher: KaliumDispatcher,
        @LegacyMeetingBinding("meetingRepository") meetingRepository: MeetingRepository,
    ): MaintainMeetingOccurrencesUseCase = MaintainMeetingOccurrencesUseCaseImpl(dispatcher, meetingRepository)

    @Provides
    @LegacyMeetingBinding("getPaginatedMeetingOccurrenceDetails")
    fun provideMeetingGetPaginatedMeetingOccurrenceDetails(
        @LegacyMeetingBinding("dispatcher") dispatcher: KaliumDispatcher,
        @LegacyMeetingBinding("meetingRepository") meetingRepository: MeetingRepository,
    ): GetPaginatedMeetingOccurrencesUseCase = GetPaginatedMeetingOccurrencesUseCaseImpl(
        dispatcher = dispatcher,
        meetingRepository = meetingRepository,
    )

    @Provides
    @LegacyMeetingBinding("observeMeetingOccurrence")
    fun provideMeetingObserveMeetingOccurrence(
        @LegacyMeetingBinding("dispatcher") dispatcher: KaliumDispatcher,
        @LegacyMeetingBinding("meetingRepository") meetingRepository: MeetingRepository,
    ): ObserveMeetingOccurrenceUseCase = ObserveMeetingOccurrenceUseCaseImpl(
        dispatcher = dispatcher,
        meetingRepository = meetingRepository,
    )

    @Provides
    @LegacyMeetingBinding("deleteMeetingForEveryone")
    fun provideMeetingDeleteMeetingForEveryone(
        @LegacyMeetingBinding("meetingRepository") meetingRepository: MeetingRepository,
        @LegacyMeetingBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyMeetingBinding("deleteConversation") deleteConversation: DeleteConversationUseCase,
        @LegacyMeetingBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): DeleteMeetingForEveryoneUseCase = DeleteMeetingForEveryoneUseCaseImpl(
        meetingRepository = meetingRepository,
        conversationRepository = conversationRepository,
        deleteConversation = deleteConversation,
        transactionProvider = transactionProvider,
    )

    @Provides
    @LegacyMeetingBinding("deleteMeetingForMe")
    fun provideMeetingDeleteMeetingForMe(
        @LegacyMeetingBinding("meetingRepository") meetingRepository: MeetingRepository,
        @LegacyMeetingBinding("leaveConversation") leaveConversation: LeaveConversationUseCase,
    ): DeleteMeetingForMeUseCase = DeleteMeetingForMeUseCaseImpl(
        meetingRepository = meetingRepository,
        leaveConversation = leaveConversation,
    )

    @Provides
    @LegacyMeetingBinding("getNextUnfinishedMeetingOccurrence")
    fun provideMeetingGetNextUnfinishedMeetingOccurrence(
        @LegacyMeetingBinding("dispatcher") dispatcher: KaliumDispatcher,
        @LegacyMeetingBinding("meetingRepository") meetingRepository: MeetingRepository,
    ): GetNextUnfinishedMeetingOccurrenceUseCase = GetNextUnfinishedMeetingOccurrenceUseCaseImpl(
        dispatcher = dispatcher,
        meetingRepository = meetingRepository,
    )

    @Provides
    @LegacyMeetingBinding("ensureMeetingIsMLSEstablished")
    fun provideMeetingEnsureMeetingIsMLSEstablished(
        @LegacyMeetingBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyMeetingBinding("joinExistingMLSConversation") joinExistingMLSConversation: JoinExistingMLSConversationUseCase,
        @LegacyMeetingBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): EnsureMeetingIsMLSEstablishedUseCase = EnsureMeetingIsMLSEstablishedUseCaseImpl(
        transactionProvider = transactionProvider,
        conversationRepository = conversationRepository,
        joinExistingMLSConversation = joinExistingMLSConversation,
    )

    @Provides
    @LegacyMeetingBinding("createNewMeeting")
    fun provideMeetingCreateNewMeeting(
        @LegacyMeetingBinding("meetingRepository") meetingRepository: MeetingRepository,
        @LegacyMeetingBinding("refreshUsersWithoutMetadata") refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase,
        @LegacyMeetingBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): CreateNewMeetingUseCase = CreateNewMeetingUseCaseImpl(
        meetingRepository = meetingRepository,
        refreshUsersWithoutMetadata = refreshUsersWithoutMetadata,
        transactionProvider = transactionProvider,
    )

    @Provides
    @LegacyMeetingBinding("updateMeeting")
    fun provideMeetingUpdateMeeting(
        @LegacyMeetingBinding("meetingRepository") meetingRepository: MeetingRepository,
        @LegacyMeetingBinding("userRepository") userRepository: UserRepository,
        @LegacyMeetingBinding("refreshUsersWithoutMetadata") refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase,
        @LegacyMeetingBinding("resetMLSConversation") resetMLSConversation: ResetMLSConversationUseCase,
        @LegacyMeetingBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): UpdateMeetingUseCase = UpdateMeetingUseCaseImpl(
        meetingRepository = meetingRepository,
        userRepository = userRepository,
        refreshUsersWithoutMetadata = refreshUsersWithoutMetadata,
        resetMLSConversation = resetMLSConversation,
        transactionProvider = transactionProvider,
    )

    @Provides
    @LegacyMeetingBinding("meetingReminders")
    fun provideMeetingMeetingReminders(
        @LegacyMeetingBinding("meetingRepository") meetingRepository: MeetingRepository,
    ): MeetingRemindersUseCase = MeetingRemindersUseCaseImpl(meetingRepository)
}

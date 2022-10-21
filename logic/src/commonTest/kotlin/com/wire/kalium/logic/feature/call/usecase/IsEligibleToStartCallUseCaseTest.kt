package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IsEligibleToStartCallUseCaseTest {

    @Mock
    val userConfigRepository = mock(classOf<UserConfigRepository>())

    @Mock
    val callRepository = mock(classOf<CallRepository>())

    private lateinit var isEligibleToStartCall: IsEligibleToStartCallUseCase

    @BeforeTest
    fun setUp() {
        isEligibleToStartCall = IsEligibleToStartCallUseCaseImpl(
            userConfigRepository = userConfigRepository,
            callRepository = callRepository
        )
    }

    @Test
    fun givenAnStorageErrorOccurred_whenVerifyingIfUserIsEligibleToStartGroupCallWithNoEstablishedCall_thenReturnUnavailable() = runTest {
        // given
        given(callRepository)
            .suspendFunction(callRepository::establishedCallConversationId)
            .whenInvoked()
            .thenReturn(null)

        given(userConfigRepository)
            .function(userConfigRepository::isConferenceCallingEnabled)
            .whenInvoked()
            .thenReturn(Either.Left(StorageFailure.Generic(Throwable("error"))))

        // when
        val result = isEligibleToStartCall(
            conversationId,
            Conversation.Type.GROUP
        )

        // then
        assertEquals(result, ConferenceCallingResult.Disabled.Unavailable)
    }

    @Test
    fun givenAnStorageErrorOccurred_whenVerifyingIfUserIsEligibleToStartOneOnOneCallWithNoEstablishedCall_thenReturnEnabled() = runTest {
        // given
        given(callRepository)
            .suspendFunction(callRepository::establishedCallConversationId)
            .whenInvoked()
            .thenReturn(null)

        given(userConfigRepository)
            .function(userConfigRepository::isConferenceCallingEnabled)
            .whenInvoked()
            .thenReturn(Either.Left(StorageFailure.Generic(Throwable("error"))))

        // when
        val result = isEligibleToStartCall(
            conversationId,
            Conversation.Type.ONE_ON_ONE
        )

        // then
        assertEquals(result, ConferenceCallingResult.Enabled)
    }

    @Test
    fun givenAnStorageErrorOccurred_whenVerifyingIfUserIsEligibleToStartGroupCallWithOtherEstablishedCall_thenReturnUnavailable() =
        runTest {
            // given
            given(callRepository)
                .suspendFunction(callRepository::establishedCallConversationId)
                .whenInvoked()
                .thenReturn(establishedCallConversationId)

            given(userConfigRepository)
                .function(userConfigRepository::isConferenceCallingEnabled)
                .whenInvoked()
                .thenReturn(Either.Left(StorageFailure.Generic(Throwable("error"))))

            // when
            val result = isEligibleToStartCall(
                conversationId,
                Conversation.Type.GROUP
            )

            // then
            assertEquals(result, ConferenceCallingResult.Disabled.Unavailable)
        }

    @Test
    fun givenUserIsEligibleToStartCall_whenVerifyingIfUserIsEligibleToStartGroupCallWithOtherEstablishedCall_thenReturnOngoingCall() =
        runTest {
            // given
            given(callRepository)
                .suspendFunction(callRepository::establishedCallConversationId)
                .whenInvoked()
                .thenReturn(establishedCallConversationId)

            given(userConfigRepository)
                .function(userConfigRepository::isConferenceCallingEnabled)
                .whenInvoked()
                .thenReturn(Either.Right(true))

            // when
            val result = isEligibleToStartCall(
                conversationId,
                Conversation.Type.GROUP
            )

            // then
            assertEquals(result, ConferenceCallingResult.Disabled.OngoingCall)
        }

    @Test
    fun givenUserIsEligibleToStartCall_whenVerifyingIfUserIsEligibleToStartGroupCallWithSameEstablishedCall_thenReturnEstablished() =
        runTest {
            // given
            given(callRepository)
                .suspendFunction(callRepository::establishedCallConversationId)
                .whenInvoked()
                .thenReturn(conversationId)

            given(userConfigRepository)
                .function(userConfigRepository::isConferenceCallingEnabled)
                .whenInvoked()
                .thenReturn(Either.Right(true))

            // when
            val result = isEligibleToStartCall(
                conversationId,
                Conversation.Type.GROUP
            )

            // then
            assertEquals(result, ConferenceCallingResult.Disabled.Established)
        }

    private companion object {
        val conversationId = ConversationId(
            value = "convValue",
            domain = "convDomain"
        )
        val establishedCallConversationId = ConversationId(
            value = "establishedValue",
            domain = "establishedDomain"
        )
    }
}

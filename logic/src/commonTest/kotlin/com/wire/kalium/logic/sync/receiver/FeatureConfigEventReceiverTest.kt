package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureConfigEventReceiverTest {

    @Test
    fun givenMLSUpdatedEventGrantingAccessForSelfUser_whenProcessingEvent_ThenSetMLSEnabledToTrue() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withSettingMLSEnabledSuccessful()
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangement.newMLSUpdatedEvent(MLSModel(listOf(TestUser.SELF.id.toPlainID()), Status.ENABLED))
        )

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setMLSEnabled)
            .with(eq(true))
    }

    @Test
    fun givenMLSUpdatedEventRemovingAccessForSelfUser_whenProcessingEvent_ThenSetMLSEnabledToFalse() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withSettingMLSEnabledSuccessful()
            .arrange()

        featureConfigEventReceiver.onEvent(arrangement.newMLSUpdatedEvent(MLSModel(emptyList(), Status.ENABLED)))

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setMLSEnabled)
            .with(eq(false))
    }

    @Suppress("MaxLineLength")
    @Test
    fun givenMLSUpdatedEventGrantingAccessForSelfUserButStatusIsDisabled_whenProcessingEvent_ThenSetMLSEnabledToFalse() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withSettingMLSEnabledSuccessful()
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangement.newMLSUpdatedEvent(MLSModel(listOf(TestUser.SELF.id.toPlainID()), Status.DISABLED))
        )

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setMLSEnabled)
            .with(eq(false))
    }

    @Test
    fun givenFileSharingUpdatedEventWithStatusEnabled_whenProcessingEvent_ThenSetFileSharingStatusToTrue() = runTest {
        val (arrangement, featureConfigEventReceiver) = Arrangement()
            .withSettingFileSharingEnabledSuccessful()
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangement.newFileSharingUpdatedEvent(ConfigsStatusModel(Status.DISABLED))
        )

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setFileSharingStatus)
            .with(eq(true), eq(true))
    }

    @Test
    fun givenFileSharingUpdatedEventWithStatusDisabled_whenProcessingEvent_ThenSetFileSharingStatusToFalse() = runTest {
        val (arrangment, featureConfigEventReceiver) = Arrangement()
            .withSettingFileSharingEnabledSuccessful()
            .arrange()

        featureConfigEventReceiver.onEvent(
            arrangment.newFileSharingUpdatedEvent(ConfigsStatusModel(Status.DISABLED))
        )

        verify(arrangment.userConfigRepository)
            .function(arrangment.userConfigRepository::setFileSharingStatus)
            .with(eq(false), eq(true))
    }

    private class Arrangement {

        val kaliumConfigs = KaliumConfigs()

        @Mock
        private val userRepository = mock(classOf<UserRepository>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        private val featureConfigEventReceiver: FeatureConfigEventReceiver = FeatureConfigEventReceiverImpl(
            userConfigRepository,
            userRepository,
            kaliumConfigs,
            TestUser.SELF.id
        )

        fun withSettingMLSEnabledSuccessful() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::setMLSEnabled)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSettingFileSharingEnabledSuccessful() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::setFileSharingStatus)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun newMLSUpdatedEvent(
            model: MLSModel
        ) = Event.FeatureConfig.MLSUpdated("eventId", model)

        fun newFileSharingUpdatedEvent(
            model: ConfigsStatusModel
        ) = Event.FeatureConfig.FileSharingUpdated("eventId", model)

        fun arrange() = this to featureConfigEventReceiver
    }
}

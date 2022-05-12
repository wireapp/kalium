package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.usecase.SetVideoPreviewUseCase
import com.wire.kalium.logic.util.PlatformView
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class SetVideoPreviewUseCaseTest {

    @Mock
    private val flowManagerService = mock(classOf<FlowManagerService>())

    @Mock
    private val platformView = mock(classOf<PlatformView>())

    private lateinit var setVideoPreviewUseCase: SetVideoPreviewUseCase

    @BeforeTest
    fun setup() {
        setVideoPreviewUseCase = SetVideoPreviewUseCase(flowManagerService)
    }

    @Test
    fun givenAValidConversationIdAndVideoState_whenUseCaseCalled_thenInvokeUseCase() = runTest {
        val conversationId = ConversationId("default", "domain")
        given(flowManagerService)
            .function(flowManagerService::setVideoPreview)
            .whenInvokedWith(eq(conversationId), any())
            .thenDoNothing()

        setVideoPreviewUseCase(conversationId, platformView)

        verify(flowManagerService).invocation {
            flowManagerService::setVideoPreview
        }.wasInvoked(once)
    }

}

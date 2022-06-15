package com.wire.kalium.logic.sync

import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCase
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateApiVersionsWorkerTest {

    @Mock
    private val updateApiVersionsUseCase = mock(classOf<UpdateApiVersionsUseCase>())

    private lateinit var updateApiVersionsWorker: UpdateApiVersionsWorker

    @BeforeTest
    fun setup() {
        updateApiVersionsWorker = UpdateApiVersionsWorker(updateApiVersionsUseCase)
    }

    @Test
    fun givenUpdateCompletes_whenExecutingAWorker_thenReturnSuccess() = runTest {
        given(updateApiVersionsUseCase)
            .suspendFunction(updateApiVersionsUseCase::invoke)
            .whenInvoked()
            .thenReturn(Unit)

        val result = updateApiVersionsWorker.doWork()

        assertEquals(result, Result.Success)
    }
}

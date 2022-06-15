package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.Times
import io.mockative.any
import io.mockative.configure
import io.mockative.given
import io.mockative.matchers.OneOfMatcher
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ConfigurationApi::class, ExperimentalCoroutinesApi::class)
class UpdateApiVersionUseCaseTest {

    @Mock
    internal val configRepository = configure(mock(ServerConfigRepository::class)) { stubsUnitByDefault = true }

    private lateinit var updateApiVersionsUseCase: UpdateApiVersionsUseCase

    @BeforeTest
    fun setup() {
        updateApiVersionsUseCase = UpdateApiVersionsUseCaseImpl(configRepository)
    }

    @Test
    fun givenConfigList_whenUpdatingApiVersions_thenALLMUSTBEUPDATED() = runTest {
        val configList = listOf(newServerConfig(1), newServerConfig(2), newServerConfig(3), newServerConfig(4))

        given(configRepository)
            .function(configRepository::configList)
            .whenInvoked()
            .thenReturn(
                Either.Right(configList)
            )

        given(configRepository)
            .suspendFunction(configRepository::updateConfigApiVersion)
            .whenInvokedWith(any())
            .then { Either.Right(Unit) }

        updateApiVersionsUseCase()

        verify(configRepository)
            .suspendFunction(configRepository::updateConfigApiVersion)
            .with(OneOfMatcher(configList.map { it.id }))
            .wasInvoked(exactly = Times(configList.size))
    }
}

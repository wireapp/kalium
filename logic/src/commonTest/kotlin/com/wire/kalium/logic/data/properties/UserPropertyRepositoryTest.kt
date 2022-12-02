package com.wire.kalium.logic.data.properties

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserPropertyRepositoryTest {

    @Test
    fun whenUserEnablingReadReceipts_thenShouldCallApiAndLocalStorageWithCorrectArgs() = runTest {
        val (arrangement, repository) = Arrangement()
            .withUpdateReadReceiptsSuccess()
            .withUpdateReadReceiptsLocallySuccess()
            .arrange()

        val result = repository.setReadReceiptsEnabled()

        result.shouldSucceed()
        verify(arrangement.propertiesApi)
            .suspendFunction(arrangement.propertiesApi::setProperty)
            .with(any(), any())
            .wasInvoked(once)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setReadReceiptsStatus)
            .with(eq(true))
            .wasInvoked(once)
    }

    @Test
    fun whenUserDisablingReadReceipts_thenShouldCallApiAndLocalStorageWithCorrectArgs() = runTest {
        val (arrangement, repository) = Arrangement()
            .withDeleteReadReceiptsSuccess()
            .withUpdateReadReceiptsLocallySuccess()
            .arrange()

        val result = repository.deleteReadReceiptsProperty()

        result.shouldSucceed()
        verify(arrangement.propertiesApi)
            .suspendFunction(arrangement.propertiesApi::deleteProperty)
            .with(any())
            .wasInvoked(once)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setReadReceiptsStatus)
            .with(eq(false))
            .wasInvoked(once)
    }

    private class Arrangement {

        @Mock
        val propertiesApi = mock(classOf<PropertiesApi>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        private val userPropertyRepository = UserPropertyDataSource(propertiesApi, userConfigRepository)

        fun withUpdateReadReceiptsSuccess() = apply {
            given(propertiesApi)
                .suspendFunction(propertiesApi::setProperty)
                .whenInvokedWith(eq(PropertiesApi.PropertyKey.WIRE_RECEIPT_MODE), eq(1))
                .thenReturn(NetworkResponse.Success(Unit, mapOf(), 200))
        }

        fun withDeleteReadReceiptsSuccess() = apply {
            given(propertiesApi)
                .suspendFunction(propertiesApi::deleteProperty)
                .whenInvokedWith(eq(PropertiesApi.PropertyKey.WIRE_RECEIPT_MODE))
                .thenReturn(NetworkResponse.Success(Unit, mapOf(), 200))
        }

        fun withUpdateReadReceiptsLocallySuccess() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::setReadReceiptsStatus)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to userPropertyRepository

    }
}

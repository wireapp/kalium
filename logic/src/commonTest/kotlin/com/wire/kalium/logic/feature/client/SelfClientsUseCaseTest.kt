package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.client.DeviceType
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.util.network.NetworkAddress
import io.ktor.utils.io.errors.IOException
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ConfigurationApi::class)
class SelfClientsUseCaseTest {


    @Mock
    private val clientRepository = configure(mock(classOf<ClientRepository>())) {
        stubsUnitByDefault = true
    }

    private lateinit var selfClientsUseCase: SelfClientsUseCase

    @BeforeTest
    fun setup() {
        selfClientsUseCase = SelfClientsUseCaseImpl(clientRepository)
    }

    @Test
    fun givenSelfListOfClientsSuccess_thenTheSuccessPropagated() = runTest {
        val expected = CLIENTS_LIST
        given(clientRepository)
            .coroutine { clientRepository.selfListOfClients() }
            .then { Either.Right(expected) }

        val actual = selfClientsUseCase.invoke()
        assertIs<SelfClientsResult.Success>(actual)
        assertEquals(expected, actual.clients)
    }



    @Test
    fun givenSelfListOfClientsFail_thenTheErrorPropagated() = runTest {
        val expected = NetworkFailure.ServerMiscommunication(KaliumException.NetworkUnavailableError(IOException("some error")))
        given(clientRepository)
            .coroutine { clientRepository.selfListOfClients() }
            .then { Either.Left(expected) }

        val actual = selfClientsUseCase.invoke()
        assertIs<SelfClientsResult.Failure.Generic>(actual)
        assertEquals(expected, actual.genericFailure)
    }

    private companion object {
        val CLIENTS_LIST = listOf(
            Client(
                clientId = PlainId(value = "client_id_1"),
                type = ClientType.Permanent,
                registrationTime = "31.08.1966",
                location = null,
                deviceType = DeviceType.Desktop,
                label = null,
                cookie = null,
                capabilities = null,
                model = "Mac ox"
            ),
            Client(
                clientId = PlainId(value = "client_id_1"),
                type = ClientType.Permanent,
                registrationTime = "01.06.2022",
                location = null,
                deviceType = DeviceType.Phone,
                label = null,
                cookie = null,
                capabilities = null,
                model = "iphone 15"
            ),
        )
    }

}

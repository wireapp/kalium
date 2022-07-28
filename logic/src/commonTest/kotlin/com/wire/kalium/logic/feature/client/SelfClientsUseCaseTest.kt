package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.client.DeviceType
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.exceptions.KaliumException
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
    }

    @Test
    fun givenSelfListOfClientsSuccess_whenGettingListOfSelfClients_thenTheListIsSortedReverseChronologically() = runTest {
        // given
        val list = listOf(
            CLIENT.copy(registrationTime = "2022.01.01"),
            CLIENT.copy(registrationTime = "2022.01.02")
        )
        val sorted = listOf(list[1], list[0])
        given(clientRepository)
            .coroutine { clientRepository.selfListOfClients() }
            .then { Either.Right(list) }
        // when
        val actual = (selfClientsUseCase.invoke() as SelfClientsResult.Success)
        // then
        assertEquals(sorted, actual.clients)
    }

    @Test
    fun givenSelfListOfClientsFail_thenTheErrorPropagated() = runTest {
        val expected = NetworkFailure.ServerMiscommunication(KaliumException.GenericError(IOException("some error")))
        given(clientRepository)
            .coroutine { clientRepository.selfListOfClients() }
            .then { Either.Left(expected) }

        val actual = selfClientsUseCase.invoke()
        assertIs<SelfClientsResult.Failure.Generic>(actual)
        assertEquals(expected, actual.genericFailure)
    }

    private companion object {
        val CLIENT = Client(
            id = PlainId(value = "client_id_1"),
            type = ClientType.Permanent,
            registrationTime = "2022.01.01",
            location = null,
            deviceType = DeviceType.Desktop,
            label = null,
            cookie = null,
            capabilities = null,
            model = "Mac ox"
        )
        val CLIENTS_LIST = listOf(
            CLIENT.copy(id = PlainId(value = "client_id_1")),
            CLIENT.copy(id = PlainId(value = "client_id_2"))
        )
    }

}

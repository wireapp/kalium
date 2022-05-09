package com.wire.kalium.logic.feature.server_config

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfigUtil
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.logic.util.stubs.newServerConfigDTO
import com.wire.kalium.network.api.api_version.VersionInfoDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.http.Url
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.any
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ConfigurationApi::class, ExperimentalCoroutinesApi::class)
class UpdateApiVersionUseCaseTest {

    @Mock
    internal val configRepository = configure(mock(ServerConfigRepository::class)) { stubsUnitByDefault = true }

    @Mock
    internal val serverConfigMapper = configure(mock(ServerConfigMapper::class)) { stubsUnitByDefault = true }

    @Mock
    internal val serverConfigUtil = configure(mock(ServerConfigUtil::class)) { stubsUnitByDefault = true }

    private lateinit var updateApiVersionsUseCase: UpdateApiVersionsUseCase

    @BeforeTest
    fun setup() {
        updateApiVersionsUseCase = UpdateApiVersionsUseCaseImpl(configRepository, serverConfigMapper, serverConfigUtil)
        given(serverConfigMapper)
            .function(serverConfigMapper::toDTO)
            .whenInvokedWith(any())
            .then {
                ServerConfigDTO(
                    Url(it.apiBaseUrl),
                    Url(it.accountsBaseUrl),
                    Url(it.webSocketBaseUrl),
                    Url(it.blackListUrl),
                    Url(it.teamsUrl),
                    Url(it.websiteUrl),
                    it.title
                )
            }
    }

    @Test
    fun givenConfigListReturnsFailure_whenUpdatingApiVersions_thenReturnStorageFailure() = runTest {
        val failure = StorageFailure.DataNotFound
        given(configRepository)
            .function(configRepository::configList)
            .whenInvoked()
            .thenReturn(Either.Left(failure))

        val result = updateApiVersionsUseCase.invoke()

        assertIs<UpdateApiVersionsResult.Failure>(result)
        assertEquals(result.genericFailure, failure)
        verify(serverConfigMapper)
            .function(serverConfigMapper::toDTO)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenFetchRemoteApiVersionReturnsFailure_whenUpdatingApiVersions_thenReturnNetworkFailure() = runTest {
        val serverConfig = newServerConfig(1)
        val serverConfigDto = newServerConfigDTO(1)
        val failure = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
        given(configRepository)
            .function(configRepository::configList)
            .whenInvoked()
            .thenReturn(Either.Right(listOf(serverConfig)))
        given(configRepository)
            .suspendFunction(configRepository::fetchRemoteApiVersion)
            .whenInvokedWith(eq(serverConfigDto))
            .thenReturn(Either.Left(failure))

        val result = updateApiVersionsUseCase.invoke()

        assertIs<UpdateApiVersionsResult.Failure>(result)
        assertEquals(result.genericFailure, failure)
        verify(serverConfigMapper)
            .function(serverConfigMapper::toDTO)
            .with(eq(serverConfig))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoreConfigReturnsFailure_whenUpdatingApiVersions_thenReturnStorageFailure() = runTest {
        val serverConfig = newServerConfig(1)
        val serverConfigDto = newServerConfigDTO(1)
        val failure = StorageFailure.DataNotFound
        given(configRepository)
            .function(configRepository::configList)
            .whenInvoked()
            .thenReturn(Either.Right(listOf(serverConfig)))
        given(configRepository)
            .suspendFunction(configRepository::fetchRemoteApiVersion)
            .whenInvokedWith(eq(serverConfigDto))
            .thenReturn(Either.Right(VersionInfoDTO("wire.com", true, listOf(1))))
        given(serverConfigUtil)
            .function(serverConfigUtil::calculateApiVersion)
            .whenInvokedWith(any(), any())
            .thenReturn(Either.Right(1))
        given(configRepository)
            .function(configRepository::storeConfig)
            .whenInvokedWith(any(), any(), any(), any())
            .thenReturn(Either.Left(failure))

        val result = updateApiVersionsUseCase.invoke()

        assertIs<UpdateApiVersionsResult.Failure>(result)
        assertEquals(result.genericFailure, failure)
    }

    @Test
    fun givenCalculateApiVersionReturnsDifferentResults_whenUpdatingApiVersions_thenFinishWithoutFailure() = runTest {
        val supportedAppVersion = 1
        val expected = listOf(
            newServerConfig(0).copy(commonApiVersion = CommonApiVersionType.Unknown),
            newServerConfig(1).copy(commonApiVersion = CommonApiVersionType.Valid(supportedAppVersion)),
            newServerConfig(2).copy(commonApiVersion = CommonApiVersionType.New),
        )
        given(configRepository)
            .function(configRepository::configList)
            .whenInvoked()
            .thenReturn(Either.Right(expected))
        given(configRepository)
            .suspendFunction(configRepository::fetchRemoteApiVersion)
            .whenInvokedWith(any())
            .then {
                val id = it.title.removePrefix("server").removeSuffix("-title").toInt()
                Either.Right(VersionInfoDTO("wire.com", true, listOf(id)))
            }
        given(serverConfigUtil)
            .function(serverConfigUtil::calculateApiVersion)
            .whenInvokedWith(any(), any())
            .then { list, _ ->
                when {
                    list[0] > supportedAppVersion -> Either.Left(ServerConfigFailure.NewServerVersion)
                    list[0] < supportedAppVersion -> Either.Left(ServerConfigFailure.UnknownServerVersion)
                    else -> Either.Right(supportedAppVersion)
                }
            }
        given(configRepository)
            .function(configRepository::storeConfig)
            .whenInvokedWith(any(), any(), any(), any())
            .then { serverConfigDTO, _, _, _ -> Either.Right(expected.first { it.title == serverConfigDTO.title }) }

        val result = updateApiVersionsUseCase.invoke()

        assertIs<UpdateApiVersionsResult.Success>(result)
        assertEquals(result.serverConfigList, expected)
    }
}

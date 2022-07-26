package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.configuration.server.CURRENT_DOMAIN
import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.FEDERATION_ENABLED
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigDataSource
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.logic.util.stubs.newServerConfigDTO
import com.wire.kalium.logic.util.stubs.newServerConfigEntity
import com.wire.kalium.network.api.configuration.ServerConfigApi
import com.wire.kalium.network.api.versioning.VersionApi
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ConfigurationApi::class)
@ExperimentalCoroutinesApi
class ServerConfigRepositoryTest {

    @Test
    fun givenUrl_whenFetchingServerConfigSuccess_thenTheSuccessIsReturned() = runTest {
        val (arrangement, repository) = Arrangement().withSuccessConfigResponse().arrange()
        val expected = arrangement.SERVER_CONFIG.links
        val serverConfigUrl = arrangement.SERVER_CONFIG_URL

        val actual = repository.fetchRemoteConfig(serverConfigUrl)

        actual.shouldSucceed { assertEquals(expected, it) }
        verify(arrangement.serverConfigApi)
            .coroutine { arrangement.serverConfigApi.fetchServerConfig(serverConfigUrl) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_thenItCanBeRetrievedAsList() {
        val (arrangement, repository) = Arrangement().withDaoEntityResponse().arrange()
        val expected = listOf(newServerConfig(1), newServerConfig(2), newServerConfig(3))

        repository.configList().shouldSucceed { assertEquals(it, expected) }
        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::allConfig)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_thenItCanBeRetrievedAsFlow() = runTest {
        val (arrangement, repository) = Arrangement().withDaoEntityFlowResponse().arrange()
        val expected = listOf(newServerConfig(1), newServerConfig(2), newServerConfig(3))

        val actual = repository.configFlow()

        assertIs<Either.Right<Flow<List<ServerConfig>>>>(actual)
        assertEquals(expected.first(), actual.value.first()[0])

        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::allConfigFlow)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_thenItCanBeRetrievedById() {
        val (arrangement, repository) = Arrangement().withConfigById().arrange()
        val expected = newServerConfig(1)

        val actual = repository.configById(expected.id)
        assertIs<Either.Right<ServerConfig>>(actual)
        assertEquals(expected, actual.value)

        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::configById)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_thenItCanBeDeleted() {
        val serverConfigId = "1"
        val (arrangement, repository) = Arrangement().withConfigById().arrange()

        val actual = repository.deleteById(serverConfigId)

        actual.shouldSucceed()
        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::deleteById)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_whenDeleting_thenItCanBeDeleted() {
        val serverConfig = newServerConfig(1)
        val (arrangement, repository) = Arrangement().withConfigById().arrange()

        val actual = repository.delete(serverConfig)

        actual.shouldSucceed()
        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::deleteById)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenValidCompatibleApiVersion_whenStoringConfigLocally_thenConfigIsStored() = runTest {
        val expected = newServerConfig(1)
        val (arrangement, repository) = Arrangement()
            .withApiAversionResponse(expected)
            .withConfigById()
            .withConfigByLinks()
            .withStoringCurrentDomain()
            .withStoringFederationEnabled()
            .arrange()

        repository.fetchApiVersionAndStore(expected.links).shouldSucceed {
            assertEquals(expected, it)
        }

        verify(arrangement.versionApi)
            .suspendFunction(arrangement.versionApi::fetchApiVersion)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::configById)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.kaliumPreferences)
            .function(arrangement.kaliumPreferences::putBoolean)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.kaliumPreferences)
            .invocation { arrangement.kaliumPreferences.putString(CURRENT_DOMAIN, "domain1.com") }
            .wasInvoked(exactly = once)
    }


    @Test
    fun givenStoredConfig_whenAddingTheSameOneWithNewApiVersionParams_thenStoredOneShouldBeUpdatedAndReturned() {
        val (arrangement, repository) = Arrangement()
            .withStoringCurrentDomain()
            .withStoringFederationEnabled(true)
            .withUpdatedServerConfig()
            .arrange()

        repository
            .storeConfig(arrangement.expectedServerConfig.links, arrangement.expectedServerConfig.metaData)
            .shouldSucceed { assertEquals(arrangement.expectedServerConfig, it) }

        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::configByLinks)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::insert)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::updateApiVersion)
            .with(any(), any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::setFederationToTrue)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::configById)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStoredConfig_whenAddingNewOne_thenNewOneShouldBeInsertedAndReturned() {
        val expected = newServerConfig(1)
        val (arrangement, repository) = Arrangement()
            .withConfigForNewRequest(expected)
            .withStoringCurrentDomain()
            .withStoringFederationEnabled()
            .arrange()

        repository
            .storeConfig(expected.links, expected.metaData)
            .shouldSucceed { assertEquals(it, expected) }

        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::configByLinks)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::insert)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::updateApiVersion)
            .with(any(), any())
            .wasNotInvoked()
        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::setFederationToTrue)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.serverConfigDAO)
            .function(arrangement.serverConfigDAO::configById)
            .with(any())
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        val SERVER_CONFIG_URL = "https://test.test/test.json"
        val SERVER_CONFIG_RESPONSE = newServerConfigDTO(1)
        val SERVER_CONFIG = newServerConfig(1)

        @Mock
        val serverConfigApi = mock(classOf<ServerConfigApi>())

        @Mock
        val serverConfigDAO = configure(mock(classOf<ServerConfigurationDAO>())) {
            stubsUnitByDefault = true
        }

        @Mock
        val versionApi = mock(classOf<VersionApi>())

        @Mock
        val kaliumPreferences = mock(classOf<KaliumPreferences>())

        private var serverConfigRepository: ServerConfigRepository =
            ServerConfigDataSource(serverConfigApi, serverConfigDAO, versionApi, kaliumPreferences)

        val serverConfigEntity = newServerConfigEntity(1)
        val expectedServerConfig = newServerConfig(1).copy(
            metaData = ServerConfig.MetaData(
                commonApiVersion = CommonApiVersionType.Valid(5),
                federation = true,
                domain = serverConfigEntity.metaData.domain
            )
        )

        fun withConfigForNewRequest(serverConfig: ServerConfig): Arrangement {
            given(serverConfigDAO)
                .invocation { with(serverConfig) { configByLinks(links.title, links.api, links.webSocket) } }
                .then { null }
            given(serverConfigDAO)
                .function(serverConfigDAO::configById)
                .whenInvokedWith(any())
                .then { newServerConfigEntity(1) }
            // config is not inserted before
            given(serverConfigDAO)
                .invocation { configByLinks(serverConfig.links.title, serverConfig.links.api, serverConfig.links.webSocket) }
                .then { null }

            return this
        }

        suspend fun withSuccessConfigResponse(): Arrangement {
            given(serverConfigApi)
                .coroutine { serverConfigApi.fetchServerConfig(SERVER_CONFIG_URL) }
                .then { NetworkResponse.Success(SERVER_CONFIG_RESPONSE.links, mapOf(), 200) }
            return this
        }

        fun withDaoEntityResponse(): Arrangement {
            given(serverConfigDAO).invocation { allConfig() }
                .then { listOf(newServerConfigEntity(1), newServerConfigEntity(2), newServerConfigEntity(3)) }
            return this
        }

        fun withConfigById(): Arrangement {
            given(serverConfigDAO)
                .function(serverConfigDAO::configById)
                .whenInvokedWith(any())
                .then { newServerConfigEntity(1) }
            return this
        }

        fun withConfigByLinks(): Arrangement {
            given(serverConfigDAO)
                .function(serverConfigDAO::configByLinks)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(null)
            return this
        }

        fun withDaoEntityFlowResponse(): Arrangement {
            given(serverConfigDAO).invocation { allConfigFlow() }
                .then { flowOf(listOf(newServerConfigEntity(1), newServerConfigEntity(2), newServerConfigEntity(3))) }
            return this
        }

        fun withApiAversionResponse(serverConfig: ServerConfig = newServerConfig(1)): Arrangement {
            val versionInfoDTO = ServerConfigDTO.MetaData(
                domain = serverConfig.metaData.domain,
                federation = serverConfig.metaData.federation,
                commonApiVersion = ApiVersionDTO.Valid(1)
            )
            given(versionApi)
                .suspendFunction(versionApi::fetchApiVersion)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(versionInfoDTO, mapOf(), 200) }

            return this
        }

        fun withUpdatedServerConfig(): Arrangement {
            val newServerConfigEntity = serverConfigEntity.copy(
                metaData = serverConfigEntity.metaData.copy(
                    apiVersion = 5,
                    federation = true
                )
            )

            given(serverConfigDAO)
                .invocation { with(serverConfigEntity) { configByLinks(links.title, links.api, links.webSocket) } }
                .then { serverConfigEntity }
            given(serverConfigDAO)
                .function(serverConfigDAO::configById)
                .whenInvokedWith(any())
                .then { newServerConfigEntity }

            return this
        }

        fun withStoringCurrentDomain(domain: String = "domain1.com"): Arrangement {
            given(kaliumPreferences)
                .invocation { putString(CURRENT_DOMAIN, domain) }
                .then { Unit }
            return this
        }

        fun withStoringFederationEnabled(enabled: Boolean = false): Arrangement {
            given(kaliumPreferences)
                .invocation { putBoolean(FEDERATION_ENABLED, enabled) }
                .then { Unit }
            return this
        }

        fun arrange() = this to serverConfigRepository

    }
}

package com.wire.kalium.api.tools.json

import com.wire.kalium.network.BackendMetaDataUtil
import com.wire.kalium.network.BackendMetaDataUtilImpl
import com.wire.kalium.network.api.base.unbound.versioning.VersionInfoDTO
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BackendMetaDataUtilTest {
    private lateinit var serverConfigUtil: BackendMetaDataUtil

    @BeforeTest
    fun setup() {
        serverConfigUtil = BackendMetaDataUtilImpl
    }

    @Test
    fun givenCommonVersionBetweenAppAndDB_whenCalculateApiVersion_thenTheHighestCommonVersionIsReturned() {
        val appVersion = setOf(1, 2, 3, 4, 5)
        val serverVersion = VersionInfoDTO(listOf(5), "domain.com", false, listOf(0, 1, 2, 3, 4))
        val expected = ServerConfigDTO.MetaData(
            serverVersion.federation,
            ApiVersionDTO.Valid(4),
            serverVersion.domain
        )

        val actual = serverConfigUtil.calculateApiVersion(serverVersion, appVersion, false)
        assertEquals(expected, actual)

    }

    @Test
    fun givenCommonVersionBetweenAppAndDBAndEnabledDevApi_whenCalculateApiVersion_thenTheHighestCommonVersionIsReturned() {
        val appVersion = setOf(1, 2, 3, 4, 5)
        val serverVersion = VersionInfoDTO(listOf(5), "domain.com", false, listOf(0, 1, 2, 3, 4))
        val expected = ServerConfigDTO.MetaData(
            serverVersion.federation,
            ApiVersionDTO.Valid(5),
            serverVersion.domain
        )

        val actual = serverConfigUtil.calculateApiVersion(serverVersion, appVersion, true)
        assertEquals(expected, actual)

    }

    @Test
    fun givenCommonVersionAndEnabledDevApiAndNullDevApiSupported_whenCalculateApiVersion_thenTheHighestCommonVersionIsReturned() {
        val appVersion = setOf(1, 2, 3, 4, 5)
        val serverVersion = VersionInfoDTO(null, "domain.com", false, listOf(0, 1, 2, 3, 4))
        val expected = ServerConfigDTO.MetaData(
            serverVersion.federation,
            ApiVersionDTO.Valid(4),
            serverVersion.domain
        )

        val actual = serverConfigUtil.calculateApiVersion(serverVersion, appVersion, true)
        assertEquals(expected, actual)

    }

    @Test
    fun givenOldBEVersion_whenCalculateApiVersion_thenTheUnknownServerIsReturned() {
        val appVersion = setOf(1, 2, 3)
        val serverVersion = VersionInfoDTO(listOf(1), "domain.com", false, listOf(0))
        val expected = ServerConfigDTO.MetaData(
            serverVersion.federation,
            ApiVersionDTO.Invalid.Unknown,
            serverVersion.domain
        )

        val actual = serverConfigUtil.calculateApiVersion(serverVersion, appVersion, false)
        assertEquals(expected, actual)
    }

    @Test
    fun givenOldAppVersion_whenCalculateApiVersion_thenTheUnknownServerIsReturned() {
        val appVersion = setOf(0)
        val serverVersion = VersionInfoDTO(listOf(4), "domain.com", false, listOf(1, 2, 3))
        val expected = ServerConfigDTO.MetaData(
            serverVersion.federation,
            ApiVersionDTO.Invalid.New,
            serverVersion.domain
        )
        val actual = serverConfigUtil.calculateApiVersion(serverVersion, appVersion, false)
        assertEquals(expected, actual)
    }

    @Test
    fun givenAnEmptyServerVersionList_whenCalculateApiVersion_thenTheUnknownServerIsReturned() {
        val appVersion = setOf(0)
        val serverVersion = VersionInfoDTO(null, "domain.com", false, emptyList<Int>())
        val expected = ServerConfigDTO.MetaData(
            serverVersion.federation,
            ApiVersionDTO.Invalid.Unknown,
            serverVersion.domain
        )

        val actual = serverConfigUtil.calculateApiVersion(serverVersion, appVersion, false)
        assertEquals(expected, actual)
    }

    @Test
    fun givenAnEmptyAppVersionList_whenCalculateApiVersion_thenTheUnknownServerIsReturned() {
        val appVersion = emptySet<Int>()
        val serverVersion = VersionInfoDTO(null, "domain.com", false, listOf(1, 2, 3))
        val expected = ServerConfigDTO.MetaData(
            serverVersion.federation,
            ApiVersionDTO.Invalid.Unknown,
            serverVersion.domain
        )

        val actual = serverConfigUtil.calculateApiVersion(serverVersion, appVersion, false)
        assertEquals(expected, actual)
    }
}

package com.wire.kalium.api.common

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
    fun givenDevelopmentApiDisabled_whenCalculateApiVersion_thenTheHighestCommonVersionDoesNotIncludeServerDevelopmentVersion() {
        val appVersion = setOf(1, 2, 3, 4, 5)
        val developmentVersion = emptySet<Int>()
        val serverVersion = VersionInfoDTO(listOf(5), "domain.com", false, listOf(0, 1, 2, 3, 4))
        val expected = ServerConfigDTO.MetaData(
            serverVersion.federation,
            ApiVersionDTO.Valid(4),
            serverVersion.domain
        )

        val actual = serverConfigUtil.calculateApiVersion(serverVersion, appVersion, developmentVersion,false)
        assertEquals(expected, actual)
    }

    @Test
    fun givenDevelopmentApiDisabled_whenCalculateApiVersion_thenTheHighestCommonVersionDoesNotIncludeAppDevelopmentVersion() {
        val appVersion = setOf(1, 2, 3, 4)
        val developmentVersion = setOf(5)
        val serverVersion = VersionInfoDTO(listOf(6), "domain.com", false, listOf(0, 1, 2, 3, 4, 5))
        val expected = ServerConfigDTO.MetaData(
            serverVersion.federation,
            ApiVersionDTO.Valid(4),
            serverVersion.domain
        )

        val actual = serverConfigUtil.calculateApiVersion(serverVersion, appVersion, developmentVersion,false)
        assertEquals(expected, actual)
    }

    @Test
    fun givenDevelopmentApiEnabled_whenCalculateApiVersion_thenTheHighestCommonVersionIncludesAppDevelopmentVersion() {
        val appVersion = setOf(1, 2, 3, 4)
        val developmentVersion = setOf(5)
        val serverVersion = VersionInfoDTO(listOf(5), "domain.com", false, listOf(0, 1, 2, 3, 4))
        val expected = ServerConfigDTO.MetaData(
            serverVersion.federation,
            ApiVersionDTO.Valid(5),
            serverVersion.domain
        )

        val actual = serverConfigUtil.calculateApiVersion(serverVersion, appVersion, developmentVersion,true)
        assertEquals(expected, actual)
    }

    @Test
    fun givenServerDevelopmentVersionIsNull_whenCalculateApiVersion_thenTheHighestCommonVersionDoesNotIncludeAppDevelopmentVersion() {
        val appVersion = setOf(1, 2, 3, 4)
        val developmentVersion = setOf(5)
        val serverVersion = VersionInfoDTO(null, "domain.com", false, listOf(0, 1, 2, 3, 4))
        val expected = ServerConfigDTO.MetaData(
            serverVersion.federation,
            ApiVersionDTO.Valid(4),
            serverVersion.domain
        )

        val actual = serverConfigUtil.calculateApiVersion(serverVersion, appVersion, developmentVersion, true)
        assertEquals(expected, actual)
    }

    @Test
    fun givenServerSupportedVersionIsOutOfRange_whenCalculateApiVersion_thenTheUnknownServerIsReturned() {
        val appVersion = setOf(1, 2, 3)
        val developmentVersion = emptySet<Int>()
        val serverVersion = VersionInfoDTO(listOf(1), "domain.com", false, listOf(0))
        val expected = ServerConfigDTO.MetaData(
            serverVersion.federation,
            ApiVersionDTO.Invalid.Unknown,
            serverVersion.domain
        )

        val actual = serverConfigUtil.calculateApiVersion(serverVersion, appVersion, developmentVersion,false)
        assertEquals(expected, actual)
    }

    @Test
    fun givenAppSupportedVersionIsOutOfRange_whenCalculateApiVersion_thenTheUnknownServerIsReturned() {
        val appVersion = setOf(0)
        val developmentVersion = emptySet<Int>()
        val serverVersion = VersionInfoDTO(listOf(4), "domain.com", false, listOf(1, 2, 3))
        val expected = ServerConfigDTO.MetaData(
            serverVersion.federation,
            ApiVersionDTO.Invalid.New,
            serverVersion.domain
        )
        val actual = serverConfigUtil.calculateApiVersion(serverVersion, appVersion, developmentVersion,false)
        assertEquals(expected, actual)
    }

    @Test
    fun givenAnEmptyServerSupportedVersionList_whenCalculateApiVersion_thenTheUnknownServerIsReturned() {
        val appVersion = setOf(0)
        val developmentVersion = emptySet<Int>()
        val serverVersion = VersionInfoDTO(null, "domain.com", false, emptyList<Int>())
        val expected = ServerConfigDTO.MetaData(
            serverVersion.federation,
            ApiVersionDTO.Invalid.Unknown,
            serverVersion.domain
        )

        val actual = serverConfigUtil.calculateApiVersion(serverVersion, appVersion, developmentVersion,false)
        assertEquals(expected, actual)
    }

    @Test
    fun givenAnEmptyAppSupportedVersionList_whenCalculateApiVersion_thenTheUnknownServerIsReturned() {
        val appVersion = emptySet<Int>()
        val developmentVersion = emptySet<Int>()
        val serverVersion = VersionInfoDTO(null, "domain.com", false, listOf(1, 2, 3))
        val expected = ServerConfigDTO.MetaData(
            serverVersion.federation,
            ApiVersionDTO.Invalid.Unknown,
            serverVersion.domain
        )

        val actual = serverConfigUtil.calculateApiVersion(serverVersion, appVersion, developmentVersion,false)
        assertEquals(expected, actual)
    }
}

package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.configuration.server.ServerConfigUtil
import com.wire.kalium.logic.configuration.server.ServerConfigUtilImpl
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerConfigUtilTest {

    private lateinit var serverConfigUtil: ServerConfigUtil

    @BeforeTest
    fun setup() {
        serverConfigUtil = ServerConfigUtilImpl
    }

    @Test
    fun givenCommonVersionBetweenAppAndDB_whenCalculateApiVersion_thenTheHighestCommonVersionIsReturned() {
        val expected = 4
        val appVersion = setOf(1, 2, 3, 4, 5)
        val serverVersion = listOf(0, 1, 2, 3, 4)

        serverConfigUtil.calculateApiVersion(serverVersion, appVersion).shouldSucceed { actual ->
            assertEquals(expected, actual)
        }
    }

    @Test
    fun givenOldBEVersion_whenCalculateApiVersion_thenTheUnknownServerIsReturned() {
        val expected = ServerConfigFailure.UnknownServerVersion
        val appVersion = setOf(1, 2, 3)
        val serverVersion = listOf(0)

        serverConfigUtil.calculateApiVersion(serverVersion, appVersion).shouldFail { actual ->
            assertEquals(expected, actual)
        }
    }

    @Test
    fun givenOldAppVersion_whenCalculateApiVersion_thenTheUnknownServerIsReturned() {
        val expected = ServerConfigFailure.NewServerVersion
        val appVersion = setOf(0)
        val serverVersion = listOf(1, 2, 3)

        serverConfigUtil.calculateApiVersion(serverVersion, appVersion).shouldFail { actual ->
            assertEquals(expected, actual)
        }
    }


    @Test
    fun givenAnEmptyServerVersionList_whenCalculateApiVersion_thenTheUnknownServerIsReturned() {
        val expected = ServerConfigFailure.UnknownServerVersion
        val appVersion = setOf(0)
        val serverVersion = emptyList<Int>()

        serverConfigUtil.calculateApiVersion(serverVersion, appVersion).shouldFail { actual ->
            assertEquals(expected, actual)
        }
    }

    @Test
    fun givenAnEmptyAppVersionList_whenCalculateApiVersion_thenTheUnknownServerIsReturned() {
        val expected = ServerConfigFailure.UnknownServerVersion
        val appVersion = emptySet<Int>()
        val serverVersion = listOf(1, 2, 3)

        serverConfigUtil.calculateApiVersion(serverVersion, appVersion).shouldFail { actual ->
            assertEquals(expected, actual)
        }
    }
}

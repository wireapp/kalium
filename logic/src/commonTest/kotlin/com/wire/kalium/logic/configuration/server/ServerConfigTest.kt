package com.wire.kalium.logic.configuration.server

import com.wire.kalium.logic.util.stubs.newServerConfig
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerConfigTest {

    private lateinit var serverConfig: ServerConfig.Links

    @BeforeTest
    fun setup() {
        serverConfig = newServerConfig(1).links
    }

    @Test
    fun whenCreatingForgotPasswordUrl_thenItsCorrect() {
        val expected =  serverConfig.accounts + "/forgot"
        serverConfig.forgotPassword.also { actual ->
            assertEquals(expected, actual)
        }
    }

    @Test
    fun whenTOSUrl_thenItsCorrect() {
        val expected =  serverConfig.website + "/pricing"
        serverConfig.pricing.also { actual ->
            assertEquals(expected, actual)
        }
    }

    @Test
    fun whenPricingUrl_thenItsCorrect() {
        val expected =  serverConfig.website + "/legal"
        serverConfig.tos.also { actual ->
            assertEquals(expected, actual)
        }
    }
}

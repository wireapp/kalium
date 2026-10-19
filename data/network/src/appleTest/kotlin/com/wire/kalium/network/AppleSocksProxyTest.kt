/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.network

import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.darwin.DarwinClientEngineConfig
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.objcPtr
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalForeignApi::class)
class AppleSocksProxyTest {
    @Test
    fun authenticationExtendsKtorProxyRoutingWithoutReplacingIt() {
        val configuration = sessionConfiguration(proxy(), ProxyCredentialsDTO("alice", "alice-secret"))
        assertEquals(
            mapOf<Any?, Any?>(
                "SOCKSEnable" to 1L,
                "SOCKSProxy" to "proxy.example",
                "SOCKSPort" to 1080L,
                "kCFStreamPropertySOCKSVersion" to "kCFStreamSocketSOCKSVersion5",
                "kCFStreamPropertySOCKSUser" to "alice",
                "kCFStreamPropertySOCKSPassword" to "alice-secret",
            ),
            normalizedProxyDictionary(configuration),
        )
        assertNull(configuration.URLCredentialStorage)
    }

    @Test
    fun optionalAuthenticationDoesNotInstallSuppliedSecrets() {
        val configuration = sessionConfiguration(proxy(false), ProxyCredentialsDTO("alice", "alice-secret"))
        assertEquals(setOf("SOCKSEnable", "SOCKSProxy", "SOCKSPort", "kCFStreamPropertySOCKSVersion"),
            configuration.connectionProxyDictionary?.keys)
        assertNull(configuration.URLCredentialStorage)
    }

    @Test
    fun missingCredentialsKeepProxyRoutingAndDoNotUseSharedCredentialStorage() {
        val configuration = sessionConfiguration(proxy(), null)
        assertEquals("proxy.example", configuration.connectionProxyDictionary?.get("SOCKSProxy"))
        assertNull(configuration.connectionProxyDictionary?.get("kCFStreamPropertySOCKSUser"))
        assertNull(configuration.connectionProxyDictionary?.get("kCFStreamPropertySOCKSPassword"))
        assertNull(configuration.URLCredentialStorage)
    }

    @Test
    fun credentialsBelongToEachEngineAndAreNotReusedByAnotherAccount() {
        val alice = sessionConfiguration(proxy(), ProxyCredentialsDTO("alice", "alice-secret"))
        val bob = sessionConfiguration(proxy(), ProxyCredentialsDTO("bob", "bob-secret"))
        val missing = sessionConfiguration(proxy(), null)
        assertEquals("alice", alice.connectionProxyDictionary?.get("kCFStreamPropertySOCKSUser"))
        assertEquals("bob", bob.connectionProxyDictionary?.get("kCFStreamPropertySOCKSUser"))
        assertNull(missing.connectionProxyDictionary?.get("kCFStreamPropertySOCKSUser"))
        assertEquals("alice-secret", alice.connectionProxyDictionary?.get("kCFStreamPropertySOCKSPassword"))
    }

    @Test
    fun partiallyMissingCredentialsDoNotInstallAnInventedUsernameOrPassword() {
        val usernameOnly = sessionConfiguration(proxy(), ProxyCredentialsDTO("alice", null))
        val passwordOnly = sessionConfiguration(proxy(), ProxyCredentialsDTO(null, "secret"))
        assertEquals("alice", usernameOnly.connectionProxyDictionary?.get("kCFStreamPropertySOCKSUser"))
        assertNull(usernameOnly.connectionProxyDictionary?.get("kCFStreamPropertySOCKSPassword"))
        assertNull(passwordOnly.connectionProxyDictionary?.get("kCFStreamPropertySOCKSUser"))
        assertEquals("secret", passwordOnly.connectionProxyDictionary?.get("kCFStreamPropertySOCKSPassword"))
    }

    @Test
    fun noProxyPreservesTheExistingSessionSettings() {
        val engine = defaultHttpEngine(null, ProxyCredentialsDTO("alice", "secret"), false, emptyMap())
        try {
            val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration()
            val dictionary = mapOf<Any?, Any?>("HTTPEnable" to 1, "HTTPProxy" to "existing.example")
            configuration.connectionProxyDictionary = dictionary
            val credentialStorage = configuration.URLCredentialStorage
            (engine.config as DarwinClientEngineConfig).sessionConfig(configuration)
            assertEquals(mapOf<Any?, Any?>("HTTPEnable" to 1L, "HTTPProxy" to "existing.example"), normalizedProxyDictionary(configuration))
            // Foundation getters can create different Kotlin wrappers for the same native storage.
            assertEquals(credentialStorage.objcPtr(), configuration.URLCredentialStorage.objcPtr())
        } finally {
            engine.close()
        }
    }

    @Test
    fun proxyLeavesCertificateChallengePolicyWithTheCertificatePinner() {
        val unpinned = defaultHttpEngine(proxy(), ProxyCredentialsDTO("alice", "secret"), false, emptyMap())
        val pinned = defaultHttpEngine(proxy(), ProxyCredentialsDTO("alice", "secret"), false,
            mapOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" to listOf("example.com")))
        try {
            assertNull((unpinned.config as DarwinClientEngineConfig).challengeHandler)
            assertNotNull((pinned.config as DarwinClientEngineConfig).challengeHandler)
        } finally {
            unpinned.close()
            pinned.close()
        }
    }

    private fun sessionConfiguration(proxy: ServerConfigDTO.ApiProxy?, credentials: ProxyCredentialsDTO?): NSURLSessionConfiguration {
        val engine = defaultHttpEngine(proxy, credentials, false, emptyMap())
        try {
            val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration()
            val config = engine.config as DarwinClientEngineConfig
            assertEquals(proxy?.let { ProxyBuilder.socks(it.host, it.port).url }, config.proxy?.url)
            // Ktor applies its routing dictionary before invoking our session callback.
            configuration.connectionProxyDictionary = proxy?.let {
                mapOf<Any?, Any?>("SOCKSEnable" to 1, "SOCKSProxy" to it.host, "SOCKSPort" to it.port)
            }
            config.sessionConfig(configuration)
            val session = NSURLSession.sessionWithConfiguration(configuration)
            return session.configuration.also { session.invalidateAndCancel() }
        } finally {
            engine.close()
        }
    }

    private fun normalizedProxyDictionary(configuration: NSURLSessionConfiguration): Map<Any?, Any?> =
        configuration.connectionProxyDictionary.orEmpty().mapValues { (_, value) ->
            if (value is Number) value.toLong() else value
        }

    private fun proxy(needsAuthentication: Boolean = true) = ServerConfigDTO.ApiProxy(needsAuthentication, "proxy.example", 1080)
}

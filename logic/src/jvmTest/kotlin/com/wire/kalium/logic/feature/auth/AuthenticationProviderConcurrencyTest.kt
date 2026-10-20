/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.persistence.TestGlobalDatabase
import dev.zacsweers.metro.createGraphFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class AuthenticationProviderConcurrencyTest {
    @Test
    fun actualAuthenticationChildrenShareCapturesWithinFacadeAndDifferAcrossFreshFacades() {
        val fixture = TestGlobalDatabase()
        val database = fixture.provider
        database.close()
        fixture.delete()
        val scope = AuthenticationScopeProvider("test/useragent").provide(
            newServerConfig(18),
            null,
            database,
            KaliumConfigs(mockedRequests = emptyList()),
        )
        val firstRegister = scope.registerScope
        val secondRegister = scope.registerScope
        val registerRepository = capture(firstRegister.register, "registerAccountRepository")
        assertSame(registerRepository, capture(firstRegister.register, "registerAccountRepository"))
        assertSame(registerRepository, capture(firstRegister.requestActivationCode, "registerAccountRepository"))
        assertNotSame(registerRepository, capture(secondRegister.register, "registerAccountRepository"))

        val firstSSO = scope.ssoLoginScope
        val secondSSO = scope.ssoLoginScope
        val ssoRepository = capture(firstSSO.initiate, "ssoLoginRepository")
        assertSame(ssoRepository, capture(firstSSO.finalize, "ssoLoginRepository"))
        assertSame(ssoRepository, capture(firstSSO.getLoginSession, "ssoLoginRepository"))
        assertSame(ssoRepository, capture(firstSSO.fetchSSOSettings, "ssoLoginRepository"))
        assertSame(ssoRepository, capture(firstSSO.metaData, "ssoLoginRepository"))
        assertSame(ssoRepository, capture(firstSSO.settings, "ssoLoginRepository"))
        assertNotSame(ssoRepository, capture(secondSSO.initiate, "ssoLoginRepository"))
        assertNotSame(ssoRepository, scope.ssoLoginRepository)
        val pendingSettings = capture(firstSSO.getLoginSession, "pendingLoginSystemSettingsRepository")
        assertSame(pendingSettings, capture(firstSSO.getLoginSession, "pendingLoginSystemSettingsRepository"))
        assertNotSame(pendingSettings, capture(secondSSO.getLoginSession, "pendingLoginSystemSettingsRepository"))
        assertSame(scope.secondFactorVerificationRepository, capture(scope.login, "secondFactorVerificationRepository"))
        assertSame(
            scope.secondFactorVerificationRepository,
            capture(scope.requestSecondFactorVerificationCode, "secondFactorVerificationRepository")
        )
    }

    // JVM identity checks inspect the real use cases rather than adding test-only SDK accessors.
    private fun capture(instance: Any, fieldName: String): Any? = instance.javaClass.getDeclaredField(fieldName).run {
        isAccessible = true
        get(instance)
    }

    @Test
    fun concurrentFirstResolutionCreatesOneGraphAndVerificationStorePerCacheKey() = runTest {
        val creations = AtomicInteger()
        val factory = createGraphFactory<AuthenticationGraph.Factory>()
        val provider = AuthenticationScopeProvider("test/useragent", object : AuthenticationGraph.Factory {
            override fun create(inputs: AuthenticationGraphInputs): AuthenticationGraph {
                creations.incrementAndGet()
                return factory.create(inputs)
            }
        })
        val fixture = TestGlobalDatabase()
        val database = fixture.provider
        database.close()
        fixture.delete()
        val config = newServerConfig(18)
        val proxy = ProxyCredentials("proxy", "password")
        val configs = KaliumConfigs(mockedRequests = emptyList())
        val start = CompletableDeferred<Unit>()
        val requests = List(32) {
            async(Dispatchers.Default) {
                start.await()
                provider.provide(config, proxy, database, configs)
            }
        }
        start.complete(Unit)
        val scopes = requests.awaitAll()
        assertEquals(1, creations.get())
        scopes.forEach {
            assertSame(scopes.first(), it)
            assertSame(scopes.first().secondFactorVerificationRepository, it.secondFactorVerificationRepository)
        }
    }
}

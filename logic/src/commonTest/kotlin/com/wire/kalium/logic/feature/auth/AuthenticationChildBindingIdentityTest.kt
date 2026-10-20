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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.auth.settings.PendingLoginSystemSettingsRepository
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.sso.SSOInitiateLoginResult
import com.wire.kalium.logic.feature.auth.sso.SSOInitiateLoginUseCase
import com.wire.kalium.logic.feature.auth.sso.SSOLoginGraph
import com.wire.kalium.logic.feature.auth.sso.SSOLoginGraphCapture
import com.wire.kalium.logic.feature.auth.sso.SSOLoginScope
import com.wire.kalium.logic.feature.auth.sso.SSOLoginSessionResult
import com.wire.kalium.logic.feature.register.RegisterGraph
import com.wire.kalium.logic.feature.register.RegisterGraphCapture
import com.wire.kalium.logic.feature.register.RegisterParam
import com.wire.kalium.logic.feature.register.RegisterResult
import com.wire.kalium.logic.feature.register.RegisterScope
import com.wire.kalium.logic.feature.register.RequestActivationCodeResult
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.network.api.model.AuthenticationResultDTO
import com.wire.kalium.network.api.model.SessionDTO
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@DependencyGraph
internal interface AuthenticationChildIdentityGraph {
    val registerFactory: RegisterGraph.Factory
    val ssoFactory: SSOLoginGraph.Factory
}

class AuthenticationChildBindingIdentityTest {
    @Test
    fun registerUseCasesAreFreshAndRetainOnlyTheirOwnFacadeRepositoryAndConfiguration() = runTest {
        val factory = createGraph<AuthenticationChildIdentityGraph>().registerFactory
        val firstRepository = mock<RegisterAccountRepository>()
        val secondRepository = mock<RegisterAccountRepository>()
        val tokens = AccountTokens(UserId("user", "domain"), "fresh", "refresh", "Bearer", null)
        val param = RegisterParam.PersonalAccount("Name", "user@example.com", "password", "code", "cookie")
        val firstConfig = newServerConfig(16)
        val secondConfig = newServerConfig(18)
        val proxy = ProxyCredentials("proxy", "password")
        for (repository in listOf(firstRepository, secondRepository)) {
            everySuspend { repository.requestEmailActivationCode(param.email) } returns Either.Right(Unit)
            everySuspend {
                repository.registerPersonalAccountWithEmail(
                    param.email, param.emailActivationCode, param.name, param.password, param.cookieLabel
                )
            } returns Either.Right(null to tokens)
        }
        val first = RegisterScope(factory.create(RegisterGraphCapture(firstRepository, firstConfig, proxy)))
        val second = RegisterScope(factory.create(RegisterGraphCapture(secondRepository, secondConfig, null)))
        assertNotSame(first.register, first.register)
        assertNotSame(first.requestActivationCode, first.requestActivationCode)
        assertNotSame(first.register, second.register)
        repeat(2) {
            assertIs<RequestActivationCodeResult.Success>(first.requestActivationCode(param.email))
            val result = assertIs<RegisterResult.Success>(first.register(param))
            assertSame(tokens, result.authData)
            assertEquals(firstConfig.id, result.serverConfigId)
            assertSame(proxy, result.proxyCredentials)
        }
        val secondResult = assertIs<RegisterResult.Success>(second.register(param))
        assertEquals(secondConfig.id, secondResult.serverConfigId)
        verifySuspend(VerifyMode.exactly(2)) { firstRepository.requestEmailActivationCode(param.email) }
        verifySuspend(VerifyMode.not) { secondRepository.requestEmailActivationCode(any()) }
    }

    @Test
    fun ssoUseCasesShareTheirFacadeCapturesAndNeverFallBackToAnotherFacade() = runTest {
        val factory = createGraph<AuthenticationChildIdentityGraph>().ssoFactory
        val firstRepository = mock<SSOLoginRepository>()
        val secondRepository = mock<SSOLoginRepository>()
        val firstSettings = mock<PendingLoginSystemSettingsRepository>()
        val secondSettings = mock<PendingLoginSystemSettingsRepository>()
        val config = newServerConfig(18)
        val proxy = ProxyCredentials("proxy", "password")
        val uuid = "00000000-0000-0000-0000-000000000001"
        val login = AuthenticationResultDTO(
            SessionDTO(TestUser.SELF_USER_DTO.id, "Bearer", "fresh-token", "refresh-token", null),
            TestUser.SELF_USER_DTO,
        )
        everySuspend { firstRepository.initiate(uuid, null) } returns Either.Right("first")
        everySuspend { secondRepository.initiate(uuid, null) } returns Either.Right("second")
        everySuspend { firstRepository.provideLoginSession("cookie") } returns Either.Right(login)
        everySuspend { secondRepository.provideLoginSession("cookie") } returns Either.Right(login)
        everySuspend { firstSettings.isIdpChangeDetectionEnabled(any()) } returns Either.Right(true)
        everySuspend { secondSettings.isIdpChangeDetectionEnabled(any()) } returns Either.Right(false)
        val first = SSOLoginScope(factory.create(SSOLoginGraphCapture(firstRepository, config, proxy, firstSettings)))
        val second = SSOLoginScope(factory.create(SSOLoginGraphCapture(secondRepository, config, null, secondSettings)))
        assertNotSame(first.initiate, first.initiate)
        assertNotSame(first.finalize, first.finalize)
        assertNotSame(first.getLoginSession, first.getLoginSession)
        assertNotSame(first.fetchSSOSettings, first.fetchSSOSettings)
        assertNotSame(first.metaData, first.metaData)
        assertNotSame(first.settings, first.settings)
        repeat(2) {
            val initiated = assertIs<SSOInitiateLoginResult.Success>(
                first.initiate(SSOInitiateLoginUseCase.Param.WithoutRedirect("wire-$uuid"))
            )
            assertEquals("first", initiated.requestUrl)
            val result = assertIs<SSOLoginSessionResult.Success>(first.getLoginSession("cookie", true))
            assertEquals(true, result.isIdpChangeDetectionEnabled)
            assertSame(proxy, result.proxyCredentials)
        }
        val initiated = assertIs<SSOInitiateLoginResult.Success>(
            second.initiate(SSOInitiateLoginUseCase.Param.WithoutRedirect("wire-$uuid"))
        )
        assertEquals("second", initiated.requestUrl)
        val result = assertIs<SSOLoginSessionResult.Success>(second.getLoginSession("cookie", true))
        assertEquals(false, result.isIdpChangeDetectionEnabled)
        verifySuspend(VerifyMode.exactly(2)) { firstRepository.provideLoginSession("cookie") }
        verifySuspend(VerifyMode.exactly(2)) { firstSettings.isIdpChangeDetectionEnabled(any()) }
        verifySuspend(VerifyMode.exactly(1)) { secondSettings.isIdpChangeDetectionEnabled(any()) }
    }
}

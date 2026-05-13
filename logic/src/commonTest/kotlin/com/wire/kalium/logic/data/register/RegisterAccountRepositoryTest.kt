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

package com.wire.kalium.logic.data.register

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.base.unauthenticated.register.RegisterApi
import com.wire.kalium.network.api.model.SelfUserDTO
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.model.UserId as UserIdDTO
import com.wire.kalium.network.api.unauthenticated.register.ActivationParam
import com.wire.kalium.network.api.unauthenticated.register.RegisterParam
import com.wire.kalium.network.api.unauthenticated.register.RequestActivationCodeParam
import com.wire.kalium.network.utils.NetworkResponse
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterAccountRepositoryTest {
    private val registerApi = mock<RegisterApi>(mode = MockMode.autoUnit)

    private val sessionMapper = mock<SessionMapper>(mode = MockMode.autoUnit)

    private lateinit var registerAccountRepository: RegisterAccountRepository

    @BeforeTest
    fun setup() {
        registerAccountRepository = RegisterAccountDataSource(registerApi, IdMapper(), sessionMapper)
    }

    @Test
    fun givenApiRequestSuccess_whenRequestingActivationCodeForAnEmail_thenSuccessIsPropagated() = runTest {
        val expected = Unit
        val email = "user@domain.de"
        everySuspend {
            registerApi.requestActivationCode(RequestActivationCodeParam.Email(email))
        }.returns(NetworkResponse.Success(expected, mapOf(), 200))

        val actual = registerAccountRepository.requestEmailActivationCode(email)

        assertIs<Either.Right<Unit>>(actual)
        assertEquals(expected, actual.value)

        verifySuspend(VerifyMode.exactly(1)) {
            registerApi.requestActivationCode(RequestActivationCodeParam.Email(email))
        }
    }

    @Test
    fun givenApiRequestFail_whenRequestingActivationCodeForAnEmail_thenNetworkFailureIsPropagated() = runTest {
        val expected = TestNetworkException.generic
        val email = "user@domain.de"
        everySuspend {
            registerApi.requestActivationCode(RequestActivationCodeParam.Email(email))
        }.returns(NetworkResponse.Error(expected))

        val actual = registerAccountRepository.requestEmailActivationCode(email)

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(actual)
        assertEquals(expected, actual.value.kaliumException)
        verifySuspend(VerifyMode.exactly(1)) {
            registerApi.requestActivationCode(RequestActivationCodeParam.Email(email))
        }
    }

    @Test
    fun givenApiRequestRequestSuccess_whenActivatingAnEmail_thenSuccessIsPropagated() = runTest {
        val expected = Unit
        val email = "user@domain.de"
        val code = "123456"
        everySuspend {
            registerApi.activate(ActivationParam.Email(email, code))
        }.returns(NetworkResponse.Success(expected, mapOf(), 200))

        val actual = registerAccountRepository.verifyActivationCode(email, code)

        assertIs<Either.Right<Unit>>(actual)
        assertEquals(expected, actual.value)

        verifySuspend(VerifyMode.exactly(1)) {
            registerApi.activate(ActivationParam.Email(email, code))
        }
    }

    @Test
    fun givenApiRequestRequestFail_whenActivatingAnEmail_thenNetworkFailureIsPropagated() = runTest {
        val expected = TestNetworkException.generic
        val email = "user@domain.de"
        val code = "123456"
        everySuspend {
            registerApi.activate(ActivationParam.Email(email, code))
        }.returns(NetworkResponse.Error(expected))

        val actual = registerAccountRepository.verifyActivationCode(email, code)

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(actual)
        assertEquals(expected, actual.value.kaliumException)

        verifySuspend(VerifyMode.exactly(1)) {
            registerApi.activate(ActivationParam.Email(email, code))
        }
    }

    @Test
    fun givenApiRequestRequestSuccess_whenRegisteringPersonalAccountWithEmail_thenSuccessIsPropagated() = runTest {
        val email = EMAIL
        val code = CODE
        val password = PASSWORD
        val name = NAME
        val ssoId = with(TEST_USER.ssoID) {
            this?.let { SsoId(scimExternalId = it.scimExternalId, subject = it.subject, tenant = it.tenant) }
        }
        val cookieLabel = "COOKIE_LABEL"
        val accountTokens = with(SESSION) {
            AccountTokens(
                userId = UserId(userId.value, userId.domain),
                accessToken = accessToken,
                refreshToken = refreshToken,
                tokenType = tokenType,
                cookieLabel = cookieLabel
            )
        }
        val expected = Pair(ssoId, accountTokens)

        everySuspend {
            registerApi.register(
                RegisterParam.PersonalAccount(
                    email = email,
                    emailCode = code,
                    name = name,
                    password = password,
                    cookieLabel = cookieLabel
                )
            )
        }.returns(NetworkResponse.Success(Pair(TEST_USER, SESSION), mapOf(), 200))

        every {
            sessionMapper.fromSessionDTO(SESSION)
        }.returns(accountTokens)

        val actual = registerAccountRepository.registerPersonalAccountWithEmail(
            email = email,
            code = code,
            name = name,
            password = password,
            cookieLabel = cookieLabel
        )

        assertIs<Either.Right<Pair<SsoId?, AccountTokens>>>(actual)
        assertEquals(expected, actual.value)

        verifySuspend(VerifyMode.exactly(1)) {
            registerApi.register(
                RegisterParam.PersonalAccount(
                    email = email,
                    emailCode = code,
                    name = name,
                    password = password,
                    cookieLabel = cookieLabel
                )
            )
        }
        verify(VerifyMode.exactly(1)) {
            sessionMapper.fromSessionDTO(any())
        }
    }

    @Suppress("LongMethod")
    @Test
    fun givenApiRequestRequestSuccess_whenRegisteringTeamAccountWithEmail_thenSuccessIsPropagated() = runTest {
        val email = EMAIL
        val code = CODE
        val password = PASSWORD
        val name = NAME
        val teamName = TEAM_NAME
        val teamIcon = TEAM_ICON
        val cookieLabel = "COOKIE_LABEL"
        val ssoId = with(TEST_USER.ssoID) {
            this?.let { SsoId(scimExternalId = it.scimExternalId, subject = it.subject, tenant = it.tenant) }
        }
        val accountTokens =
            with(SESSION) {
                AccountTokens(
                    userId = UserId(userId.value, userId.domain),
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    tokenType = tokenType,
                    cookieLabel = cookieLabel
                )
            }
        val expected = Pair(ssoId, accountTokens)

        everySuspend {
            registerApi.register(
                RegisterParam.TeamAccount(
                    email = email,
                    emailCode = code,
                    name = name,
                    password = password,
                    teamName = teamName,
                    teamIcon = teamIcon,
                    cookieLabel = cookieLabel
                )
            )
        }.returns(
            NetworkResponse.Success(Pair(TEST_USER, SESSION), mapOf(), 200)
        )

        every {
            sessionMapper.fromSessionDTO(SESSION)
        }.returns(accountTokens)

        val actual = registerAccountRepository.registerTeamWithEmail(
            email = email,
            code = code,
            name = name,
            password = password,
            teamName = teamName,
            teamIcon = teamIcon,
            cookieLabel = cookieLabel
        )

        assertIs<Either.Right<Pair<SsoId?, AccountTokens>>>(actual)
        assertEquals(expected, actual.value)

        verifySuspend(VerifyMode.exactly(1)) {
            registerApi.register(
                RegisterParam.TeamAccount(
                    email = email,
                    emailCode = code,
                    name = name,
                    password = password,
                    teamName = teamName,
                    teamIcon = teamIcon,
                    cookieLabel = cookieLabel
                )
            )
        }
        verify(VerifyMode.exactly(1)) { sessionMapper.fromSessionDTO(SESSION) }
    }

    @Test
    fun givenApiRequestRequestFail_whenRegisteringWithEmail_thenNetworkFailureIsPropagated() = runTest {
        val email = EMAIL
        val code = CODE
        val password = PASSWORD
        val name = NAME
        val expected = TestNetworkException.generic
        val cookieLabel = "COOKIE_LABEL"
        everySuspend {
            registerApi.register(
                RegisterParam.PersonalAccount(
                    email = email,
                    emailCode = code,
                    name = name,
                    password = password,
                    cookieLabel = cookieLabel
                )
            )
        }.returns(NetworkResponse.Error(expected))

        val actual = registerAccountRepository.registerPersonalAccountWithEmail(
            email = email,
            code = code,
            name = name,
            password = password,
            cookieLabel = cookieLabel
        )

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(actual)
        assertEquals(expected, actual.value.kaliumException)

        verifySuspend(VerifyMode.exactly(1)) {
            registerApi.register(
                RegisterParam.PersonalAccount(
                    email = email,
                    emailCode = code,
                    name = name,
                    password = password,
                    cookieLabel = cookieLabel
                )
            )
        }
        verify(VerifyMode.exactly(0)) {
            sessionMapper.fromSessionDTO(any())
        }
    }

    private companion object {
        const val NAME = "user_name"
        const val EMAIL = "user@domain.de"
        const val CODE = "123456"
        const val PASSWORD = "password"
        const val TEAM_NAME = "teamName"
        const val TEAM_ICON = "teamIcon"
        val USERID_DTO = UserIdDTO("user_id", "domain.com")
        val SESSION: SessionDTO = SessionDTO(USERID_DTO, "tokenType", "access_token", "refresh_token", "cookieLabel")
        val TEST_USER: SelfUserDTO = SelfUserDTO(
            id = USERID_DTO,
            name = NAME,
            email = EMAIL,
            accentId = 1,
            assets = listOf(),
            deleted = null,
            handle = null,
            service = null,
            teamId = null,
            expiresAt = "",
            nonQualifiedId = "",
            locale = "",
            managedByDTO = null,
            phone = null,
            ssoID = null,
            supportedProtocols = null
        )
    }
}

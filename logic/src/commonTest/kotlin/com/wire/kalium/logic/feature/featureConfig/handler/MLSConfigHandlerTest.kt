/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.featureConfig.handler

import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.UpdateSupportedProtocolsAndResolveOneOnOnesArrangement
import com.wire.kalium.logic.util.arrangement.usecase.UpdateSupportedProtocolsAndResolveOneOnOnesArrangementImpl
import io.mockative.eq
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MLSConfigHandlerTest {
    @Test
    fun givenMlsIsEnabledAndMlsIsDefaultProtocol_whenSyncing_thenSetMlsAsDefault() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(Either.Right(setOf(SupportedProtocol.PROTEUS)))
            withSetSupportedProtocolsSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetMLSEnabledSuccessful()
        }

        handler.handle(MLS_CONFIG.copy(
            status = Status.ENABLED,
            defaultProtocol = SupportedProtocol.MLS
        ), duringSlowSync = false)

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setDefaultProtocol)
            .with(eq(SupportedProtocol.MLS))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsEnabledAndProteusIsDefaultProtocol_whenSyncing_thenSetProteusAsDefault() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(Either.Right(setOf(SupportedProtocol.PROTEUS)))
            withSetSupportedProtocolsSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetMLSEnabledSuccessful()
        }

        handler.handle(MLS_CONFIG.copy(
            status = Status.ENABLED,
            defaultProtocol = SupportedProtocol.PROTEUS
        ), duringSlowSync = false)

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setDefaultProtocol)
            .with(eq(SupportedProtocol.PROTEUS))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsDisabledAndMlsIsDefaultProtocol_whenSyncing_thenSetProteusAsDefault() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(Either.Right(setOf(SupportedProtocol.PROTEUS)))
            withSetSupportedProtocolsSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetMLSEnabledSuccessful()
        }

        handler.handle(MLS_CONFIG.copy(
            status = Status.DISABLED,
            defaultProtocol = SupportedProtocol.MLS
        ), duringSlowSync = false)

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setDefaultProtocol)
            .with(eq(SupportedProtocol.PROTEUS))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsEnabledAndSelfUserIsWhitelisted_whenSyncing_thenSetMlsEnabled() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(Either.Right(setOf(SupportedProtocol.PROTEUS)))
            withSetSupportedProtocolsSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetMLSEnabledSuccessful()
        }

        handler.handle(MLS_CONFIG.copy(
            status = Status.ENABLED,
            allowedUsers = listOf(SELF_USER_ID.toPlainID())
        ), duringSlowSync = false)

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setMLSEnabled)
            .with(eq(true))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsEnabledAndSelfUserIsNotWhitelisted_whenSyncing_thenSetMlsDisabled() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(Either.Right(setOf(SupportedProtocol.PROTEUS)))
            withSetSupportedProtocolsSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetMLSEnabledSuccessful()
        }

        handler.handle(MLS_CONFIG.copy(
            status = Status.ENABLED,
            allowedUsers = listOf(TestUser.OTHER_USER_ID.toPlainID())
        ), duringSlowSync = false)

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setMLSEnabled)
            .with(eq(false))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsDisabled_whenSyncing_thenSetMlsDisabled() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(Either.Right(setOf(SupportedProtocol.PROTEUS)))
            withSetSupportedProtocolsSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetMLSEnabledSuccessful()
        }

        handler.handle(MLS_CONFIG.copy(
            status = Status.DISABLED
        ), duringSlowSync = false)

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setMLSEnabled)
            .with(eq(false))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSupportedProtocolsHasChangedInEvent_whenSyncing_thenUpdateSelfSupportedProtocols() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(Either.Right(setOf(SupportedProtocol.PROTEUS)))
            withUpdateSupportedProtocolsAndResolveOneOnOnesSuccessful()
            withSetSupportedProtocolsSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetMLSEnabledSuccessful()
        }

        handler.handle(MLS_CONFIG.copy(
            status = Status.ENABLED,
            supportedProtocols = setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS)
        ), duringSlowSync = false)

        verify(arrangement.updateSupportedProtocolsAndResolveOneOnOnes)
            .suspendFunction(arrangement.updateSupportedProtocolsAndResolveOneOnOnes::invoke)
            .with(eq(true))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSupportedProtocolsHasChangedDuringSlowSync_whenSyncing_thenUpdateSelfSupportedProtocols() = runTest {
        val (arrangement, handler) = arrange {
            withGetSupportedProtocolsReturning(Either.Right(setOf(SupportedProtocol.PROTEUS)))
            withUpdateSupportedProtocolsAndResolveOneOnOnesSuccessful()
            withSetSupportedProtocolsSuccessful()
            withSetDefaultProtocolSuccessful()
            withSetMLSEnabledSuccessful()
        }

        handler.handle(MLS_CONFIG.copy(
            status = Status.ENABLED,
            supportedProtocols = setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS)
        ), duringSlowSync = true)

        verify(arrangement.updateSupportedProtocolsAndResolveOneOnOnes)
            .suspendFunction(arrangement.updateSupportedProtocolsAndResolveOneOnOnes::invoke)
            .with(eq(false))
            .wasInvoked(exactly = once)
    }

    private class Arrangement(private val block: Arrangement.() -> Unit) :
        UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl(),
        UpdateSupportedProtocolsAndResolveOneOnOnesArrangement by UpdateSupportedProtocolsAndResolveOneOnOnesArrangementImpl()
    {
        fun arrange() = run {
            block()
            this@Arrangement to MLSConfigHandler(
                userConfigRepository = userConfigRepository,
                updateSupportedProtocolsAndResolveOneOnOnes = updateSupportedProtocolsAndResolveOneOnOnes,
                SELF_USER_ID
            )
        }
    }

    private companion object {
        fun arrange(configuration: Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        val SELF_USER_ID = TestUser.USER_ID
        val MLS_CONFIG = MLSModel(
            allowedUsers = emptyList(),
            defaultProtocol = SupportedProtocol.MLS,
            supportedProtocols = setOf(SupportedProtocol.PROTEUS),
            status = Status.ENABLED
        )
    }

}

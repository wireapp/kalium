/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.di

import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.clientPlatform
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder
import com.wire.kalium.logic.feature.auth.LogoutCallbackManager
import com.wire.kalium.logic.sync.GlobalWorkScheduler
import com.wire.kalium.logic.sync.WorkSchedulerProvider
import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class GlobalBindingIdentityTest {
    @Test
    fun validationResolvesFreshUseCasesWithoutAnyGlobalResourceOrRuntimeOwner() {
        val graph = createKaliumGraph(
            mock<GlobalGraphInputs>(),
            GlobalGraphOwner { error("Validation must not start the global runtime") },
        )
        // Every input getter throws unless explicitly configured. Resolving these individual
        // bindings must not open storage/network, initialize account providers or start schedulers.
        assertNotSame(graph.globalValidateEmailUseCase, graph.globalValidateEmailUseCase)
        assertNotSame(graph.globalValidatePasswordUseCase, graph.globalValidatePasswordUseCase)
        assertNotSame(graph.globalValidateUserHandleUseCase, graph.globalValidateUserHandleUseCase)
        assertNotSame(graph.globalValidateSSOCodeUseCase, graph.globalValidateSSOCodeUseCase)
    }

    @Test
    fun schedulerBelongsToItsExistingGlobalOwnerAndExternalInstancesAreNotCopied() {
        val owners = mutableListOf<GlobalKaliumScope>()
        val schedulers = mutableListOf<GlobalWorkScheduler>()
        val schedulerProvider = object : WorkSchedulerProvider by mock<WorkSchedulerProvider>() {
            override fun globalWorkScheduler(scope: GlobalKaliumScope): GlobalWorkScheduler =
                mock<GlobalWorkScheduler>(mode = MockMode.autoUnit).also {
                    owners.add(scope)
                    schedulers.add(it)
                }
        }
        val callbackManager = mock<LogoutCallbackManager>()
        val loudness = mock<AudioNormalizedLoudnessBuilder>()
        val inputs = object : GlobalGraphInputs by mock<GlobalGraphInputs>() {
            override val workSchedulerProvider = schedulerProvider
            override val logoutCallbackManager = callbackManager
            override val audioNormalizedLoudnessBuilder = loudness
        }
        val first = GlobalKaliumScope(inputs)
        var second: GlobalKaliumScope? = null
        try {
            assertEquals(if (clientPlatform == "ios") 0 else 1, schedulers.size)
            val scheduler = first.updateApiVersionsScheduler
            assertSame(scheduler, first.updateApiVersionsScheduler)
            assertSame(first, owners.single())
            assertEquals(1, schedulers.size)
            assertSame(callbackManager, first.logoutCallbackManager)
            assertSame(schedulerProvider, first.workSchedulerProvider)
            assertSame(loudness, first.audioNormalizedLoudnessBuilder)

            val another = GlobalKaliumScope(inputs).also { second = it }
            assertNotSame(scheduler, another.updateApiVersionsScheduler)
            assertEquals(2, schedulers.size)
            assertSame(another, owners.last())
            // Configured external instances can be shared across CoreLogic owners independently
            // of Metro's per-graph SingleIn policy. Keep the exact supplied instances.
            assertSame(callbackManager, another.logoutCallbackManager)
            assertSame(schedulerProvider, another.workSchedulerProvider)
            assertSame(loudness, another.audioNormalizedLoudnessBuilder)
        } finally {
            first.cancel()
            second?.cancel()
        }
    }
}

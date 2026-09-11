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
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.logic.data.featureConfig.AllowedGlobalOperationsModel
import com.wire.kalium.logic.data.featureConfig.AssetAuditLogConfigModel
import com.wire.kalium.logic.data.featureConfig.EnableUserProfileQRCodeConfigModel
import com.wire.kalium.logic.data.featureConfig.PreventAdminlessGroupsConfigModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class FeatureConfigStatusHandlersTest {

    @Test
    fun givenAllowedGlobalOperationsEnabled_whenHandling_thenConfiguredMlsResetValueIsStored() = runTest {
        val arrangement = arrange {
            withSetMlsConversationsResetEnabledReturning(Either.Right(Unit))
        }

        val result = arrangement.allowedGlobalOperationsHandler.handle(
            AllowedGlobalOperationsModel(mlsConversationsReset = true, status = Status.ENABLED)
        )

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.setMlsConversationsResetEnabled(true) }
    }

    @Test
    fun givenAllowedGlobalOperationsDisabled_whenHandling_thenMlsResetIsDisabled() = runTest {
        val arrangement = arrange {
            withSetMlsConversationsResetEnabledReturning(Either.Right(Unit))
        }

        val result = arrangement.allowedGlobalOperationsHandler.handle(
            AllowedGlobalOperationsModel(mlsConversationsReset = true, status = Status.DISABLED)
        )

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.setMlsConversationsResetEnabled(false) }
    }

    @Test
    fun givenAssetAuditLogEnabled_whenHandling_thenFeatureIsEnabled() = runTest {
        val arrangement = arrange {
            withSetAssetAuditLogEnabledReturning(Either.Right(Unit))
        }

        val result = arrangement.assetAuditLogConfigHandler.handle(AssetAuditLogConfigModel(Status.ENABLED))

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.setAssetAuditLogEnabled(true) }
    }

    @Test
    fun givenAssetAuditLogConfigMissing_whenHandling_thenFeatureIsDisabled() = runTest {
        val arrangement = arrange {
            withSetAssetAuditLogEnabledReturning(Either.Right(Unit))
        }

        val result = arrangement.assetAuditLogConfigHandler.handle(null)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.setAssetAuditLogEnabled(false) }
    }

    @Test
    fun givenProfileQrCodeDisabled_whenHandling_thenFeatureIsDisabled() = runTest {
        val arrangement = arrange {
            withSetProfileQRCodeEnabledReturning(Either.Right(Unit))
        }

        val result = arrangement.enableUserProfileQRCodeConfigHandler.handle(
            EnableUserProfileQRCodeConfigModel(Status.DISABLED)
        )

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.setProfileQRCodeEnabled(false) }
    }

    @Test
    fun givenProfileQrCodeConfigMissing_whenHandling_thenFeatureDefaultsToEnabled() = runTest {
        val arrangement = arrange {
            withSetProfileQRCodeEnabledReturning(Either.Right(Unit))
        }

        val result = arrangement.enableUserProfileQRCodeConfigHandler.handle(null)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.setProfileQRCodeEnabled(true) }
    }

    @Test
    fun givenPreventAdminlessGroupsEnabled_whenHandling_thenFeatureIsEnabled() = runTest {
        val arrangement = arrange {
            withSetPreventAdminlessGroupsEnabledReturning(Either.Right(Unit))
        }

        val result = arrangement.preventAdminlessGroupsConfigHandler.handle(
            PreventAdminlessGroupsConfigModel(Status.ENABLED)
        )

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.setPreventAdminlessGroupsEnabled(true) }
    }

    @Test
    fun givenPreventAdminlessGroupsConfigMissing_whenHandling_thenFeatureIsDisabled() = runTest {
        val arrangement = arrange {
            withSetPreventAdminlessGroupsEnabledReturning(Either.Right(Unit))
        }

        val result = arrangement.preventAdminlessGroupsConfigHandler.handle(null)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.setPreventAdminlessGroupsEnabled(false) }
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit,
    ) : UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl() {
        val allowedGlobalOperationsHandler = AllowedGlobalOperationsHandler(userConfigRepository)
        val assetAuditLogConfigHandler = AssetAuditLogConfigHandler(userConfigRepository)
        val enableUserProfileQRCodeConfigHandler = EnableUserProfileQRCodeConfigHandler(userConfigRepository)
        val preventAdminlessGroupsConfigHandler = PreventAdminlessGroupsConfigHandler(userConfigRepository)

        suspend fun arrange() = run {
            block()
            this@Arrangement
        }
    }
}

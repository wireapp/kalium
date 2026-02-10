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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.onlyRight
import com.wire.kalium.logic.configuration.E2EISettings
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.e2ei.MLSClientE2EIStatus
import com.wire.kalium.logic.feature.e2ei.X509Identity
import com.wire.kalium.logic.feature.e2ei.usecase.GetMLSClientIdentityResult
import com.wire.kalium.logic.feature.e2ei.usecase.GetMLSClientIdentityUseCase
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Observe [E2EISettings] to notify user when setting is changed to Enabled
 */
public interface ObserveE2EIRequiredUseCase {
    /**
     * @return [Flow] of [E2EIRequiredResult]
     */
    public suspend operator fun invoke(): Flow<E2EIRequiredResult>
}

internal class ObserveE2EIRequiredUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val featureSupport: FeatureSupport,
    private val mlsClientIdentity: GetMLSClientIdentityUseCase,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val dispatcher: CoroutineDispatcher = KaliumDispatcherImpl.io,
    private val renewCertificateRandomDelay: Duration = RENEW_RANDOM_DELAY
) : ObserveE2EIRequiredUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun invoke(): Flow<E2EIRequiredResult> {
        if (!featureSupport.isMLSSupported) return flowOf(E2EIRequiredResult.NotRequired)

        return userConfigRepository
            .observeE2EINotificationTime()
            .onlyRight()
            .delayUntilNotifyTime()
            .flatMapLatest {
                observeE2EISettings().map { setting ->
                    if (!setting.isRequired) return@map E2EIRequiredResult.NotRequired

                    return@map currentClientIdProvider().fold(
                        {
                            E2EIRequiredResult.NotRequired
                        },
                        { clientId ->
                            return@map when (val identityResult = mlsClientIdentity(clientId)) {
                                is GetMLSClientIdentityResult.Failure -> E2EIRequiredResult.NotRequired
                                is GetMLSClientIdentityResult.Success -> {
                                    val clientIdentity = identityResult.identity
                                    when (clientIdentity.e2eiStatus) {
                                        MLSClientE2EIStatus.REVOKED -> E2EIRequiredResult.NotRequired
                                        MLSClientE2EIStatus.NOT_ACTIVATED -> onUserHasNoCertificate(setting)
                                        MLSClientE2EIStatus.EXPIRED -> E2EIRequiredResult.NoGracePeriod.Renew
                                        MLSClientE2EIStatus.VALID -> onUserHasValidCertificate(clientIdentity.x509Identity!!)
                                    }
                                }
                            }
                        }
                    )
                }
            }.flowOn(dispatcher)
    }

    private fun onUserHasNoCertificate(setting: E2EISettings) =
        setting.gracePeriodLeft()?.let { timeLeft ->
            E2EIRequiredResult.WithGracePeriod.Create(timeLeft)
        } ?: E2EIRequiredResult.NoGracePeriod.Create

    private fun onUserHasValidCertificate(identity: X509Identity) = if (identity.shouldRenew()) {
        E2EIRequiredResult.WithGracePeriod.Renew(identity.renewGracePeriodLeft())
    } else {
        E2EIRequiredResult.NotRequired
    }

    private fun observeE2EISettings() = userConfigRepository.observeE2EISettings().onlyRight().flowOn(dispatcher)

    private fun Flow<Instant>.delayUntilNotifyTime(): Flow<Instant> = flatMapLatest { instant ->
        val delayMillis = instant
            .minus(DateTimeUtil.currentInstant())
            .inWholeMilliseconds
            .coerceAtLeast(NO_DELAY_MS)
        flowOf(instant).onStart { delay(delayMillis) }
    }

    private fun E2EISettings.gracePeriodLeft(): Duration? = gracePeriodEnd?.let {
        if (gracePeriodEnd <= DateTimeUtil.currentInstant()) null
        else gracePeriodEnd.minus(DateTimeUtil.currentInstant())
    }

    private fun X509Identity.shouldRenew(): Boolean =
        notAfter.minus(DateTimeUtil.currentInstant())
            .minus(RENEW_CONSTANT_DELAY)
            .minus(renewCertificateRandomDelay)
            .inWholeMilliseconds <= 0

    private fun X509Identity.renewGracePeriodLeft(): Duration = notAfter.minus(DateTimeUtil.currentInstant())

    companion object {
        private const val NO_DELAY_MS = 0L
        private val RENEW_CONSTANT_DELAY = 28.days
        private val RENEW_RANDOM_DELAY = Random.nextLong(0L, 1.days.inWholeSeconds).seconds
    }
}

public sealed class E2EIRequiredResult {
    public sealed class WithGracePeriod(public open val timeLeft: Duration) : E2EIRequiredResult() {
        public data class Create(override val timeLeft: Duration) : WithGracePeriod(timeLeft)
        public data class Renew(override val timeLeft: Duration) : WithGracePeriod(timeLeft)
    }

    public sealed class NoGracePeriod : E2EIRequiredResult() {
        public data object Create : NoGracePeriod()
        public data object Renew : NoGracePeriod()
    }

    public data object NotRequired : E2EIRequiredResult()
}

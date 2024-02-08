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

import com.wire.kalium.logic.configuration.E2EISettings
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.e2ei.CertificateStatus
import com.wire.kalium.logic.feature.e2ei.usecase.GetE2EICertificateUseCaseResult
import com.wire.kalium.logic.feature.e2ei.usecase.GetE2eiCertificateUseCase
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onlyRight
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
import kotlin.time.Duration

/**
 * Observe [E2EISettings] to notify user when setting is changed to Enabled
 */
interface ObserveE2EIRequiredUseCase {
    /**
     * @return [Flow] of [E2EIRequiredResult]
     */
    operator fun invoke(): Flow<E2EIRequiredResult>
}

internal class ObserveE2EIRequiredUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val featureSupport: FeatureSupport,
    private val e2eiCertificate: GetE2eiCertificateUseCase,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val dispatcher: CoroutineDispatcher = KaliumDispatcherImpl.io
) : ObserveE2EIRequiredUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun invoke(): Flow<E2EIRequiredResult> {
        if (!featureSupport.isMLSSupported) return flowOf(E2EIRequiredResult.NotRequired)

        return userConfigRepository
            .observeE2EINotificationTime()
            .onlyRight()
            .delayUntilNotifyTime()
            .flatMapLatest {
                observeE2EISettings().map { setting ->
                    if (!setting.isRequired) {
                        E2EIRequiredResult.NotRequired
                    } else {
                        currentClientIdProvider()
                            .map { clientId ->
                                val certificateResult = e2eiCertificate(clientId)
                                when {
                                    certificateResult.isValid() -> E2EIRequiredResult.NotRequired

                                    // TODO check if it was fixed in CC
                                    // When user just logged in and app requests the certificate
                                    // than MLSClient throws ConversationNotFound exception.
                                    // So we do not ask user to create a certificate in that case
                                    // as the certificate might be already generated
                                    (certificateResult is GetE2EICertificateUseCaseResult.Failure) -> E2EIRequiredResult.NotRequired

                                    setting.isGracePeriodLeft() -> {
                                        val timeLeft = setting.gracePeriodEnd!!.minus(DateTimeUtil.currentInstant())
                                        if (certificateResult !is GetE2EICertificateUseCaseResult.Failure)
                                            E2EIRequiredResult.WithGracePeriod.Renew(timeLeft)
                                        else E2EIRequiredResult.WithGracePeriod.Create(timeLeft)
                                    }

                                    else -> {
                                        if (certificateResult !is GetE2EICertificateUseCaseResult.Failure)
                                            E2EIRequiredResult.NoGracePeriod.Renew
                                        else E2EIRequiredResult.NoGracePeriod.Create
                                    }
                                }
                            }.getOrElse { E2EIRequiredResult.NotRequired }
                    }
                }
            }
            .flowOn(dispatcher)
    }

    private fun observeE2EISettings() = userConfigRepository.observeE2EISettings().onlyRight().flowOn(dispatcher)

    private fun Flow<Instant>.delayUntilNotifyTime(): Flow<Instant> = flatMapLatest { instant ->
        val delayMillis = instant
            .minus(DateTimeUtil.currentInstant())
            .inWholeMilliseconds
            .coerceAtLeast(NO_DELAY_MS)
        flowOf(instant).onStart { delay(delayMillis) }
    }

    private fun GetE2EICertificateUseCaseResult.isValid(): Boolean =
        this is GetE2EICertificateUseCaseResult.Success && certificate.status == CertificateStatus.VALID

    private fun E2EISettings.isGracePeriodLeft(): Boolean = gracePeriodEnd != null && gracePeriodEnd > DateTimeUtil.currentInstant()

    companion object {
        private const val NO_DELAY_MS = 0L
    }
}

sealed class E2EIRequiredResult {
    sealed class WithGracePeriod(open val timeLeft: Duration) : E2EIRequiredResult() {
        data class Create(override val timeLeft: Duration) : WithGracePeriod(timeLeft)
        data class Renew(override val timeLeft: Duration) : WithGracePeriod(timeLeft)
    }

    sealed class NoGracePeriod : E2EIRequiredResult() {
        data object Create : NoGracePeriod()
        data object Renew : NoGracePeriod()
    }

    data object NotRequired : E2EIRequiredResult()
}

/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.keypackage

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.network.exceptions.KaliumException
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal interface KeyPackageUploadRetryPolicy {
    val maxAttempts: Int

    /**
     * Waits before retrying a failed upload.
     *
     * @return `false` when the server asks for a delay outside the bounded retry window.
     */
    suspend fun waitBeforeRetry(failure: NetworkFailure, failedAttempt: Int): Boolean

    suspend fun waitBeforeNextBatch()
}

internal data class KeyPackageUploadRetryConfig(
    val maxAttempts: Int = DEFAULT_MAX_UPLOAD_ATTEMPTS,
    val batchDelay: Duration = DEFAULT_BATCH_DELAY,
    val initialRetryDelay: Duration = DEFAULT_INITIAL_RETRY_DELAY,
    val maxRetryDelay: Duration = DEFAULT_MAX_RETRY_DELAY,
    val jitterFactor: Double = DEFAULT_JITTER_FACTOR,
)

@Suppress("FunctionNaming")
internal fun KeyPackageUploadRetryPolicy(
    config: KeyPackageUploadRetryConfig = KeyPackageUploadRetryConfig(),
    jitterSource: () -> Double = { Random.nextDouble() },
    delayFunction: suspend (Duration) -> Unit = { delay(it) },
): KeyPackageUploadRetryPolicy = DefaultKeyPackageUploadRetryPolicy(
    config = config,
    jitterSource = jitterSource,
    delayFunction = delayFunction,
)

private class DefaultKeyPackageUploadRetryPolicy(
    private val config: KeyPackageUploadRetryConfig,
    private val jitterSource: () -> Double,
    private val delayFunction: suspend (Duration) -> Unit,
) : KeyPackageUploadRetryPolicy {

    override val maxAttempts: Int = config.maxAttempts

    init {
        require(config.maxAttempts > 0) { "maxAttempts must be greater than zero" }
        require(!config.batchDelay.isNegative()) { "batchDelay must not be negative" }
        require(!config.initialRetryDelay.isNegative()) { "initialRetryDelay must not be negative" }
        require(config.maxRetryDelay >= config.initialRetryDelay) { "maxRetryDelay must not be less than initialRetryDelay" }
        require(config.jitterFactor in 0.0..1.0) { "jitterFactor must be between zero and one" }
    }

    override suspend fun waitBeforeRetry(failure: NetworkFailure, failedAttempt: Int): Boolean {
        val retryAfter = failure.retryAfter()
        if (retryAfter != null && retryAfter > config.maxRetryDelay) return false

        val exponentialDelay = config.initialRetryDelay
            .times(RETRY_FACTOR.pow((failedAttempt - 1).coerceAtLeast(0)))
            .coerceAtMost(config.maxRetryDelay)
        val jitteredDelay = exponentialDelay
            .times(1.0 + jitterSource().coerceIn(0.0, 1.0) * config.jitterFactor)
            .coerceAtMost(config.maxRetryDelay)
        delayFunction(maxOf(jitteredDelay, retryAfter ?: Duration.ZERO))
        return true
    }

    override suspend fun waitBeforeNextBatch() {
        delayFunction(config.batchDelay)
    }
}

private fun NetworkFailure.retryAfter(): Duration? {
    val exception = (this as? NetworkFailure.ServerMiscommunication)?.kaliumException
    val retryAfterSeconds = when (exception) {
        is KaliumException.InvalidRequestError -> exception.retryAfterSeconds
        else -> null
    }
    return retryAfterSeconds?.seconds
}

private const val DEFAULT_MAX_UPLOAD_ATTEMPTS = 3
private val DEFAULT_BATCH_DELAY = 250.milliseconds
private val DEFAULT_INITIAL_RETRY_DELAY = 1.seconds
private val DEFAULT_MAX_RETRY_DELAY = 8.seconds
private const val DEFAULT_JITTER_FACTOR = 0.2
private const val RETRY_FACTOR = 2.0

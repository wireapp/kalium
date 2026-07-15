@file:OptIn(com.wire.kalium.calling.runtime.ExperimentalCallingRuntimeApi::class)

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

package com.wire.kalium.logic.service

import com.wire.kalium.calling.runtime.CallConfig
import com.wire.kalium.calling.runtime.CallConfigResult
import com.wire.kalium.calling.runtime.CallTransport
import com.wire.kalium.calling.runtime.CallingFailure
import com.wire.kalium.calling.runtime.CallingResult
import com.wire.kalium.calling.runtime.OutgoingCallingSignal
import com.wire.kalium.calling.runtime.SftConnectionResult
import com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi
import com.wire.kalium.network.api.base.authenticated.CallApi
import com.wire.kalium.network.utils.NetworkResponse

/**
 * Encodes a calling GenericMessage, encrypts it with the signal protocol, and sends it through
 * Wire MessageApi or MLSMessageApi. Implementations must use durable crypto state and must not
 * route calling content through chat persistence.
 */
@ExperimentalKaliumServiceApi
public fun interface EncryptedCallingSignalSender {
    public suspend fun send(signal: OutgoingCallingSignal): CallingResult
}

/** Wire HTTP call configuration/SFT adapter plus encrypted signalling sender. */
@ExperimentalKaliumServiceApi
public class WireCallTransport(
    private val callApi: CallApi,
    private val signalSender: EncryptedCallingSignalSender,
) : CallTransport {
    override suspend fun getCallConfig(limit: Int?): CallConfigResult = when (val response = callApi.getCallConfig(limit)) {
        is NetworkResponse.Success -> CallConfigResult.Success(CallConfig(response.value))
        is NetworkResponse.Error -> CallConfigResult.Failure(
            CallingFailure.Transport("Call configuration request failed", response.kException),
        )
    }

    override suspend fun connectToSft(url: String, payload: ByteArray): SftConnectionResult =
        when (val response = callApi.connectToSFT(url, payload.decodeToString())) {
            is NetworkResponse.Success -> SftConnectionResult.Success(response.value)
            is NetworkResponse.Error -> SftConnectionResult.Failure(
                CallingFailure.Transport("SFT request failed", response.kException),
            )
        }

    override suspend fun sendSignal(signal: OutgoingCallingSignal): CallingResult = signalSender.send(signal)
}

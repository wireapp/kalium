/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.cryptography.PkiEnvironmentHooks
import com.wire.kalium.cryptography.PkiHttpHeader
import com.wire.kalium.cryptography.PkiHttpMethod
import com.wire.kalium.cryptography.PkiHttpResponse
import com.wire.kalium.logic.data.e2ei.PkiHttpRequests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

internal class ReceivingPkiHooks(
    private val requests: PkiHttpRequests,
    private val scope: CoroutineScope,
) : PkiEnvironmentHooks {
    override suspend fun httpRequest(
        method: PkiHttpMethod,
        url: String,
        headers: List<PkiHttpHeader>,
        body: ByteArray,
    ): PkiHttpResponse = scope.async {
        // Native callbacks have their own coroutine context. Tie IO to the owned attempt
        // so timeout/cancellation drains it before the PKI environment and lease close.
        requests.request(method, url, headers, body)
    }.await()

    override suspend fun authenticate(idp: String, keyAuth: String, acmeAud: String): String =
        error("Interactive E2EI enrollment is unavailable during receiving")

    override suspend fun getBackendNonce(): String = error("E2EI enrollment is unavailable during receiving")

    override suspend fun fetchBackendAccessToken(dpop: String): String = error("E2EI enrollment is unavailable during receiving")
}

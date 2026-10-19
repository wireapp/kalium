/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.network

import com.wire.kalium.network.exceptions.isPlatformProxyFailure
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSError
import platform.Foundation.NSPOSIXErrorDomain
import platform.Foundation.NSURLErrorDomain
import platform.posix.EAUTH
import platform.posix.ECONNREFUSED
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppleProxyFailureTest {
    @Test
    fun socksAuthenticationRejectionIsAProxyFailure() {
        assertTrue(failure(NSPOSIXErrorDomain, EAUTH.toLong()).isPlatformProxyFailure())
    }

    @Test
    fun unrelatedTransportAndTlsErrorsAreNotProxyAuthenticationFailures() {
        assertFalse(failure(NSPOSIXErrorDomain, ECONNREFUSED.toLong()).isPlatformProxyFailure())
        assertFalse(failure(NSURLErrorDomain, EAUTH.toLong()).isPlatformProxyFailure())
        assertFalse(failure(NSURLErrorDomain, -1202).isPlatformProxyFailure())
        assertFalse(failure(NSURLErrorDomain, -999).isPlatformProxyFailure())
        assertFalse(CancellationException("cancelled").isPlatformProxyFailure())
        assertFalse(IllegalStateException("HTTP 401").isPlatformProxyFailure())
    }

    private fun failure(domain: String?, code: Long) = DarwinHttpRequestException(NSError(requireNotNull(domain), code, null))
}

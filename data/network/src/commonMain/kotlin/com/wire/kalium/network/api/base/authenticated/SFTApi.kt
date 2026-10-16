/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.network.api.base.authenticated

import com.wire.kalium.network.utils.NetworkResponse

/**
 * Talks to SFT servers on behalf of AVS. SFT servers are not Wire backends,
 * so these requests are not versioned and never carry Wire credentials.
 */
interface SFTApi {
    suspend fun connectToSFT(url: String, data: String): NetworkResponse<ByteArray>
}

/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import kotlin.io.encoding.Base64

suspend fun processMLSWelcome(context: MlsCoreCryptoContext, message: String) =
    wrapMLSRequest { context.processWelcomeMessage(Base64.decode(message)) }

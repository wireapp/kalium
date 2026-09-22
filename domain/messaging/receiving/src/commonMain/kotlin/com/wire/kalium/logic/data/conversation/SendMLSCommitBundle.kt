/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.MLSTransportFailureSerialization
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapNetworkMlsFailureIfApplicable
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.MlsMessageRejectedException
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi

/** Both callers await transport and own the returned events before completing their work. */
suspend fun sendMLSCommitBundle(
    api: MLSMessageApi,
    bundle: CommitBundle,
    mapper: MLSCommitBundleMapper = MLSCommitBundleMapperImpl(),
) = wrapApiRequest { api.sendCommitBundle(mapper.toDTO(bundle)) }
    .flatMapLeft { Either.Left(it.wrapNetworkMlsFailureIfApplicable()) }
    .fold({ throw MlsMessageRejectedException(MLSTransportFailureSerialization.serialize(it)) }, { it.events })

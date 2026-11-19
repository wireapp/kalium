/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.call

/** Bounded rejection reasons; callers can diagnose Answer without logging signaling data. */
public enum class IncomingCallSignalFailure { UNAVAILABLE, SUPERSEDED }

public class IncomingCallSignalException(public val reason: IncomingCallSignalFailure) : IllegalStateException(reason.name)

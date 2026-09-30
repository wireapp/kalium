/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature

import com.wire.kalium.logic.util.PlatformView
import platform.UIKit.UIView

internal actual fun remoteVideoTestView(): PlatformView = PlatformView(UIView())

/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import okio.FileSystem

/** Host filesystem for notification preview images, absent on browser targets. */
internal expect fun receivedPreviewFileSystem(): FileSystem?

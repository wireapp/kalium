/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import okio.FileSystem

// Browsers do not expose the host filesystem used by native notification previews.
internal actual fun receivedPreviewFileSystem(): FileSystem? = null

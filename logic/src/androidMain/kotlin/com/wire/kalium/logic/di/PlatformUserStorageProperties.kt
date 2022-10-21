package com.wire.kalium.logic.di

import android.content.Context
import com.wire.kalium.logic.util.SecurityHelper

actual class PlatformUserStorageProperties internal constructor(
    val applicationContext: Context,
    val securityHelper: SecurityHelper
)

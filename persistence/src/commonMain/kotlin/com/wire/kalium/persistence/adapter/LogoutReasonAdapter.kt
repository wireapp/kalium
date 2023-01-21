package com.wire.kalium.persistence.adapter

import app.cash.sqldelight.ColumnAdapter
import com.wire.kalium.persistence.model.LogoutReason

internal object LogoutReasonAdapter : ColumnAdapter<LogoutReason, String> {
    override fun decode(databaseValue: String): LogoutReason = LogoutReason.valueOf(databaseValue)

    override fun encode(value: LogoutReason): String = value.name
}

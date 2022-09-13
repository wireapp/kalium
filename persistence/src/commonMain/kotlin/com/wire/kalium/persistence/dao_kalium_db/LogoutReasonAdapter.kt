package com.wire.kalium.persistence.dao_kalium_db

import app.cash.sqldelight.ColumnAdapter
import com.wire.kalium.persistence.model.LogoutReason

object LogoutReasonAdapter : ColumnAdapter<LogoutReason, String> {
    override fun decode(databaseValue: String): LogoutReason = LogoutReason.valueOf(databaseValue)

    override fun encode(value: LogoutReason): String = value.name
}

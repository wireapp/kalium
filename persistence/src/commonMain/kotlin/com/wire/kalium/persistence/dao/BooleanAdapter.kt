package com.wire.kalium.persistence.dao

import app.cash.sqldelight.ColumnAdapter

class BooleanAdapter : ColumnAdapter<Boolean, Long> {

    override fun decode(databaseValue: Long): Boolean {
        return databaseValue == 1L
    }

    override fun encode(value: Boolean): Long {
        return if (value) 1L else 0L
    }

}

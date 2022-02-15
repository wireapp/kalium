package com.wire.kalium.persistence.dao

import app.cash.sqldelight.ColumnAdapter

class QualifiedIDAdapter: ColumnAdapter<QualifiedID, String> {

    override fun decode(databaseValue: String): QualifiedID {
        val components = databaseValue.split("@")
        return QualifiedID(components.first(), components.last())
    }

    override fun encode(value: QualifiedID): String {
        return "${value.value}@${value.domain}"
    }

}

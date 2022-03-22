package com.wire.kalium.persistence.dao

import app.cash.sqldelight.ColumnAdapter

class QualifiedIDAdapter: ColumnAdapter<QualifiedIDEntity, String> {

    override fun decode(databaseValue: String): QualifiedIDEntity {
        val components = databaseValue.split("@")
        return QualifiedIDEntity(components.first(), components.last())
    }

    override fun encode(value: QualifiedIDEntity): String {
        return "${value.value}@${value.domain}"
    }

}

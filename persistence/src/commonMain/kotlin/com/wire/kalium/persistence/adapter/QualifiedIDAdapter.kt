package com.wire.kalium.persistence.adapter

import app.cash.sqldelight.ColumnAdapter
import com.wire.kalium.persistence.dao.QualifiedIDEntity

internal object QualifiedIDAdapter : ColumnAdapter<QualifiedIDEntity, String> {

    override fun decode(databaseValue: String): QualifiedIDEntity {
        val components = databaseValue.split("@")
        return QualifiedIDEntity(components.first(), components.last())
    }

    override fun encode(value: QualifiedIDEntity): String {
        return "${value.value}@${value.domain}"
    }

}

internal class QualifiedIDListAdapter : ColumnAdapter<List<QualifiedIDEntity>, String> {

    override fun decode(databaseValue: String): List<QualifiedIDEntity> =
        if (databaseValue.isEmpty()) listOf()
        else databaseValue.split(",").map { itemDatabaseValue ->
            val components = itemDatabaseValue.split("@")
            QualifiedIDEntity(components.first(), components.last())
        }

    override fun encode(value: List<QualifiedIDEntity>): String =
        value.joinToString(",") { "${it.value}@${it.domain}" }
}

package com.wire.kalium.persistence.dao

import app.cash.sqldelight.ColumnAdapter

class BotServiceAdapter : ColumnAdapter<BotEntity, String> {

    override fun decode(databaseValue: String): BotEntity {
        val components = databaseValue.split("@")
        return BotEntity(components.first(), components.last())
    }

    override fun encode(value: BotEntity): String {
        return "${value.id}@${value.provider}"
    }

}

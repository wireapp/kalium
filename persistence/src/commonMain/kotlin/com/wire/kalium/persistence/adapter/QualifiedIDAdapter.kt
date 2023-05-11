/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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

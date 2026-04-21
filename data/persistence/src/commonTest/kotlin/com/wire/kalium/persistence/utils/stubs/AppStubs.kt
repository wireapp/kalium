/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.dao.AppEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity

fun newAppEntity(id: String = "test") = newAppEntity(QualifiedIDEntity(id, "wire.com"), id)

fun newAppEntity(qualifiedIDEntity: QualifiedIDEntity, id: String = "test") =
    AppEntity(
        id = qualifiedIDEntity,
        name = "app$id",
        description = "description$id",
        category = "DEVELOPER",
        teamId = "team_$id",
        previewAssetId = null,
        completeAssetId = null
    )

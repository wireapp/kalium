/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.persistence.dao.publiclink

import com.wire.kalium.persistence.PublicLinksQueries
import com.wire.kalium.persistence.dao.publiclink.PublicLinkMapper.toDao
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.withContext

internal class PublicLinkDaoImpl(
    private val queries: PublicLinksQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : PublicLinkDao {

    override suspend fun insert(id: String, password: String) {
        withContext(writeDispatcher.value) {
            queries.insertLink(id, password)
        }
    }

    override suspend fun update(id: String, password: String) {
        withContext(writeDispatcher.value) {
            queries.updateLink(password, id)
        }
    }

    override suspend fun get(id: String): PublicLinkEntity? = withContext(readDispatcher.value) {
        queries.getLink(id, ::toDao)
            .executeAsOneOrNull()
    }

    override suspend fun delete(id: String) {
        withContext(writeDispatcher.value) {
            queries.deleteLink(id)
        }
    }
}

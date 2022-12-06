package com.wire.kalium.persistence.dao.message

import kotlinx.datetime.Clock

object LocalId {
    private const val LOCAL_ID_PREFIX = "local_id_"

    fun check(id: String): Boolean = id.startsWith(LOCAL_ID_PREFIX)

    fun generate(): String = LOCAL_ID_PREFIX + Clock.System.now().toEpochMilliseconds()
}

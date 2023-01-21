package com.wire.kalium.persistence.dao.message

import com.wire.kalium.util.DateTimeUtil

object LocalId {
    private const val LOCAL_ID_PREFIX = "local_id_"

    fun check(id: String): Boolean = id.startsWith(LOCAL_ID_PREFIX)

    fun generate(): String = LOCAL_ID_PREFIX + DateTimeUtil.currentInstant().toEpochMilliseconds()
}

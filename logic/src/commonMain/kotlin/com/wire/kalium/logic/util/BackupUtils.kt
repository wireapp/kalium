package com.wire.kalium.logic.util

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.util.FileNameUtil

expect val clientPlatform: String

fun backupDBName(userId: UserId): String {
    val idMapper = MapperProvider.idMapper()
    return FileNameUtil.userDBName(idMapper.toDaoModel(userId))
}

package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.dao.UserDAO

expect class Database {
    val userDAO: UserDAO
}

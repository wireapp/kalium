package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.QualifiedID

typealias UserId = QualifiedID

interface User {
    val id: UserId
}

class SelfUser(override val id: UserId) : User {

}

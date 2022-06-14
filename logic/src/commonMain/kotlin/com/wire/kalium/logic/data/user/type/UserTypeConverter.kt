package com.wire.kalium.logic.data.user.type

import com.wire.kalium.logic.data.conversation.UserType
import com.wire.kalium.persistence.dao.UserTypeEntity

interface UserTypeConverter<T> {
    val guest: T
    val federated: T
    val internal: T
}

class DomainUserTypeConverter : UserTypeConverter<UserType> {
    override val guest: UserType
        get() = UserType.GUEST
    override val federated: UserType
        get() = UserType.FEDERATED
    override val internal: UserType
        get() = UserType.INTERNAL
}

class EntityUserTypeConverter : UserTypeConverter<UserTypeEntity> {
    override val guest: UserTypeEntity
        get() = UserTypeEntity.GUEST
    override val federated: UserTypeEntity
        get() = UserTypeEntity.FEDERATED
    override val internal: UserTypeEntity
        get() = UserTypeEntity.INTERNAL
}

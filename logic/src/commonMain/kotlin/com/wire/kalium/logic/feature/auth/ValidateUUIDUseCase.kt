package com.wire.kalium.logic.feature.auth

import java.util.UUID

interface ValidateUUIDUseCase {
    operator fun invoke(uuid: String): Boolean
}

class ValidateUUIDUseCaseImpl() : ValidateUUIDUseCase {
    override fun invoke(uuid: String): Boolean = try { UUID.fromString(uuid).let { true } } catch (e: IllegalArgumentException) { false }
}

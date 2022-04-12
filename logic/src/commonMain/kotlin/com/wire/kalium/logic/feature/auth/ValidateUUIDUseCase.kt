package com.wire.kalium.logic.feature.auth

import com.benasher44.uuid.uuidFrom

interface ValidateUUIDUseCase {
    operator fun invoke(uuid: String): Boolean
}

class ValidateUUIDUseCaseImpl() : ValidateUUIDUseCase {
    override fun invoke(uuid: String): Boolean = try { uuidFrom(uuid).let { true } } catch (e: IllegalArgumentException) { false }
}

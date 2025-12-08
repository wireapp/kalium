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
package com.wire.kalium.logic.api.feature.user

import com.wire.kalium.logic.data.user.SelfUser

/**
 * This use case is responsible for retrieving the current user.
 */
public interface GetSelfUserUseCase {

    /**
     * @return current user [SelfUser]
     */
    public suspend operator fun invoke(): SelfUser?

}

/**
 * Internal implementation of [GetSelfUserUseCase] that delegates to the logic layer.
 */
internal class GetSelfUserUseCaseImpl internal constructor(
    private val getSelfUser: com.wire.kalium.logic.feature.user.GetSelfUserUseCase
) : GetSelfUserUseCase {
    override suspend operator fun invoke(): SelfUser? = getSelfUser()
}

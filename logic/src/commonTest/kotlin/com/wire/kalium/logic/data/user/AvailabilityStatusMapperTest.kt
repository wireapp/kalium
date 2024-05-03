/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.user

import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AvailabilityStatusMapperTest {

    private lateinit var availabilityStatusMapper: AvailabilityStatusMapper

    @BeforeTest
    fun setUp() {
        availabilityStatusMapper = AvailabilityStatusMapperImpl()
    }

    @Test
    fun givenDaoAvailabilityStatus_whenMappingFromDao_thenApiStatusReturn() {
        val expectedResult = UserAvailabilityStatus.AVAILABLE
        val result = availabilityStatusMapper.fromDaoAvailabilityStatusToModel(UserAvailabilityStatusEntity.AVAILABLE)

        assertEquals(expectedResult, result)
    }

    @Test
    fun givenApiAvailabilityStatus_whenMappingFromApi_thenDaoStatusReturn() {
        val expectedResult = UserAvailabilityStatus.BUSY
        val result = availabilityStatusMapper.fromDaoAvailabilityStatusToModel(UserAvailabilityStatusEntity.BUSY)

        assertEquals(expectedResult, result)
    }
}

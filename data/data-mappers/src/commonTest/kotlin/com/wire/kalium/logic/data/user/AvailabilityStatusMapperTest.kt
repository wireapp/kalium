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
import com.wire.kalium.protobuf.messages.Availability
import com.wire.kalium.util.InternalKaliumApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalKaliumApi::class)
class AvailabilityStatusMapperTest {

    private lateinit var availabilityStatusMapper: AvailabilityStatusMapper

    @BeforeTest
    fun setUp() {
        availabilityStatusMapper = AvailabilityStatusMapperImpl()
    }

    @Test
    fun givenDaoAvailabilityStatuses_whenMappingToModel_thenEveryStatusIsPreservedAndNullBecomesNone() {
        mapOf(
            UserAvailabilityStatusEntity.AVAILABLE to UserAvailabilityStatus.AVAILABLE,
            UserAvailabilityStatusEntity.BUSY to UserAvailabilityStatus.BUSY,
            UserAvailabilityStatusEntity.AWAY to UserAvailabilityStatus.AWAY,
            UserAvailabilityStatusEntity.NONE to UserAvailabilityStatus.NONE,
        ).forEach { (input, expected) ->
            assertEquals(expected, availabilityStatusMapper.fromDaoAvailabilityStatusToModel(input))
        }
        assertEquals(UserAvailabilityStatus.NONE, availabilityStatusMapper.fromDaoAvailabilityStatusToModel(null))
    }

    @Test
    fun givenModelAvailabilityStatuses_whenMappingToDao_thenEveryStatusIsPreserved() {
        mapOf(
            UserAvailabilityStatus.AVAILABLE to UserAvailabilityStatusEntity.AVAILABLE,
            UserAvailabilityStatus.BUSY to UserAvailabilityStatusEntity.BUSY,
            UserAvailabilityStatus.AWAY to UserAvailabilityStatusEntity.AWAY,
            UserAvailabilityStatus.NONE to UserAvailabilityStatusEntity.NONE,
        ).forEach { (input, expected) ->
            assertEquals(expected, availabilityStatusMapper.fromModelAvailabilityStatusToDao(input))
        }
    }

    @Test
    fun givenProtoAvailabilityStatuses_whenMappingToModel_thenEveryStatusIsPreservedAndUnrecognizedBecomesNone() {
        mapOf(
            Availability.Type.AVAILABLE to UserAvailabilityStatus.AVAILABLE,
            Availability.Type.BUSY to UserAvailabilityStatus.BUSY,
            Availability.Type.AWAY to UserAvailabilityStatus.AWAY,
            Availability.Type.NONE to UserAvailabilityStatus.NONE,
            Availability.Type.UNRECOGNIZED(99) to UserAvailabilityStatus.NONE,
        ).forEach { (input, expected) ->
            assertEquals(expected, availabilityStatusMapper.fromProtoAvailabilityToModel(Availability(input)))
        }
    }

    @Test
    fun givenModelAvailabilityStatuses_whenMappingToProto_thenEveryStatusIsPreserved() {
        mapOf(
            UserAvailabilityStatus.AVAILABLE to Availability.Type.AVAILABLE,
            UserAvailabilityStatus.BUSY to Availability.Type.BUSY,
            UserAvailabilityStatus.AWAY to Availability.Type.AWAY,
            UserAvailabilityStatus.NONE to Availability.Type.NONE,
        ).forEach { (input, expected) ->
            assertEquals(expected, availabilityStatusMapper.fromModelAvailabilityToProto(input).type)
        }
    }
}

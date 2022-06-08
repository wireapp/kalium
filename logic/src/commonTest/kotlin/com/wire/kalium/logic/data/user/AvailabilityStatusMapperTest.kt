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
        val result = availabilityStatusMapper.fromDaoAvailabilityStatusToUser(UserAvailabilityStatusEntity.AVAILABLE)

        assertEquals(expectedResult, result)
    }

    @Test
    fun givenApiAvailabilityStatus_whenMappingFromApi_thenDaoStatusReturn() {
        val expectedResult = UserAvailabilityStatus.BUSY
        val result = availabilityStatusMapper.fromDaoAvailabilityStatusToUser(UserAvailabilityStatusEntity.BUSY)

        assertEquals(expectedResult, result)
    }
}

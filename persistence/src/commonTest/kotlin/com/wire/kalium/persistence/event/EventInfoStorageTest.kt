package com.wire.kalium.persistence.event

import com.russhwolf.settings.MockSettings
import com.russhwolf.settings.Settings
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventInfoStorageTest {

    private val mockSettings: Settings = MockSettings()
    private val kaliumPreferences = KaliumPreferencesSettings(mockSettings)

    private lateinit var eventInfoStorage: EventInfoStorage

    @BeforeTest
    fun setup(){
        mockSettings.clear()
        eventInfoStorage = EventInfoStorage(kaliumPreferences)
    }

    @Test
    fun givenNoEventIdWasSaved_whenGettingTheLastEventId_thenResultShouldBeNull(){
        assertNull(eventInfoStorage.lastProcessedId)
    }

    @Test
    fun givenAnEventIdWasSaved_whenGettingTheLastEventId_thenTheSavedIdShouldBeReturned(){
        val testId = "😎EventId"
        eventInfoStorage.lastProcessedId = testId

        val result = eventInfoStorage.lastProcessedId

        assertEquals(testId, result)
    }

    @Test
    fun givenTheLastIdWasUpdatedMultipleTimes_whenGettingTheLastEventId_thenTheLatestIdShouldBeReturned(){
        val latestId = "sold"
        eventInfoStorage.lastProcessedId = "give it once"
        eventInfoStorage.lastProcessedId = "give it twice"
        eventInfoStorage.lastProcessedId = latestId

        val result = eventInfoStorage.lastProcessedId

        assertEquals(latestId, result)
    }

    @Test
    fun givenTheLastIdExisted_andWasUpdatedToNull_whenGettingTheLastEventId_thenNullShouldBeReturned(){
        eventInfoStorage.lastProcessedId = "give it once"
        eventInfoStorage.lastProcessedId = null

        val result = eventInfoStorage.lastProcessedId

        assertNull(result)
    }
}

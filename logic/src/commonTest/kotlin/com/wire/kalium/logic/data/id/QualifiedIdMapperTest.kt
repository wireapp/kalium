package com.wire.kalium.logic.data.id

import com.wire.kalium.logic.data.user.UserRepository
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class QualifiedIdMapperTest {

    @Mock
    private val userRepository = mock(classOf<UserRepository>())

    private lateinit var qualifiedIdMapper: QualifiedIdMapper

    @BeforeTest
    fun setUp() {
        qualifiedIdMapper = QualifiedIdMapperImpl(userRepository)
    }

    @Test
    fun `Given a valid string, when mapping to qualifiedId, then creates a QualifiedId with correct values`() {
        // Given
        val mockQualifiedIdValue = "mocked-value"
        val mockQualifiedIdDomain = "mocked.domain"
        val correctQualifiedIdString = "$mockQualifiedIdValue@$mockQualifiedIdDomain"
        given(userRepository)
            .function(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(QualifiedID(mockQualifiedIdValue, mockQualifiedIdDomain))

        // When
        val correctQualifiedId = qualifiedIdMapper.fromStringToQualifiedID(correctQualifiedIdString)

        // Then
        assertEquals(correctQualifiedId.value, mockQualifiedIdValue)
        assertEquals(correctQualifiedId.domain, mockQualifiedIdDomain)
    }

    @Test
    fun `Given a string without domain, when mapping to qualifiedId, then returns a correct qualifiedID with a fallback domain`() {
        // Given
        val fallbackDomain = "wire.com"
        val conversationId = "conversationId"

        given(userRepository)
            .invocation { getSelfUserId() }
            .thenReturn(QualifiedID(conversationId, fallbackDomain))

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = conversationId, domain = fallbackDomain),
            result
        )
    }

    @Test
    fun `Given a valid string that starts with '@', when mapping to qualifiedId, then returns a correct qualifiedID`() {
        // Given
        val conversationId = "@conversationId@dom"

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "@conversationId", domain = "dom"),
            result
        )
    }

    @Test
    fun `Given a valid string that contains '@' in the middle, when mapping to qualifiedId, then returns a correct qualifiedID`() {
        // Given
        val conversationId = "convers@ationId@dom"

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "convers@ationId", domain = "dom"),
            result
        )
    }

    @Test
    fun `Given a valid string that ends with '@', when mapping to qualifiedId, then returns a correct qualifiedID`() {
        // Given
        val conversationId = "conversationId@@dom"

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "conversationId@", domain = "dom"),
            result
        )
    }

}

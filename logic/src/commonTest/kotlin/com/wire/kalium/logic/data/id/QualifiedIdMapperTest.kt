package com.wire.kalium.logic.data.id

import com.wire.kalium.logic.data.user.UserId
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

    private val selfUserId = UserId("selfUserId", "selfDomain")

    private lateinit var qualifiedIdMapper: QualifiedIdMapper

    @BeforeTest
    fun setUp() {
        qualifiedIdMapper = QualifiedIdMapperImpl(selfUserId)
    }

    @Test
    fun givenAValidString_whenMappingToQualifiedId_thenCreatesAQualifiedIdWithACorrectValues() {
        // Given
        val mockQualifiedIdValue = "mocked-value"
        val mockQualifiedIdDomain = "mocked.domain"
        val correctQualifiedIdString = "$mockQualifiedIdValue@$mockQualifiedIdDomain"

        // When
        val correctQualifiedId = qualifiedIdMapper.fromStringToQualifiedID(correctQualifiedIdString)

        // Then
        assertEquals(correctQualifiedId.value, mockQualifiedIdValue)
        assertEquals(correctQualifiedId.domain, mockQualifiedIdDomain)
    }

    @Test
    fun givenAStringWithoutDomain_whenMappingToQualifiedId_thenReturnsACorrectQualifiedIDWithAFallbackDomain() {
        // Given
        val fallbackDomain = selfUserId.domain
        val conversationId = "conversationId"

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = conversationId, domain = fallbackDomain),
            result
        )
    }

    @Test
    fun givenAStringWithoutDomainThatEndsWithAtSign_whenMappingToQualifiedId_thenReturnsACorrectQualifiedIDWithAFallbackDomain() {
        // Given
        val fallbackDomain = selfUserId.domain
        val conversationId = "conversationId@"

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "conversationId", domain = fallbackDomain),
            result
        )
    }

    @Test
    fun givenAValidStringThatStartsWithAtSign_whenMappingToQualifiedId_thenReturnsACorrectQualifiedIDWithAFallbackDomain() {
        // Given
        val fallbackDomain = selfUserId.domain
        val conversationId = "@conversationId"

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "conversationId", domain = fallbackDomain),
            result
        )
    }

    @Test
    fun givenAValidStringThatStartsAndEndsWithAtSign_whenMappingToQualifiedId_thenReturnsACorrectQualifiedIDWithAFallbackDomain() {
        // Given
        val fallbackDomain = selfUserId.domain
        val conversationId = "@conversationId@"

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "conversationId", domain = fallbackDomain),
            result
        )
    }

    @Test
    fun givenAValidStringThatStartsWithAtSignAndContainsAnotherAtSign_whenMappingToQualifiedId_thenReturnsACorrectQualifiedID() {
        // Given
        val conversationId = "@conversationId@dom"

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "conversationId", domain = "dom"),
            result
        )
    }

    @Test
    fun givenAValidStringThatContainsAtSignInTheMiddle_whenMappingToQualifiedId_thenReturnsACorrectQualifiedID() {
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
    fun givenAValidStringThatStartsWithAtSignContainsAtSignInTheMiddle_whenMappingToQualifiedId_thenReturnsACorrectQualifiedID() {
        // Given
        val conversationId = "@convers@ationId@dom"

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "convers@ationId", domain = "dom"),
            result
        )
    }

    @Test
    fun givenAValidStringThatEndsWithAtSign_whenMappingToQualifiedId_thenReturnsACorrectQualifiedID() {
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

    @Test
    fun givenAnEmptyString_whenMappingToQualifiedId_thenReturnsAnEmptyQualifiedID() {
        // Given
        val conversationId = ""

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "", domain = ""),
            result
        )
    }

    @Test
    fun givenAStringWithOnlyAtSign_whenMappingToQualifiedId_thenReturnsAnEmptyQualifiedID() {
        // Given
        val conversationId = "@"

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "", domain = ""),
            result
        )
    }

}

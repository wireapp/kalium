package com.wire.kalium.logic.data.id

import com.wire.kalium.logic.framework.TestUser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class QualifiedIdMapperTest {

    private val selfUserId = TestUser.USER_ID

    private fun createMapper(selfUserId: QualifiedID = this.selfUserId): QualifiedIdMapper = QualifiedIdMapperImpl(selfUserId)

    @Test
    fun givenAValidString_whenMappingToQualifiedId_thenCreatesAQualifiedIdWithACorrectValues() = runTest {
        // Given
        val mockQualifiedIdValue = "mocked-value"
        val mockQualifiedIdDomain = "mocked.domain"
        val correctQualifiedIdString = "$mockQualifiedIdValue@$mockQualifiedIdDomain"

        val qualifiedIdMapper = createMapper(QualifiedID(mockQualifiedIdValue, mockQualifiedIdDomain))

        // When
        val correctQualifiedId = qualifiedIdMapper.fromStringToQualifiedID(correctQualifiedIdString)

        // Then
        assertEquals(correctQualifiedId.value, mockQualifiedIdValue)
        assertEquals(correctQualifiedId.domain, mockQualifiedIdDomain)
    }

    @Test
    fun givenAStringWithoutDomain_whenMappingToQualifiedId_thenReturnsACorrectQualifiedIDWithAFallbackDomain() = runTest {
        // Given
        val fallbackDomain = selfUserId.domain
        val conversationId = "conversationId"

        val qualifiedIdMapper = createMapper(QualifiedID(conversationId, fallbackDomain))

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = conversationId, domain = fallbackDomain),
            result
        )
    }

    @Test
    fun givenAStringWithoutDomainThatEndsWithAtSign_whenMappingToQualifiedId_thenReturnsACorrectQualifiedIDWithAFallbackDomain() = runTest {
        // Given
        val fallbackDomain = selfUserId.domain
        val conversationId = "conversationId@"

        val qualifiedIdMapper = createMapper(QualifiedID(conversationId, fallbackDomain))

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "conversationId", domain = fallbackDomain),
            result
        )
    }

    @Test
    fun givenAValidStringThatStartsWithAtSign_whenMappingToQualifiedId_thenReturnsACorrectQualifiedIDWithAFallbackDomain() = runTest {
        // Given
        val fallbackDomain = selfUserId.domain
        val conversationId = "@conversationId"

        val qualifiedIdMapper = createMapper(QualifiedID(conversationId, fallbackDomain))

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "conversationId", domain = fallbackDomain),
            result
        )
    }

    @Test
    fun givenAValidStringThatStartsAndEndsWithAtSign_whenMappingToQualifiedId_thenReturnsACorrectQualifiedIDWithAFallbackDomain() =
        runTest {
            // Given
            val fallbackDomain = selfUserId.domain
            val conversationId = "@conversationId@"

            val qualifiedIdMapper = createMapper(QualifiedID(conversationId, fallbackDomain))

            // When
            val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

            // Then
            assertEquals(
                QualifiedID(value = "conversationId", domain = fallbackDomain),
                result
            )
        }

    @Test
    fun givenAValidStringThatStartsWithAtSignAndContainsAnotherAtSign_whenMappingToQualifiedId_thenReturnsACorrectQualifiedID() = runTest {
        // Given
        val conversationId = "@conversationId@dom"
        val qualifiedIdMapper = createMapper()

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "conversationId", domain = "dom"),
            result
        )
    }

    @Test
    fun givenAValidStringThatContainsAtSignInTheMiddle_whenMappingToQualifiedId_thenReturnsACorrectQualifiedID() = runTest {
        // Given
        val conversationId = "convers@ationId@dom"
        val qualifiedIdMapper = createMapper()

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "convers@ationId", domain = "dom"),
            result
        )
    }

    @Test
    fun givenAValidStringThatStartsWithAtSignContainsAtSignInTheMiddle_whenMappingToQualifiedId_thenReturnsACorrectQualifiedID() = runTest {
        // Given
        val conversationId = "@convers@ationId@dom"
        val qualifiedIdMapper = createMapper()

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "convers@ationId", domain = "dom"),
            result
        )
    }

    @Test
    fun givenAValidStringThatEndsWithAtSign_whenMappingToQualifiedId_thenReturnsACorrectQualifiedID() = runTest {
        // Given
        val conversationId = "conversationId@@dom"
        val qualifiedIdMapper = createMapper()

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "conversationId@", domain = "dom"),
            result
        )
    }

    @Test
    fun givenAnEmptyString_whenMappingToQualifiedId_thenReturnsAnEmptyQualifiedID() = runTest {
        // Given
        val conversationId = ""
        val qualifiedIdMapper = createMapper()

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "", domain = ""),
            result
        )
    }

    @Test
    fun givenAStringWithOnlyAtSign_whenMappingToQualifiedId_thenReturnsAnEmptyQualifiedID() = runTest {
        // Given
        val conversationId = "@"
        val qualifiedIdMapper = createMapper()

        // When
        val result = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        // Then
        assertEquals(
            QualifiedID(value = "", domain = ""),
            result
        )
    }

}

package com.wire.kalium.logic.data.id

import kotlin.test.Test
import kotlin.test.assertEquals

class QualifiedIDTest {

    private val domain = "wire.com"

    @Test
    fun givenAUserQuery_whenMappingToQualifiedId_thenTheValueAndDomainShouldBeMappedCorrectly() {
        val query = "user@$domain"
        val qualifiedID = query.parseIntoQualifiedID()

        assertEquals(qualifiedID.value, "user")
        assertEquals(qualifiedID.domain, domain)
    }

    @Test
    fun givenAUserQueryWithoutDomain_whenMappingToQualifiedId_thenTheValueAndDomainShouldBeMappedCorrectly() {
        val query = "user"
        val qualifiedID = query.parseIntoQualifiedID()

        assertEquals(qualifiedID.value, "user")
        assertEquals(qualifiedID.domain, "")
    }

    @Test
    fun givenAUserQueryWithAtWithoutDomain_whenMappingToQualifiedId_thenTheValueAndDomainShouldBeMappedCorrectly() {
        val query = "user@"
        val qualifiedID = query.parseIntoQualifiedID()

        assertEquals(qualifiedID.value, "user")
        assertEquals(qualifiedID.domain, "")
    }

    @Test
    fun givenAUserQueryWithAtAtStartWithoutDomain_whenMappingToQualifiedId_thenTheValueAndDomainShouldBeMappedCorrectly() {
        val query = "@user"
        val qualifiedID = query.parseIntoQualifiedID()

        assertEquals(qualifiedID.value, "user")
        assertEquals(qualifiedID.domain, "")
    }

    @Test
    fun givenAUserQueryWithManyAts_whenMappingToQualifiedId_thenTheValueAndDomainShouldBeMappedCorrectly() {
        val query = "@user@asdf@$domain"
        val qualifiedID = query.parseIntoQualifiedID()

        assertEquals(qualifiedID.value, "user")
        assertEquals(qualifiedID.domain, domain)
    }
}

package com.wire.kalium.logic.data.id

import kotlin.test.Test
import kotlin.test.assertEquals

class ParseSearchQueryToQualifiedIDTest {
    @Test
    fun givenUser_correctFederatedId_splitToValueAndDomain() {
        val searchQuery = "damian@wire.com"

        assertEquals(searchQuery.parseSearchQueryToQualifiedID(), QualifiedID("damian", "wire.com"))
    }

    @Test
    fun givenUser_correctFederatedId_doubleDotFederation_splitToValueAndDomain() {
        val searchQuery = "damian@wire.com.pl"

        assertEquals(searchQuery.parseSearchQueryToQualifiedID(), QualifiedID("damian", "wire.com.pl"))
    }


    @Test
    fun givenUser_correctFederatedId_containsDoubleAtCharacter_everythingIsValue_domainIsEmpty() {
        val searchQuery = "dam@ian@wire.com.pl.anything"

        assertEquals(searchQuery.parseSearchQueryToQualifiedID(), QualifiedID("dam@ian@wire.com.pl.anything", ""))
    }

    @Test
    fun givenUser_correctFederatedId_missingAtCharacter_everythingIsValue_domainIsEmpty() {
        val searchQuery = "damianwire.com.pl.anything"

        assertEquals(searchQuery.parseSearchQueryToQualifiedID(), QualifiedID("damianwire.com.pl.anything", ""))
    }


    @Test
    fun givenUser_correctFederatedId_dotBeforeAtCharacter_everythingIsValue_domainIsEmpty() {
        val searchQuery = "wire.com@damian"

        assertEquals(searchQuery.parseSearchQueryToQualifiedID(), QualifiedID("wire.com@damian", ""))
    }

    @Test
    fun givenUser_correctFederatedId_startsWithAtCharacter_everythingIsValue_domainIsEmpty() {
        val searchQuery = "@damian.wire.com"

        assertEquals(searchQuery.parseSearchQueryToQualifiedID(), QualifiedID("@damian.wire.com", ""))
    }

    @Test
    fun givenUser_correctFederatedId_endsWithAtCharacter_everythingIsValue_domainIsEmpty() {
        val searchQuery = "damian.wire.com@"

        assertEquals(searchQuery.parseSearchQueryToQualifiedID(), QualifiedID("damian.wire.com@", ""))
    }
}

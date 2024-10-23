import com.wire.kalium.logic.data.id.QualifiedID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class QualifiedIdTest {
    @Test
    fun givenIdsWithoutDomains_whenEqualsIgnoringBlankDomain_thenReturnsTrue() {
        // Given
        val qualifiedId1 = QualifiedID("id1", "")
        val qualifiedId2 = QualifiedID("id1", "")

        // When
        val result = qualifiedId1.equalsIgnoringBlankDomain(qualifiedId2)

        // Then
        assertTrue(result)
    }

    @Test
    fun givenOneIdWithoutDomain_whenEqualsIgnoringBlankDomain_thenReturnsTrue() {
        // Given
        val qualifiedId1 = QualifiedID("id1", "")
        val qualifiedId2 = QualifiedID("id1", "domain")

        // When
        val result = qualifiedId1.equalsIgnoringBlankDomain(qualifiedId2)

        // Then
        assertTrue(result)
    }

    @Test
    fun givenIdsWithSameDomains_whenEqualsIgnoringBlankDomain_thenReturnsTrue() {
        // Given
        val qualifiedId1 = QualifiedID("id1", "domain")
        val qualifiedId2 = QualifiedID("id1", "domain")

        // When
        val result = qualifiedId1.equalsIgnoringBlankDomain(qualifiedId2)

        // Then
        assertTrue(result)
    }

    @Test
    fun givenIdsWithDifferentDomains_whenEqualsIgnoringBlankDomain_thenReturnsFalse() {
        // Given
        val qualifiedId1 = QualifiedID("id1", "domain1")
        val qualifiedId2 = QualifiedID("id1", "domain2")

        // When
        val result = qualifiedId1.equalsIgnoringBlankDomain(qualifiedId2)

        // Then
        assertTrue(!result)
    }

    @Test
    fun givenIdsWithDifferentValues_whenEqualsIgnoringBlankDomain_thenReturnsFalse() {
        // Given
        val qualifiedId1 = QualifiedID("id1", "")
        val qualifiedId2 = QualifiedID("id2", "")

        // When
        val result = qualifiedId1.equalsIgnoringBlankDomain(qualifiedId2)

        // Then
        assertFalse(result)
    }
}
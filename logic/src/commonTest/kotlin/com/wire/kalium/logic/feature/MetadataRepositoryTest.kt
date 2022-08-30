package com.wire.kalium.logic.feature

class MetadataRepositoryTest {
    // check reset
    // check set new value
    // check is passed
//     @Test
//     fun givenNoPreviousTimestamp_whenlastKeyPackageCountCheck_thenReturnsDistantPast() = runTest {
//         val (_, keyPackageRepository) = KeyPackageRepositoryTest.Arrangement()
//             .withLastKeyPackageCountCheckTimestamp(null)
//             .arrange()
//
//         val result = keyPackageRepository.lastKeyPackageCountCheck()
//
//         result.shouldSucceed { actualTimestamp ->
//             assertEquals(actualTimestamp, Instant.DISTANT_PAST)
//         }
//     }
//
//     @Test
//     fun givenPreviousTimestamp_whenlastKeyPackageCountCheck_thenReturnsExpectedTimestamp() = runTest {
//         val expectedTimestamp = Instant.DISTANT_FUTURE
//         val (_, keyPackageRepository) = KeyPackageRepositoryTest.Arrangement()
//             .withLastKeyPackageCountCheckTimestamp(expectedTimestamp)
//             .arrange()
//
//         val result = keyPackageRepository.lastKeyPackageCountCheck()
//
//         result.shouldSucceed { actualTimestamp ->
//             assertEquals(actualTimestamp, expectedTimestamp)
//         }
//     }
//     fun withLastKeyPackageCountCheckTimestamp(timestamp: Instant?) = apply {
//         given(metadataDAO).suspendFunction(metadataDAO::valueByKeyFlow)
//             .whenInvokedWith(eq(LAST_KEY_PACKAGE_COUNT_CHECK))
//             .thenReturn(flowOf(timestamp?.toString()))
//     }
}

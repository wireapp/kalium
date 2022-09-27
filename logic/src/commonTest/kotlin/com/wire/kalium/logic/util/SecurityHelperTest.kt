package com.wire.kalium.logic.util

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import io.ktor.util.encodeBase64
import io.mockative.Mock
import io.mockative.any
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecurityHelperTest {

    @Mock
    private val passphraseStorage: PassphraseStorage = configure(mock(PassphraseStorage::class)) {
        stubsUnitByDefault = true
    }

    private lateinit var securityHelper: SecurityHelper

    @BeforeTest
    fun setup() {
        securityHelper = SecurityHelper(passphraseStorage)
    }

    @Test
    fun whenCallingGlobalDBSecretTwice_thenTheSameValueIsReturned() {
        given(passphraseStorage).function(passphraseStorage::getPassphrase).whenInvokedWith(any()).then { null }
        given(passphraseStorage).function(passphraseStorage::setPassphrase).whenInvokedWith(any(), any())
        val secret1 = securityHelper.globalDBSecret()
        given(passphraseStorage)
            .function(passphraseStorage::getPassphrase)
            .whenInvokedWith(any())
            .then { secret1.value.encodeBase64() }

        val secret2 = securityHelper.globalDBSecret()
        assertTrue(secret1.value.contentEquals(secret2.value))

        verify(passphraseStorage).function(passphraseStorage::getPassphrase).with(any()).wasInvoked(exactly = twice)
        verify(passphraseStorage)
            .function(passphraseStorage::setPassphrase)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun whenCallingUserDBSecretTwice_thenTheSameValueIsReturned() {
        given(passphraseStorage).function(passphraseStorage::getPassphrase).whenInvokedWith(any()).then { null }
        given(passphraseStorage).function(passphraseStorage::setPassphrase).whenInvokedWith(any(), any())
        val userId = UserId("df8703fb-bbab-4b10-a369-0ef781a17cf5", "wire.com")
        val secret1 = securityHelper.userDBSecret(userId)
        given(passphraseStorage)
            .function(passphraseStorage::getPassphrase)
            .whenInvokedWith(any())
            .then { secret1.value.encodeBase64() }

        val secret2 = securityHelper.userDBSecret(userId)
        assertTrue(secret1.value.contentEquals(secret2.value))
        verify(passphraseStorage).function(passphraseStorage::getPassphrase).with(any()).wasInvoked(exactly = twice)
        verify(passphraseStorage)
            .function(passphraseStorage::setPassphrase)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun whenCallingMlsDBSecretTwiceForTheSameUser_thenTheSameValueIsReturned() {
        given(passphraseStorage).function(passphraseStorage::getPassphrase).whenInvokedWith(any()).then { null }
        given(passphraseStorage).function(passphraseStorage::setPassphrase).whenInvokedWith(any(), any())
        val userId = UserId("df8703fb-bbab-4b10-a369-0ef781a17cf5", "wire.com")
        val secret1 = securityHelper.mlsDBSecret(userId)
        given(passphraseStorage)
            .function(passphraseStorage::getPassphrase).whenInvokedWith(any())
            .then { secret1.value }

        val secret2 = securityHelper.mlsDBSecret(userId)
        assertEquals(secret1.value, secret2.value)
        verify(passphraseStorage).function(passphraseStorage::getPassphrase).with(any()).wasInvoked(exactly = twice)
        verify(passphraseStorage)
            .function(passphraseStorage::setPassphrase)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }
}

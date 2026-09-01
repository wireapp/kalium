/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
@file:Suppress("NoUnusedImports", "TooGenericExceptionCaught")

package com.wire.kalium.cryptography

import com.wire.crypto.CipherSuite
import com.wire.crypto.ClientId
import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoContext
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.Credential
import com.wire.crypto.CredentialRef
import com.wire.crypto.CredentialType as CoreCredentialType
import com.wire.crypto.Disposable
import com.wire.crypto.ExternalSender
import com.wire.crypto.KeyPackageRef
import com.wire.crypto.MlsException
import com.wire.crypto.Uuid
import com.wire.crypto.toGroupInfo
import com.wire.crypto.toMLSKeyPackage
import com.wire.crypto.toWelcome
import com.wire.kalium.cryptography.utils.toBundle
import com.wire.kalium.cryptography.utils.toCrypto
import com.wire.kalium.cryptography.utils.toCryptography

@Suppress("TooManyFunctions")
class MLSClientImpl private constructor(
    private val coreCrypto: CoreCrypto,
    private val clientId: ClientId,
    private val defaultCipherSuite: CipherSuite,
    private var activeCredentialRef: CredentialRef,
    private val onClose: () -> Unit
) : MLSClient {

    private var isClosed = false

    override suspend fun close() {
        if (isClosed) return
        isClosed = true
        try {
            clientId.close()
        } finally {
            try {
                coreCrypto.close()
            } finally {
                onClose()
            }
        }
    }

    override fun getDefaultCipherSuite(): MLSCiphersuite = defaultCipherSuite.toCryptography()

    override suspend fun getPublicKey(): Pair<ByteArray, MLSCiphersuite> =
        coreCrypto.publicKey(activeCredentialRef) to defaultCipherSuite.toCryptography()

    override suspend fun getCredentialRef(credentialType: CredentialType): CryptoCredentialRef? =
        coreCrypto.findCredentials(
            clientId = clientId,
            cipherSuite = defaultCipherSuite,
            credentialType = credentialType.toCrypto()
        ).takeNewest()?.let(::CryptoCredentialRefImpl)

    override suspend fun getCredentialRefs(credentialType: CredentialType): List<CryptoCredentialRef> {
        val credentialRefs = coreCrypto.findCredentials(
            clientId = clientId,
            cipherSuite = defaultCipherSuite,
            credentialType = credentialType.toCrypto()
        )
        return credentialRefs.sortedByDescending { it.earliestValidity() }.map(::CryptoCredentialRefImpl)
    }

    override suspend fun getGroupState(groupId: MLSGroupId): E2EIConversationState = groupId.toCrypto().useNative {
        coreCrypto.e2eiConversationState(it).toCryptography()
    }

    override suspend fun getUserIdentities(
        groupId: MLSGroupId,
        users: List<CryptoQualifiedID>
    ): Map<String, List<WireIdentity>> = groupId.toCrypto().useNative { nativeGroupId ->
        users.useNativeMapped({ Uuid(it.value) }) { nativeUsers ->
            coreCrypto.getUserIdentities(nativeGroupId, nativeUsers).toCryptographyAndClose()
        }
    }

    override suspend fun <R> transaction(name: String, block: suspend (context: MlsCoreCryptoContext) -> R): R {
        var transactionCredential = activeCredentialRef
        val result = coreCrypto.transaction(name) { context ->
            block(
                mlsCoreCryptoContext(
                    context = context,
                    selectedCredential = { transactionCredential },
                    onCredentialSelected = { transactionCredential = it }
                )
            )
        }

        activeCredentialRef = transactionCredential
        return result
    }

    private fun mlsCoreCryptoContext(
        context: CoreCryptoContext,
        selectedCredential: () -> CredentialRef,
        onCredentialSelected: (CredentialRef) -> Unit
    ) = object : MlsCoreCryptoContext {
        override fun getDefaultCipherSuite(): MLSCiphersuite = defaultCipherSuite.toCryptography()

        override suspend fun generateKeyPackages(
            amount: Int,
            credentialRef: CryptoCredentialRef?
        ): List<ByteArray> {
            require(amount >= 0) { "Key package amount must not be negative" }
            val effectiveCredential = credentialRef?.unwrap() ?: selectedCredential()
            return List(amount) {
                context.generateKeyPackage(effectiveCredential).let { keyPackage ->
                    try {
                        keyPackage.serialize()
                    } finally {
                        keyPackage.close()
                    }
                }
            }
        }

        override suspend fun validKeyPackageCount(): ULong {
            return context.getKeyPackages()
                .countMatching(selectedCredential())
                .toULong()
        }

        override suspend fun updateKeyingMaterial(groupId: MLSGroupId) {
            groupId.toCrypto().useNative { context.updateKeyingMaterial(it) }
        }

        override suspend fun joinByExternalCommit(publicGroupState: ByteArray): MLSGroupId {
            val groupInfo = publicGroupState.toGroupInfo()
            return try {
                context.joinByExternalCommit(groupInfo, selectedCredential()).useNative { it.toCryptography() }
            } finally {
                groupInfo.close()
            }
        }

        override suspend fun conversationExists(groupId: MLSGroupId): Boolean = groupId.toCrypto().useNative {
            context.conversationExists(it)
        }

        override suspend fun conversationEpoch(groupId: MLSGroupId): ULong = groupId.toCrypto().useNative {
            context.conversationEpoch(it)
        }

        override suspend fun createConversation(groupId: MLSGroupId, externalSenders: ByteArray) {
            kaliumLogger.d("createConversation: using defaultCipherSuite=$defaultCipherSuite")
            val credentialRef = selectedCredential()
            val externalSender = ExternalSender.parse(externalSenders, credentialRef.signatureScheme())
            try {
                groupId.toCrypto().useNative {
                    context.createConversation(
                        conversationId = it,
                        credentialRef = credentialRef,
                        externalSender = externalSender
                    )
                }
            } finally {
                externalSender.close()
            }
        }

        override suspend fun getExternalSenders(groupId: MLSGroupId): ExternalSenderKey = groupId.toCrypto().useNative {
            context.getExternalSender(it).let { externalSender ->
                try {
                    ExternalSenderKey(externalSender.serialize())
                } finally {
                    externalSender.close()
                }
            }
        }

        override suspend fun wipeConversation(groupId: MLSGroupId) {
            groupId.toCrypto().useNative { context.wipeConversation(it) }
        }

        override suspend fun processWelcomeMessage(message: WelcomeMessage): MLSGroupId {
            val welcome = message.toWelcome()
            return try {
                context.processWelcomeMessage(welcome).useNative { it.toCryptography() }
            } finally {
                welcome.close()
            }
        }

        override suspend fun commitPendingProposals(groupId: MLSGroupId) {
            groupId.toCrypto().useNative { context.commitPendingProposals(it) }
        }

        override suspend fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage = groupId.toCrypto().useNative {
            context.encryptMessage(it, message)
        }

        override suspend fun decryptMessage(
            groupId: MLSGroupId,
            message: ByteArray,
        ): MLSDecryptResult = try {
            groupId.toCrypto().useNative { nativeGroupId ->
                val decryptedMessage = context.decryptMessage(nativeGroupId, message)
                try {
                    val bufferedMessages = when (decryptedMessage) {
                        is com.wire.crypto.DecryptedMessage.Commit -> decryptedMessage.bufferedMessages.orEmpty()
                        is com.wire.crypto.DecryptedMessage.Proposal,
                        is com.wire.crypto.DecryptedMessage.Text -> emptyList()
                    }

                    MLSDecryptResult.Success(
                        buildList {
                            add(decryptedMessage.toBundle())
                            addAll(bufferedMessages.map { it.toBundle() })
                        }
                    )
                } finally {
                    decryptedMessage.destroy()
                }
            }
        } catch (throwable: CoreCryptoException.Mls) {
            when (throwable.mlsError) {
                is MlsException.BufferedFutureMessage -> MLSDecryptResult.BufferedFutureMessage
                is MlsException.BufferedCommit -> MLSDecryptResult.BufferedCommit
                else -> throw throwable
            }
        }

        override suspend fun members(groupId: MLSGroupId): List<CryptoQualifiedClientId> = groupId.toCrypto().useNative {
            context.getClientIds(it).mapToCryptographyAndClose()
        }

        override suspend fun addMember(groupId: MLSGroupId, membersKeyPackages: List<MLSKeyPackage>) {
            if (membersKeyPackages.isEmpty()) return

            groupId.toCrypto().useNative { nativeGroupId ->
                membersKeyPackages.useNativeMapped({ it.toMLSKeyPackage() }) { keyPackages ->
                    context.addClientsToConversation(nativeGroupId, keyPackages)
                }
            }
        }

        override suspend fun removeMember(groupId: MLSGroupId, members: List<CryptoQualifiedClientId>) {
            if (members.isEmpty()) return

            groupId.toCrypto().useNative { nativeGroupId ->
                members.useNativeMapped({ it.toCoreCryptoClientId() }) { nativeMembers ->
                    context.removeClientsFromConversation(nativeGroupId, nativeMembers)
                }
            }
        }

        override suspend fun deriveSecret(groupId: MLSGroupId, keyLength: UInt): ByteArray = groupId.toCrypto().useNative {
            context.exportSecretKey(it, keyLength).let { secret ->
                try {
                    secret.copyBytes()
                } finally {
                    secret.close()
                }
            }
        }

        override fun selectCredential(credentialRef: CryptoCredentialRef) {
            if (credentialRef.unwrap().matches(selectedCredential())) return
            onCredentialSelected(credentialRef.unwrap())
        }

        override suspend fun setConversationCredential(groupId: MLSGroupId, credentialRef: CryptoCredentialRef) {
            groupId.toCrypto().useNative {
                context.setConversationCredential(it, credentialRef.unwrap())
            }
        }

        override suspend fun getConversationCredentialRef(groupId: MLSGroupId): CryptoCredentialRef = groupId.toCrypto().useNative {
            CryptoCredentialRefImpl(context.conversationCredential(it))
        }

        override suspend fun removeKeyPackages(credentialRef: CryptoCredentialRef) {
            context.removeKeyPackagesFor(credentialRef.unwrap())
        }

        override suspend fun removeCredential(credentialRef: CryptoCredentialRef) {
            val credential = credentialRef.unwrap()
            require(!credential.matches(selectedCredential())) {
                "Select a replacement credential before removing the active credential"
            }
            context.removeCredential(credential)
        }

        override suspend fun checkCredentials() {
            context.checkCredentials()
        }

        override suspend fun isE2EIEnabled(): Boolean = context.e2eiIsEnabled(defaultCipherSuite)

        override suspend fun getGroupState(groupId: MLSGroupId): E2EIConversationState = groupId.toCrypto().useNative {
            context.e2eiConversationState(it).toCryptography()
        }

        override suspend fun getDeviceIdentities(
            groupId: MLSGroupId,
            clients: List<CryptoQualifiedClientId>
        ): List<WireIdentity> = groupId.toCrypto().useNative { nativeGroupId ->
            clients.useNativeMapped({ it.toCoreCryptoClientId() }) { nativeClients ->
                context.getDeviceIdentities(nativeGroupId, nativeClients).toCryptographyAndClose()
            }
        }

        override suspend fun getUserIdentities(
            groupId: MLSGroupId,
            users: List<CryptoQualifiedID>
        ): Map<String, List<WireIdentity>> = groupId.toCrypto().useNative { nativeGroupId ->
            users.useNativeMapped({ Uuid(it.value) }) { nativeUsers ->
                context.getUserIdentities(nativeGroupId, nativeUsers).toCryptographyAndClose()
            }
        }

        override suspend fun removeStaleKeyPackages() {
            val keyPackageRefs = context.getKeyPackages()
            try {
                keyPackageRefs.forEach { keyPackageRef ->
                    if (!keyPackageRef.isValid()) context.removeKeyPackage(keyPackageRef)
                }
            } finally {
                keyPackageRefs.forEach { it.close() }
            }
        }
    }

    companion object {
        /**
         * Creates the Kalium facade and restores the credential used for new MLS operations.
         *
         * Existing X509 credentials are preferred so an upgraded E2EI client does not silently
         * fall back to Basic. A Basic credential is added once for a new client and reused on
         * subsequent initializations.
         */
        suspend fun create(
            coreCrypto: CoreCrypto,
            clientId: ClientId,
            defaultCipherSuite: CipherSuite,
            onClose: () -> Unit = {}
        ): MLSClientImpl {
            val x509Credential = coreCrypto.findCredentials(
                clientId = clientId,
                cipherSuite = defaultCipherSuite,
                credentialType = CoreCredentialType.X509
            ).takeNewest()
            val basicCredential = if (x509Credential == null) {
                coreCrypto.findCredentials(
                    clientId = clientId,
                    cipherSuite = defaultCipherSuite,
                    credentialType = CoreCredentialType.BASIC
                ).takeNewest()
            } else {
                null
            }
            val activeCredential = x509Credential ?: basicCredential ?: coreCrypto.transaction { context ->
                val credential = Credential.basic(defaultCipherSuite, clientId)
                try {
                    context.addCredential(credential)
                } finally {
                    credential.close()
                }
            }

            return MLSClientImpl(coreCrypto, clientId, defaultCipherSuite, activeCredential, onClose)
        }
    }
}

private inline fun <T : Disposable, R> T.useNative(block: (T) -> R): R = try {
    block(this)
} finally {
    destroy()
}

private inline fun <S, T : Disposable, R> List<S>.useNativeMapped(
    transform: (S) -> T,
    block: (List<T>) -> R
): R {
    val nativeValues = mutableListOf<T>()
    return try {
        forEach { nativeValues += transform(it) }
        block(nativeValues)
    } finally {
        nativeValues.forEach { it.destroy() }
    }
}

private fun List<ClientId>.mapToCryptographyAndClose(): List<CryptoQualifiedClientId> = try {
    map { it.toCryptography() }
} finally {
    forEach { it.destroy() }
}

private fun List<com.wire.crypto.WireIdentity>.toCryptographyAndClose(): List<WireIdentity> = try {
    mapNotNull { it.toCryptography() }
} finally {
    forEach { it.destroy() }
}

private fun Map<Uuid, List<com.wire.crypto.WireIdentity>>.toCryptographyAndClose(): Map<String, List<WireIdentity>> = try {
    entries.associate { (userId, identities) ->
        userId.toString() to identities.mapNotNull { it.toCryptography() }
    }
} finally {
    forEach { (userId, identities) ->
        userId.close()
        identities.forEach { it.destroy() }
    }
}

private fun List<CredentialRef>.takeNewest(): CredentialRef? = maxByOrNull { it.earliestValidity() }

private fun List<KeyPackageRef>.countMatching(credentialRef: CredentialRef): Int = try {
    count { keyPackageRef ->
        keyPackageRef.isValid() &&
                keyPackageRef.cipherSuite() == credentialRef.cipherSuite() &&
                keyPackageRef.credentialType() == credentialRef.type() &&
                keyPackageRef.signatureScheme() == credentialRef.signatureScheme()
    }
} finally {
    forEach { it.close() }
}

private fun CredentialRef.matches(other: CredentialRef): Boolean =
    cipherSuite() == other.cipherSuite() &&
            type() == other.type() &&
            publicKeyHash().contentEquals(other.publicKeyHash())

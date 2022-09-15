package com.wire.kalium.persistence.daokaliumdb

import com.wire.kalium.persistence.MLSPublicKey
import com.wire.kalium.persistence.MLSPublicKeyQueries
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.model.Key
import com.wire.kalium.persistence.model.MLSPublicKeyEntity

interface MLSPublicKeyMapper {
    fun toModel(publicKey: MLSPublicKey): MLSPublicKeyEntity
}

internal class MLSPublicKeyMapperImpl : MLSPublicKeyMapper {
    override fun toModel(publicKey: MLSPublicKey) = with(publicKey) {
        MLSPublicKeyEntity(cipher_suite, Key(key), keyType = key_type)
    }
}

interface MLSPublicKeysDAO {
    fun insert(publicKey: MLSPublicKeyEntity, serverConfigId: String)
    fun getKeys(userId: UserIDEntity): List<MLSPublicKeyEntity>
}

class MLSPublicKeysDAOImpl(private val queries: MLSPublicKeyQueries, private val mapper: MLSPublicKeyMapper = MLSPublicKeyMapperImpl()) :
    MLSPublicKeysDAO {
    override fun insert(publicKey: MLSPublicKeyEntity, serverConfigId: String) =
        with(publicKey) {
            queries.insert(cipherSuite, key.value, keyType, serverConfigId)
        }

    override fun getKeys(userId: UserIDEntity) =
        queries.getByUserId(userId).executeAsList().map(mapper::toModel)

}


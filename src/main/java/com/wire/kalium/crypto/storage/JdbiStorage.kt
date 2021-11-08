package com.wire.kalium.crypto.storage

import org.jdbi.v3.core.Jdbi
import com.wire.bots.cryptobox.IStorage
import com.wire.bots.cryptobox.IRecord
import com.wire.bots.cryptobox.PreKey

class JdbiStorage(jdbi: Jdbi?) : IStorage {
    private val sessionsDAO: SessionsDAO?
    private val identitiesDAO: IdentitiesDAO?
    private val prekeysDAO: PrekeysDAO?
    override fun fetchSession(id: String?, sid: String?): IRecord? {
        val session = sessionsDAO.get(id, sid)
        return Record(id, sid, session?.data) //todo implement commit on UPDATE
    }

    override fun fetchIdentity(id: String?): ByteArray? {
        val identity = identitiesDAO.get(id)
        return identity?.data
    }

    override fun insertIdentity(id: String?, data: ByteArray?) {
        identitiesDAO.insert(id, data)
    }

    override fun fetchPrekeys(id: String?): Array<PreKey?>? {
        val preKeys = prekeysDAO.get(id)
        if (preKeys.isEmpty()) return null
        val ret = arrayOfNulls<PreKey?>(preKeys.size)
        return preKeys.toArray(ret)
    }

    override fun insertPrekey(id: String?, kid: Int, data: ByteArray?) {
        prekeysDAO.insert(id, kid, data)
    }

    override fun purge(id: String?) {
        sessionsDAO.delete(id)
        identitiesDAO.delete(id)
        prekeysDAO.delete(id)
    }

    internal inner class Record(private val id: String?, private val sid: String?, private val data: ByteArray?) : IRecord {
        override fun getData(): ByteArray? {
            return data
        }

        override fun persist(update: ByteArray?) {
            if (update != null) {
                sessionsDAO.insert(id, sid, update)
                //todo implement commits
            }
        }
    }

    init {
        sessionsDAO = jdbi.onDemand(SessionsDAO::class.java)
        identitiesDAO = jdbi.onDemand(IdentitiesDAO::class.java)
        prekeysDAO = jdbi.onDemand(PrekeysDAO::class.java)
    }
}

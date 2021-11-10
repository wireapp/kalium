package com.wire.helium.helpers

import com.wire.bots.cryptobox.IRecord
import com.wire.bots.cryptobox.IStorage
import com.wire.bots.cryptobox.PreKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MemStorage : IStorage {
    private val sessions = ConcurrentHashMap<String, Record?>()
    private val identities = ConcurrentHashMap<String, ByteArray>()
    private val prekeys = ConcurrentHashMap<String, ArrayList<PreKey>>()
    override fun fetchSession(id: String, sid: String): IRecord {
        val key = key(id, sid)
        var record: Record? = sessions.computeIfAbsent(key) { k: String? -> null }
                ?: return Record(key, null)
        var i = 0
        while (i < 1000 && record!!.locked) {
            sleep(1)
            record = sessions[key]
            i++
        }
        record!!.locked = true
        //sessions.put(key, record);
        return Record(key, record.data)
    }

    override fun fetchIdentity(id: String): ByteArray {
        return identities[id]!!
    }

    override fun insertIdentity(id: String, data: ByteArray) {
        identities[id] = data
    }

    override fun fetchPrekeys(id: String): Array<PreKey> {
        val ret = prekeys[id]
        return ret?.toTypedArray()
    }

    override fun insertPrekey(id: String, kid: Int, data: ByteArray) {
        val preKey = PreKey(kid, data)
        val list = prekeys.computeIfAbsent(id) { k: String? -> ArrayList() }
        list.add(preKey)
    }

    override fun purge(id: String) {
        sessions.remove(id)
        prekeys.remove(id)
        identities.remove(id)
    }

    private fun sleep(millis: Int) {
        try {
            Thread.sleep(millis.toLong())
        } catch (ignored: InterruptedException) {
        }
    }

    private fun key(id: String, sid: String): String {
        return String.format("%s-%s", id, sid)
    }

    private inner class Record  //this.key = key;
    internal constructor(key: String?, //private final String key;
                         private var data: ByteArray) : IRecord {
        var locked = false
        override fun getData(): ByteArray {
            return data
        }

        override fun persist(data: ByteArray) {
            this.data = data
            //sessions.put(key, this);
        }
    }
}
@file:JsModule("@wireapp/store-engine")
@file:JsNonModule
@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS")
package com.wire.kalium.cryptography.externals

import kotlin.js.*
import org.khronos.webgl.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import org.w3c.dom.parsing.*
import org.w3c.dom.svg.*
import org.w3c.dom.url.*
import org.w3c.fetch.*
import org.w3c.files.*
import org.w3c.notifications.*
import org.w3c.performance.*
import org.w3c.workers.*
import org.w3c.xhr.*

external open class MemoryEngine : CRUDEngine {
    override var storeName: String
    open var stores: Any
    open var autoIncrementedPrimaryKey: Any
    override fun <EntityType, PrimaryKey> create(tableName: String, primaryKey: PrimaryKey, entity: EntityType): Promise<PrimaryKey>
    override fun clearTables(): Promise<Unit>
    override fun <PrimaryKey> delete(tableName: String, primaryKey: PrimaryKey): Promise<PrimaryKey>
    override fun deleteAll(tableName: String): Promise<Boolean>
    open fun init(storeName: String): Promise<Any>
    open fun <ObjectType> initWithObject(storeName: String, obj: ObjectType): Promise<Any>
    open var assignDb: Any
    override fun isSupported(): Promise<Unit>
    override fun purge(): Promise<Unit>
    override fun <EntityType, PrimaryKey> read(tableName: String, primaryKey: PrimaryKey): Promise<EntityType>
    override fun <T> readAll(tableName: String): Promise<Array<T>>
    override fun readAllPrimaryKeys(tableName: String): Promise<Array<String>>
    override fun <PrimaryKey, ChangesType> update(tableName: String, primaryKey: PrimaryKey, changes: ChangesType): Promise<PrimaryKey>
    override fun <PrimaryKey, ChangesType> updateOrCreate(tableName: String, primaryKey: PrimaryKey, changes: ChangesType): Promise<PrimaryKey>
    open var prepareTable: Any
}

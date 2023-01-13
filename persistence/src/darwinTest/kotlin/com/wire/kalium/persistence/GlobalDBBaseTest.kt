package com.wire.kalium.persistence

import co.touchlab.sqliter.DatabaseFileContext.deleteDatabase
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.util.FileNameUtil
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual abstract class GlobalDBBaseTest {

    private var storePath = NSFileManager.defaultManager.URLForDirectory(NSCachesDirectory, NSUserDomainMask, null, true, null)!!.path!!

    actual fun deleteDatabase() {
        deleteDatabase(FileNameUtil.globalDBName(), storePath)
    }

    actual fun createDatabase(): GlobalDatabaseProvider {
        return GlobalDatabaseProvider(storePath)
    }
}

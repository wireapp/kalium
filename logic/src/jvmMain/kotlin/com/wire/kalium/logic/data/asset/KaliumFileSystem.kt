package com.wire.kalium.logic.data.asset

import okio.*

actual class KaliumFileSystem : FileSystem() {
    override fun appendingSink(file: Path, mustExist: Boolean): Sink = SYSTEM.appendingSink(file, mustExist)

    override fun atomicMove(source: Path, target: Path) = SYSTEM.atomicMove(source, target)

    override fun canonicalize(path: Path): Path = SYSTEM.canonicalize(path)

    override fun createDirectory(dir: Path, mustCreate: Boolean) = SYSTEM.createDirectory(dir, mustCreate)

    override fun createSymlink(source: Path, target: Path) = SYSTEM.createSymlink(source, target)

    override fun delete(path: Path, mustExist: Boolean) = SYSTEM.delete(path, mustExist)

    override fun list(dir: Path): List<Path> = SYSTEM.list(dir)

    override fun listOrNull(dir: Path): List<Path>? = SYSTEM.listOrNull(dir)

    override fun metadataOrNull(path: Path): FileMetadata? = SYSTEM.metadataOrNull(path)

    override fun sink(file: Path, mustCreate: Boolean): Sink = SYSTEM.sink(file, mustCreate)

    override fun source(file: Path): Source = SYSTEM.source(file)

    override fun openReadOnly(file: Path): FileHandle = SYSTEM.openReadOnly(file)

    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle =
        SYSTEM.openReadWrite(file, mustCreate, mustExist)
}

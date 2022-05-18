package com.wire.kalium.logic.data.message

data class AssetContent(
    val sizeInBytes: Long,
    val name: String? = null,
    val mimeType: String,
    val metadata: AssetMetadata? = null,
    val remoteData: RemoteData,
    val downloadStatus: Message.DownloadStatus
) {
    sealed class AssetMetadata {
        data class Image(val width: Int, val height: Int) : AssetMetadata()
        data class Video(val width: Int?, val height: Int?, val durationMs: Long?) : AssetMetadata()
        data class Audio(val durationMs: Long?, val normalizedLoudness: ByteArray?) : AssetMetadata() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false

                other as Audio

                if (durationMs != other.durationMs) return false
                if (normalizedLoudness != null) {
                    if (other.normalizedLoudness == null) return false
                    if (!normalizedLoudness.contentEquals(other.normalizedLoudness)) return false
                } else if (other.normalizedLoudness != null) return false

                return true
            }

            override fun hashCode(): Int {
                var result = durationMs?.hashCode() ?: 0
                result = 31 * result + (normalizedLoudness?.contentHashCode() ?: 0)
                return result
            }
        }
    }

    data class RemoteData(
        val otrKey: ByteArray,
        val sha256: ByteArray,
        val assetId: String,
        val assetToken: String?,
        val assetDomain: String?,
        val encryptionAlgorithm: EncryptionAlgorithm?
    ) {
        enum class EncryptionAlgorithm { AES_CBC, AES_GCM }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as RemoteData

            if (!otrKey.contentEquals(other.otrKey)) return false
            if (!sha256.contentEquals(other.sha256)) return false
            if (assetId != other.assetId) return false
            if (assetToken != other.assetToken) return false
            if (assetDomain != other.assetDomain) return false
            if (encryptionAlgorithm != other.encryptionAlgorithm) return false

            return true
        }

        override fun hashCode(): Int {
            var result = otrKey.contentHashCode()
            result = 31 * result + sha256.contentHashCode()
            result = 31 * result + (assetId?.hashCode() ?: 0)
            result = 31 * result + (assetToken?.hashCode() ?: 0)
            result = 31 * result + (assetDomain?.hashCode() ?: 0)
            result = 31 * result + (encryptionAlgorithm?.hashCode() ?: 0)
            return result
        }
    }
}

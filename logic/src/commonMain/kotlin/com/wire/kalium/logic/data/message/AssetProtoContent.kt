package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.LegalHoldStatus

data class AssetProtoContent(
    val original: Original,
    val preview: Preview? = null,
    val uploadStatus: UploadStatus? = null,
    val expectsReadConfirmation: Boolean? = false,
    val legalHoldStatus: LegalHoldStatus? = null
) {
    data class Original(
        val mimeType: String,
        val size: Int,
        val name: String? = null,
        val metadata: AssetMetadata? = null,
        val source: String? = null,
        val caption: String? = null
    )

    data class Preview(
        val mimeType: String,
        val size: String,
        val remoteData: RemoteData
    )

    sealed class AssetMetadata {
        data class Image(val width: Int, val height: Int, val tag: String?) : AssetMetadata()
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
        val assetId: String?,
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

    sealed class UploadStatus {
        data class Pending(val reason: NotUploaded): UploadStatus()
        data class Uploaded(val remoteData: RemoteData): UploadStatus()
    }

    enum class NotUploaded {
        CANCELED, FAILED
    }
}

package com.debbly.server.storage

import com.debbly.server.config.S3DefaultProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.util.UUID

@Service
class S3Service(
    @Qualifier("s3DefaultClient") private val s3Client: S3Client,
    @Qualifier("s3DefaultPresigner") private val s3Presigner: S3Presigner,
    private val s3ConfigProperties: S3DefaultProperties
) {

    data class PresignedUpload(
        val key: String,
        val uploadUrl: String,
        val publicUrl: String,
        val expiresInSeconds: Long
    )

    fun generateAvatarUpload(userId: String, contentType: String): PresignedUpload {
        val key = "$userId/avatars/${UUID.randomUUID()}.${extensionFromContentType(contentType)}"
        return generatePresignedUpload(
            key = key,
            contentType = contentType,
            expires = Duration.ofMinutes(10)
        )
    }

    fun generateEventBannerUpload(userId: String, contentType: String): PresignedUpload {
        val key = "$userId/events/banners/${UUID.randomUUID()}.${extensionFromContentType(contentType)}"
        return generatePresignedUpload(
            key = key,
            contentType = contentType,
            expires = Duration.ofMinutes(10)
        )
    }

    fun buildUsersPublicUrl(key: String): String = buildPublicUrl(s3ConfigProperties.bucket.users, key)

    fun isUsersPublicUrl(url: String): Boolean =
        url.startsWith("${s3ConfigProperties.endpoint}/${s3ConfigProperties.bucket.users}/")

    fun isAvatarKeyOwnedByUser(userId: String, key: String): Boolean = key.startsWith("$userId/avatars/")

    fun isEventBannerKeyOwnedByUser(userId: String, key: String): Boolean = key.startsWith("$userId/events/banners/")

    fun deleteAvatar(avatarUrl: String) {
        val key = extractKeyFromUrl(avatarUrl, s3ConfigProperties.bucket.users)
        if (key != null) {
            val deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(s3ConfigProperties.bucket.users)
                .key(key)
                .build()

            s3Client.deleteObject(deleteObjectRequest)
        }
    }

    private fun generatePresignedUpload(key: String, contentType: String, expires: Duration): PresignedUpload {
        val putRequest = PutObjectRequest.builder()
            .bucket(s3ConfigProperties.bucket.users)
            .key(key)
            .contentType(contentType)
            .build()

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(expires)
            .putObjectRequest(putRequest)
            .build()

        val presigned = s3Presigner.presignPutObject(presignRequest)

        return PresignedUpload(
            key = key,
            uploadUrl = presigned.url().toString(),
            publicUrl = buildUsersPublicUrl(key),
            expiresInSeconds = expires.seconds
        )
    }

    private fun buildPublicUrl(bucket: String, key: String): String {
        return "${s3ConfigProperties.endpoint}/$bucket/$key"
    }

    private fun extractKeyFromUrl(url: String, bucket: String): String? {
        val bucketPrefix = "${s3ConfigProperties.endpoint}/$bucket/"
        return if (url.startsWith(bucketPrefix)) {
            url.removePrefix(bucketPrefix)
        } else {
            null
        }
    }

    private fun extensionFromContentType(contentType: String): String {
        return when (contentType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "bin"
        }
    }
}

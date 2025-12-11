package com.debbly.server.storage

import com.debbly.server.config.S3DefaultProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.*

@Service
class S3Service(
    @Qualifier("s3DefaultClient") private val s3Client: S3Client,
    private val s3ConfigProperties: S3DefaultProperties
) {

    fun uploadAvatar(file: MultipartFile, userId: String): String {
        val fileExtension = file.originalFilename?.substringAfterLast('.', "jpg")
        val fileName = "${userId}/avatars/${UUID.randomUUID()}.$fileExtension"

        val putObjectRequest = PutObjectRequest.builder()
            .bucket(s3ConfigProperties.bucket.users)
            .key(fileName)
            .contentType(file.contentType ?: "image/jpeg")
            .build()

        s3Client.putObject(
            putObjectRequest,
            RequestBody.fromInputStream(file.inputStream, file.size)
        )

        return buildPublicUrl(s3ConfigProperties.bucket.users, fileName)
    }

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
}

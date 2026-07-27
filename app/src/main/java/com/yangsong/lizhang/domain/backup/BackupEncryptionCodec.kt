package com.yangsong.lizhang.domain.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupPasswordRequiredException : Exception("该备份需要密码")

class InvalidBackupPasswordException(
    cause: Throwable? = null,
) : Exception("密码错误或加密备份已损坏", cause)

/**
 * 加密备份外层封装。
 *
 * 原始备份格式保持不变，使用 PBKDF2-HMAC-SHA256 派生密钥，并通过 AES-256-GCM
 * 同时保护内容机密性与完整性。密码只参与当前操作，不写入文件或本地偏好。
 */
object BackupEncryptionCodec {
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_DOCUMENT_BYTES = BackupArchiveCodec.MAX_FILE_BYTES + 128

    private const val envelopeVersion = 1
    private const val keyBits = 256
    private const val iterations = 210_000
    private const val saltSize = 16
    private const val ivSize = 12
    private const val tagBits = 128
    private val magic = "LIZHANG-ENCRYPTED\n".toByteArray(Charsets.US_ASCII)
    private val secureRandom = SecureRandom()

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size >= magic.size && bytes.copyOfRange(0, magic.size).contentEquals(magic)

    fun encrypt(plainBytes: ByteArray, password: String): ByteArray {
        require(password.length >= MIN_PASSWORD_LENGTH) {
            "备份密码至少需要 $MIN_PASSWORD_LENGTH 个字符"
        }
        require(plainBytes.size <= BackupArchiveCodec.MAX_FILE_BYTES) { "备份文件超过大小限制" }

        val salt = ByteArray(saltSize).also(secureRandom::nextBytes)
        val iv = ByteArray(ivSize).also(secureRandom::nextBytes)
        val encryptedSize = plainBytes.size + tagBits / 8
        val header = createHeader(salt, iv, encryptedSize)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = deriveKey(password, salt)
        val encrypted = try {
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(tagBits, iv))
            cipher.updateAAD(magic + header)
            cipher.doFinal(plainBytes)
        } finally {
            key.encoded?.fill(0)
        }

        return magic + header + encrypted
    }

    @Throws(InvalidBackupPasswordException::class)
    fun decrypt(encryptedBytes: ByteArray, password: String): ByteArray {
        if (!isEncrypted(encryptedBytes)) throw InvalidBackupPasswordException()
        if (encryptedBytes.size > MAX_DOCUMENT_BYTES) throw InvalidBackupPasswordException()
        if (password.isEmpty()) throw BackupPasswordRequiredException()

        return try {
            DataInputStream(ByteArrayInputStream(encryptedBytes, magic.size, encryptedBytes.size - magic.size)).use { input ->
                val version = input.readInt()
                if (version != envelopeVersion) throw InvalidBackupPasswordException()
                val actualIterations = input.readInt()
                if (actualIterations != iterations) throw InvalidBackupPasswordException()
                val salt = input.readSizedBytes(saltSize)
                val iv = input.readSizedBytes(ivSize)
                val encryptedSize = input.readInt()
                if (encryptedSize !in tagBits / 8..BackupArchiveCodec.MAX_FILE_BYTES + tagBits / 8) {
                    throw InvalidBackupPasswordException()
                }
                val encrypted = ByteArray(encryptedSize).also(input::readFully)
                if (input.read() != -1) throw InvalidBackupPasswordException()

                val header = createHeader(salt, iv, encryptedSize)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val key = deriveKey(password, salt)
                try {
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(tagBits, iv))
                    cipher.updateAAD(magic + header)
                    cipher.doFinal(encrypted)
                } finally {
                    key.encoded?.fill(0)
                }
            }
        } catch (error: BackupPasswordRequiredException) {
            throw error
        } catch (error: InvalidBackupPasswordException) {
            throw error
        } catch (error: AEADBadTagException) {
            throw InvalidBackupPasswordException(error)
        } catch (error: Exception) {
            throw InvalidBackupPasswordException(error)
        }
    }

    private fun createHeader(salt: ByteArray, iv: ByteArray, encryptedSize: Int): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(envelopeVersion)
                output.writeInt(iterations)
                output.writeInt(salt.size)
                output.write(salt)
                output.writeInt(iv.size)
                output.write(iv)
                output.writeInt(encryptedSize)
            }
            buffer.toByteArray()
        }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val passwordChars = password.toCharArray()
        val keySpec = PBEKeySpec(passwordChars, salt, iterations, keyBits)
        passwordChars.fill('\u0000')
        return try {
            val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(keySpec)
                .encoded
            try {
                SecretKeySpec(keyBytes, "AES")
            } finally {
                keyBytes.fill(0)
            }
        } finally {
            keySpec.clearPassword()
        }
    }

    private fun DataInputStream.readSizedBytes(expectedSize: Int): ByteArray {
        val actualSize = readInt()
        if (actualSize != expectedSize) throw InvalidBackupPasswordException()
        return ByteArray(actualSize).also(::readFully)
    }
}

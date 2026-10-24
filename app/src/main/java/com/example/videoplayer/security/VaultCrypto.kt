package com.example.videoplayer.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val KEY_LENGTH = 256
private const val ITERATIONS = 120_000
private const val AES = "AES"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val TAG_LENGTH_BITS = 128

data class EncryptedVaultPayload(
    val salt: ByteArray,
    val iv: ByteArray,
    val cipherText: ByteArray
)

object VaultCrypto {
    fun generateSalt(): ByteArray = ByteArray(16).apply { SecureRandom().nextBytes(this) }

    fun generateIv(): ByteArray = ByteArray(12).apply { SecureRandom().nextBytes(this) }

    fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, AES)
    }

    fun encrypt(data: ByteArray, password: String): EncryptedVaultPayload {
        val salt = generateSalt()
        val iv = generateIv()
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val parameterSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec)
        val encrypted = cipher.doFinal(data)
        return EncryptedVaultPayload(salt, iv, encrypted)
    }

    fun decrypt(payload: EncryptedVaultPayload, password: String): ByteArray {
        val key = deriveKey(password, payload.salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val parameterSpec = GCMParameterSpec(TAG_LENGTH_BITS, payload.iv)
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec)
        return cipher.doFinal(payload.cipherText)
    }

    fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val key = deriveKey(password, salt)
        return MessageDigest.getInstance("SHA-256").digest(key.encoded)
    }

    fun passwordMatches(password: String, salt: ByteArray, expectedHash: ByteArray): Boolean {
        val actual = hashPassword(password, salt)
        return MessageDigest.isEqual(actual, expectedHash)
    }
}

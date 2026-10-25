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

    fun getEncryptCipher(password: String, salt: ByteArray, iv: ByteArray): Cipher {
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher
    }

    fun getDecryptCipher(password: String, salt: ByteArray, iv: ByteArray): Cipher {
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher
    }

    fun encrypt(data: ByteArray, password: String): EncryptedVaultPayload {
        val salt = generateSalt()
        val iv = generateIv()
        val cipher = getEncryptCipher(password, salt, iv)
        val encrypted = cipher.doFinal(data)
        return EncryptedVaultPayload(salt, iv, encrypted)
    }

    fun decrypt(payload: EncryptedVaultPayload, password: String): ByteArray {
        val cipher = getDecryptCipher(password, payload.salt, payload.iv)
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

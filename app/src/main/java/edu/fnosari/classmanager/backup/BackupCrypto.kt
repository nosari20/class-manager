package edu.fnosari.classmanager.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Optional password protection for backup files.
 * Layout: MAGIC(6) | salt(16) | iv(12) | AES-256-GCM ciphertext (tag appended by GCM).
 * Key: PBKDF2WithHmacSHA256, 200_000 iterations.
 */
object BackupCrypto {
    private val MAGIC = "CMENC1".toByteArray(Charsets.US_ASCII)
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val ITERATIONS = 200_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size + SALT_LEN + IV_LEN &&
            bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun encrypt(plain: ByteArray, password: String): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain)
        return MAGIC + salt + iv + ct
    }

    /** Returns null on wrong password or corrupted data. */
    fun decrypt(data: ByteArray, password: String): ByteArray? {
        if (!isEncrypted(data)) return null
        return try {
            val salt = data.copyOfRange(MAGIC.size, MAGIC.size + SALT_LEN)
            val iv = data.copyOfRange(MAGIC.size + SALT_LEN, MAGIC.size + SALT_LEN + IV_LEN)
            val ct = data.copyOfRange(MAGIC.size + SALT_LEN + IV_LEN, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(ct)
        } catch (e: Exception) {
            null
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return SecretKeySpec(key.encoded, "AES")
    }
}

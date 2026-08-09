package edu.fnosari.classmanager.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val payload = "SQLite format 3 fake backup content with accents éàü".toByteArray()

    @Test fun roundTrip() {
        val enc = BackupCrypto.encrypt(payload, "motdepasse")
        val dec = BackupCrypto.decrypt(enc, "motdepasse")
        assertArrayEquals(payload, dec)
    }

    @Test fun wrongPasswordReturnsNull() {
        val enc = BackupCrypto.encrypt(payload, "motdepasse")
        assertNull(BackupCrypto.decrypt(enc, "wrong"))
    }

    @Test fun isEncryptedDetection() {
        val enc = BackupCrypto.encrypt(payload, "x")
        assertTrue(BackupCrypto.isEncrypted(enc))
        assertFalse(BackupCrypto.isEncrypted(payload))
        assertFalse(BackupCrypto.isEncrypted(ByteArray(3)))
    }

    @Test fun tamperedCiphertextRejected() {
        val enc = BackupCrypto.encrypt(payload, "motdepasse")
        enc[enc.size - 5] = (enc[enc.size - 5] + 1).toByte()
        assertNull(BackupCrypto.decrypt(enc, "motdepasse"))
    }

    @Test fun differentSaltsPerEncryption() {
        val a = BackupCrypto.encrypt(payload, "x")
        val b = BackupCrypto.encrypt(payload, "x")
        assertFalse(a.contentEquals(b))
    }
}

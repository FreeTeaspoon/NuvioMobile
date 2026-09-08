package com.nuvio.app.features.backup

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

class BackupCryptoAndroidTest {
    @Test
    fun encryptedPayloadDecryptsWithSamePassphraseKey() {
        val salt = ByteArray(16) { it.toByte() }
        val iv = ByteArray(16) { (it + 16).toByte() }
        val keyMaterial = BackupCrypto.deriveKey(
            passphrase = "correct horse battery staple",
            salt = salt,
            iterations = 1_000,
            outputBytes = 64,
        )
        val encryptionKey = keyMaterial.copyOfRange(0, 32)
        val plaintext = "saved items and watch history".encodeToByteArray()

        val ciphertext = BackupCrypto.encryptAesCbcPkcs7(plaintext, encryptionKey, iv)
        val decrypted = BackupCrypto.decryptAesCbcPkcs7(ciphertext, encryptionKey, iv)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun differentPassphrasesProduceDifferentKeys() {
        val salt = ByteArray(16) { it.toByte() }

        val first = BackupCrypto.deriveKey("one", salt, 1_000, 64)
        val second = BackupCrypto.deriveKey("two", salt, 1_000, 64)

        assertFalse(first.contentEquals(second))
    }
}

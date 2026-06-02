package com.nuvio.app.features.backup

internal expect object BackupCrypto {
    fun randomBytes(size: Int): ByteArray
    fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int, outputBytes: Int): ByteArray
    fun encryptAesCbcPkcs7(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
    fun decryptAesCbcPkcs7(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
}


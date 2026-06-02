package com.nuvio.app.features.backup

internal actual object BackupCrypto {
    actual fun randomBytes(size: Int): ByteArray =
        unsupported()

    actual fun deriveKey(
        passphrase: String,
        salt: ByteArray,
        iterations: Int,
        outputBytes: Int,
    ): ByteArray =
        unsupported()

    actual fun encryptAesCbcPkcs7(
        plaintext: ByteArray,
        key: ByteArray,
        iv: ByteArray,
    ): ByteArray =
        unsupported()

    actual fun decryptAesCbcPkcs7(
        ciphertext: ByteArray,
        key: ByteArray,
        iv: ByteArray,
    ): ByteArray =
        unsupported()

    actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        unsupported()

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("Encrypted backups are not available on iOS yet.")
}


package app.still.data.usage

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable, authenticated file. The PIN and derived key are never persisted. */
internal object EncryptedDataExport {
    private val magic = "STILLDB1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 600_000
    const val MAX_BYTES = 512L * 1024 * 1024
    const val MIME_TYPE = "application/x-still-database"
    val IMPORT_MIME_TYPES = arrayOf(MIME_TYPE, "application/octet-stream")

    fun validateFile(name: String?, input: InputStream) {
        require(name?.endsWith(".stilldb", ignoreCase = true) == true) { "Select a .stilldb export created by Still" }
        val header = ByteArray(magic.size + 16 + 12 + 16)
        java.io.DataInputStream(input).readFully(header)
        require(header.copyOfRange(0, magic.size).contentEquals(magic)) { "Not a supported Still export" }
    }

    fun encrypt(input: InputStream, output: OutputStream, pin: CharArray) {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val header = magic + salt + nonce
        output.write(header)
        transform(input, output, cipher(Cipher.ENCRYPT_MODE, pin, salt, nonce, header))
    }

    fun decrypt(input: InputStream, output: OutputStream, pin: CharArray) {
        val header = ByteArray(magic.size + 16 + 12)
        java.io.DataInputStream(input).readFully(header)
        require(header.copyOfRange(0, magic.size).contentEquals(magic)) { "Not a supported Still export" }
        transform(input, output, cipher(Cipher.DECRYPT_MODE, pin,
            header.copyOfRange(8, 24), header.copyOfRange(24, 36), header))
    }

    private fun cipher(mode: Int, pin: CharArray, salt: ByteArray, nonce: ByteArray, header: ByteArray): Cipher {
        require(pin.size == 6 && pin.all { it in '0'..'9' }) { "Enter a six-digit PIN" }
        val spec = PBEKeySpec(pin, salt, ITERATIONS, 256)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(header)
            }
        } finally { key.fill(0) }
    }

    private fun transform(input: InputStream, output: OutputStream, cipher: Cipher) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            require(total <= MAX_BYTES) { "Export exceeds the 512 MB limit" }
            cipher.update(buffer, 0, count)?.let(output::write)
        }
        // Never accept plaintext before the authentication tag has been verified.
        output.write(cipher.doFinal())
        buffer.fill(0)
    }
}

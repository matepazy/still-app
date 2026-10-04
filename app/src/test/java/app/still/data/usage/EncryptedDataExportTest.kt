package app.still.data.usage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class EncryptedDataExportTest {
    private val original = ByteArray(150_000) { (it % 251).toByte() }

    private fun export(pin: String = "012345"): ByteArray = ByteArrayOutputStream().also {
        EncryptedDataExport.encrypt(ByteArrayInputStream(original), it, pin.toCharArray())
    }.toByteArray()

    private fun decrypt(bytes: ByteArray, pin: String = "012345"): ByteArray = ByteArrayOutputStream().also {
        EncryptedDataExport.decrypt(ByteArrayInputStream(bytes), it, pin.toCharArray())
    }.toByteArray()

    @Test fun roundTripAndFreshRandomness() {
        val first = export()
        assertArrayEquals(original, decrypt(first))
        assertFalse(first.contentEquals(export()))
        assertFalse(first.contentEquals(original))
    }

    @Test fun wrongPinIsRejected() {
        assertThrows(Exception::class.java) { decrypt(export(), "654321") }
    }

    @Test fun modifiedAndTruncatedFilesAreRejected() {
        val bytes = export()
        val altered = bytes.copyOf().also { it[50] = (it[50].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { decrypt(altered) }
        assertThrows(Exception::class.java) { decrypt(bytes.copyOf(bytes.size - 1)) }
        assertThrows(Exception::class.java) { decrypt(bytes + byteArrayOf(0)) }
        val changedHeader = bytes.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { decrypt(changedHeader) }
    }

    @Test fun onlySixAsciiDigitsAreAccepted() {
        listOf("12345", "1234567", "12a456", "１２３４５６").forEach { pin ->
            assertThrows(IllegalArgumentException::class.java) { export(pin) }
        }
    }

    @Test fun selectionRequiresStillExtensionAndHeader() {
        val valid = export()
        EncryptedDataExport.validateFile("history.stilldb", ByteArrayInputStream(valid))
        EncryptedDataExport.validateFile("history.STILLDB", ByteArrayInputStream(valid))
        listOf(null, "history.db", "history.stilldb.txt").forEach { name ->
            assertThrows(Exception::class.java) { EncryptedDataExport.validateFile(name, ByteArrayInputStream(valid)) }
        }
        assertThrows(Exception::class.java) { EncryptedDataExport.validateFile("fake.stilldb", ByteArrayInputStream(original)) }
        assertThrows(Exception::class.java) { EncryptedDataExport.validateFile("short.stilldb", ByteArrayInputStream(valid.copyOf(40))) }
    }
}

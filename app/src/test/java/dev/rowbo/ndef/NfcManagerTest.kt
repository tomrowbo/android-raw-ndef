package dev.rowbo.ndef

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NfcManagerTest {
    private val nfcManager = NfcManager()

    @Test
    fun parseNdefData_withValidTextRecord_returnsCorrectText() {
        // This is the raw data from your log, representing "test"
        val rawData = byteArrayOf(
            0x04, 0x35, 0x79, 0xC0.toByte(),  // Page 0
            0x52, 0x4A, 0x74, 0x80.toByte(),  // Page 1
            0xEC.toByte(), 0x48, 0x00, 0x00,  // Page 2
            0xE1.toByte(), 0x11, 0x12, 0x00,  // Page 3
            0x03, 0x0B, 0xD1.toByte(), 0x01,  // Page 4 - NDEF TLV start
            0x07, 0x54, 0x02, 0x65,  // Page 5
            0x6E, 0x74, 0x65, 0x73,  // Page 6
            0x74, 0xFE.toByte(), 0x00, 0x00   // Page 7 - Content + Terminator
        )

        val result = nfcManager.parseNdefData(rawData)
        assertEquals("test", result)
    }

    @Test
    fun createNdefTextRecord_withText_returnsCorrectBytes() {
        val expectedData = byteArrayOf(
            0x04, 0x35, 0x79, 0xC0.toByte(),  // Page 0
            0x52, 0x4A, 0x74, 0x80.toByte(),  // Page 1
            0xEC.toByte(), 0x48, 0x00, 0x00,  // Page 2
            0xE1.toByte(), 0x11, 0x12, 0x00,  // Page 3
            0x03, 0x0B, 0xD1.toByte(), 0x01,  // Page 4 - NDEF TLV start
            0x07, 0x54, 0x02, 0x65,  // Page 5
            0x6E, 0x74, 0x65, 0x73,  // Page 6
            0x74, 0xFE.toByte(), 0x00, 0x00   // Page 7 - Content + Terminator
        )

        val result = nfcManager.createNdefTextRecord("test")
        
        // Compare byte by byte for easier debugging
        assertEquals(expectedData.size, result.size
        )
        expectedData.forEachIndexed { index, byte ->
            assertEquals("Byte at position $index doesn't match", 
                byte, result[index])
        }
    }

    @Test
    fun createNdefTextRecord_roundTrip_maintainsOriginalText() {
        val originalText = "test"
        val ndefData = nfcManager.createNdefTextRecord(originalText)
        val parsedText = nfcManager.parseNdefData(ndefData)
        assertEquals(originalText, parsedText)
    }
} 
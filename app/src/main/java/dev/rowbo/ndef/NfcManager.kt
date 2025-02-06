package dev.rowbo.ndef

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.util.Log
import java.io.IOException

class NfcManager {
    fun readTag(tag: Tag): String {
        Log.d("NFC", "Available technologies: ${tag.techList.joinToString()}")
        
        val ultralight = MifareUltralight.get(tag) ?: throw Exception("Tag is not a Mifare Ultralight tag")
        
        return try {
            ultralight.connect()
            if (!ultralight.isConnected) {
                throw Exception("Failed to connect to tag")
            }
            
            // Read all pages
            val allData = mutableListOf<Byte>()
            for (i in 0..35 step 4) {
                try {
                    val pages = ultralight.readPages(i)
                    allData.addAll(pages.toList())
                } catch (e: Exception) {
                    Log.w("NFC", "Stopped reading at page $i: ${e.message}")
                    break
                }
            }
            ultralight.close()

            // Log raw page data
            Log.d("NFC", "Raw data:")
            for (i in allData.indices step 4) {
                if (i + 4 > allData.size) break
                val page = allData.subList(i, i + 4).toByteArray()
                val pageNum = i/4
                Log.d("NFC", "Page $pageNum: ${bytesToHex(page)}")
            }
            
            parseNdefData(allData.toByteArray())
            
        } catch (e: Exception) {
            Log.e("NFC", "Error reading tag", e)
            throw e
        }
    }

    fun parseNdefData(rawData: ByteArray): String {
        // Skip capability container and get NDEF data starting at page 4
        val ndefData = if (rawData.size > 16) {
            rawData.copyOfRange(16, rawData.size)
        } else {
            rawData
        }
        
        Log.d("NFC", "\nNDEF Structure:")
        
        var index = 0
        var content = ""
        
        while (index < ndefData.size) {
            val byte = ndefData[index].toInt() and 0xFF
            
            when (byte) {
                0x03 -> { // NDEF Message TLV
                    val length = ndefData[index + 1].toInt() and 0xFF
                    Log.d("NFC", "NDEF Message TLV found (length: $length)")
                    
                    // Move to the actual NDEF message content
                    index += 2  // Skip TLV header
                    val ndefMessage = ndefData.copyOfRange(index, index + length)
                    
                    // Parse NDEF record
                    var recordIndex = 0
                    while (recordIndex < ndefMessage.size) {
                        val header = ndefMessage[recordIndex].toInt() and 0xFF
                        val mb = (header and 0x80) != 0 // Message Begin
                        val me = (header and 0x40) != 0 // Message End
                        val cf = (header and 0x20) != 0 // Chunk Flag
                        val sr = (header and 0x10) != 0 // Short Record
                        val il = (header and 0x08) != 0 // ID Length present
                        val tnf = header and 0x07 // Type Name Format
                        
                        Log.d("NFC", "Record Header: 0x${header.toString(16)}")
                        Log.d("NFC", "MB: $mb, ME: $me, CF: $cf, SR: $sr, IL: $il, TNF: $tnf")
                        
                        recordIndex++
                        val typeLength = ndefMessage[recordIndex++].toInt() and 0xFF
                        val payloadLength = if (sr) {
                            ndefMessage[recordIndex++].toInt() and 0xFF
                        } else {
                            val pl = ByteArray(4)
                            System.arraycopy(ndefMessage, recordIndex, pl, 0, 4)
                            recordIndex += 4
                            (pl[0].toInt() and 0xFF shl 24) or
                            (pl[1].toInt() and 0xFF shl 16) or
                            (pl[2].toInt() and 0xFF shl 8) or
                            (pl[3].toInt() and 0xFF)
                        }
                        
                        val idLength = if (il) ndefMessage[recordIndex++].toInt() and 0xFF else 0
                        
                        Log.d("NFC", "Type Length: $typeLength")
                        Log.d("NFC", "Payload Length: $payloadLength")
                        if (il) Log.d("NFC", "ID Length: $idLength")
                        
                        if (typeLength > 0) {
                            val type = ndefMessage.copyOfRange(recordIndex, recordIndex + typeLength)
                            Log.d("NFC", "Type: ${String(type)} (${bytesToHex(type)})")
                            recordIndex += typeLength
                        }
                        
                        if (idLength > 0) {
                            val id = ndefMessage.copyOfRange(recordIndex, recordIndex + idLength)
                            Log.d("NFC", "ID: ${bytesToHex(id)}")
                            recordIndex += idLength
                        }
                        
                        if (payloadLength > 0) {
                            val payload = ndefMessage.copyOfRange(recordIndex, recordIndex + payloadLength)
                            
                            if (tnf == 1 && typeLength == 1 && String(ndefMessage.copyOfRange(recordIndex - typeLength, recordIndex)) == "T") {
                                val statusByte = payload[0].toInt()
                                val languageCodeLength = statusByte and 0x3F
                                content = String(payload, 1 + languageCodeLength, payload.size - 1 - languageCodeLength)
                            }
                            recordIndex += payloadLength
                        }
                        
                        if (me) break
                    }
                    break
                }
                0xFE -> {
                    Log.d("NFC", "Terminator TLV found")
                    break
                }
                else -> {
                    if (index + 1 < ndefData.size) {
                        val length = ndefData[index + 1].toInt() and 0xFF
                        Log.d("NFC", "Skipping TLV type 0x${byte.toString(16)} (length: $length)")
                        index += 2 + length
                    } else {
                        index++
                    }
                }
            }
        }
        if (content.isEmpty()){
            throw Exception("No NDEF message found")
        }
        return content
    }
    
    private fun Byte.toBinaryString(): String {
        return String.format("%8s", Integer.toBinaryString(this.toInt() and 0xFF))
            .replace(' ', '0')
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 3)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 3] = HEX_CHARS[v ushr 4]
            hexChars[i * 3 + 1] = HEX_CHARS[v and 0x0F]
            hexChars[i * 3 + 2] = ' '
        }
        return String(hexChars)
    }

    fun createNdefTextRecord(text: String): ByteArray {
        // Create capability container (CC) - first 4 pages
        val cc = byteArrayOf(
            0x04, 0x35, 0x79, 0xC0.toByte(),  // Page 0
            0x52, 0x4A, 0x74, 0x80.toByte(),  // Page 1
            0xEC.toByte(), 0x48, 0x00, 0x00,  // Page 2
            0xE1.toByte(), 0x11, 0x12, 0x00   // Page 3
        )

        // Create NDEF Message
        val languageCode = "en"
        val languageCodeLength = languageCode.length
        val textBytes = text.toByteArray()
        val payloadLength = 1 + languageCodeLength + textBytes.size // status byte + language code + text

        val ndefMessage = byteArrayOf(
            0xD1.toByte(),  // TNF=1 (Well Known) + MB=1 + ME=1 + SR=1
            0x01,           // Type Length = 1 (Text record type "T")
            payloadLength.toByte(),  // Payload Length
            0x54,           // Type: "T"
            (languageCodeLength and 0x3F).toByte(),  // Status byte (no UTF-16, language code length)
            *languageCode.toByteArray(),  // Language code
            *textBytes      // The actual text
        )

        // Create TLV structure
        val tlv = byteArrayOf(
            0x03,  // NDEF Message TLV tag
            ndefMessage.size.toByte(),  // Length
            *ndefMessage,  // Value (NDEF Message)
            0xFE.toByte(),  // Terminator TLV
            0x00, 0x00  // Padding to complete the page
        )

        // Combine everything
        return cc + tlv
    }

    fun writeTag(tag: Tag, text: String) {
        val ultralight = MifareUltralight.get(tag) ?: throw Exception("Tag is not a Mifare Ultralight tag")
        
        try {
            ultralight.connect()
            if (!ultralight.isConnected) {
                throw Exception("Failed to connect to tag")
            }

            // Read capability container to check if tag is writable
            val cc = ultralight.readPages(3)  // Read page 3 which contains write access
            if ((cc[3].toInt() and 0xFF) != 0x00) {
                throw Exception("Tag appears to be write protected")
            }

            // Create NDEF message data
            val data = createNdefTextRecord(text)
            
            // Write the data page by page, starting from page 4
            // Pages 0-3 are reserved for capability container
            for (i in 16 until data.size step 4) {  // Start at byte 16 (page 4)
                val page = data.slice(i until minOf(i + 4, data.size)).toByteArray()
                // Pad the last page with zeros if needed
                val paddedPage = if (page.size < 4) {
                    page + ByteArray(4 - page.size)
                } else {
                    page
                }
                val pageNumber = i / 4
                try {
                    ultralight.writePage(pageNumber, paddedPage)
                    Log.d("NFC", "Wrote page $pageNumber: ${bytesToHex(paddedPage)}")
                } catch (e: IOException) {
                    throw Exception("Failed to write page $pageNumber. Tag may be protected or damaged.")
                }
            }
            
            ultralight.close()
        } catch (e: Exception) {
            ultralight.close()
            Log.e("NFC", "Error writing to tag", e)
            throw e
        }
    }

    companion object {
        private val HEX_CHARS = "0123456789ABCDEF".toCharArray()
        
        private val URI_PREFIXES = arrayOf(
            "", // 0x00
            "http://www.", // 0x01
            "https://www.", // 0x02
            "http://", // 0x03
            "https://", // 0x04
            "tel:", // 0x05
            "mailto:", // 0x06
            "ftp://anonymous:anonymous@", // 0x07
            "ftp://ftp.", // 0x08
            "ftps://", // 0x09
            "sftp://", // 0x0A
            "smb://", // 0x0B
            "nfs://", // 0x0C
            "ftp://", // 0x0D
            "dav://", // 0x0E
            "news:", // 0x0F
            "telnet://", // 0x10
            "imap:", // 0x11
            "rtsp://", // 0x12
            "urn:", // 0x13
            "pop:", // 0x14
            "sip:", // 0x15
            "sips:", // 0x16
            "tftp:", // 0x17
            "btspp://", // 0x18
            "btl2cap://", // 0x19
            "btgoep://", // 0x1A
            "tcpobex://", // 0x1B
            "irdaobex://", // 0x1C
            "file://", // 0x1D
            "urn:epc:id:", // 0x1E
            "urn:epc:tag:", // 0x1F
            "urn:epc:pat:", // 0x20
            "urn:epc:raw:", // 0x21
            "urn:epc:", // 0x22
            "urn:nfc:" // 0x23
        )
    }
} 

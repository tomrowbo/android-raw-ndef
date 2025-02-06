package dev.rowbo.ndef

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.util.Log

class NfcManager {
    fun readTag(tag: Tag): String {
        Log.d("NFC", "Available technologies: ${tag.techList.joinToString()}")
        
        // Try NDEF first
        Ndef.get(tag)?.use { ndef ->
            try {
                ndef.connect()
                val ndefMessage = ndef.cachedNdefMessage ?: ndef.ndefMessage
                if (ndefMessage != null) {
                    return parseNdefMessage(ndefMessage)
                }
            } catch (e: Exception) {
                Log.e("NFC", "Error reading NDEF message", e)
            }
        }
        
        // Fallback to raw reading if NDEF fails
        val ultralight = MifareUltralight.get(tag) ?: return "Not a supported tag"
        
        return try {
            ultralight.connect()
            if (!ultralight.isConnected) {
                return "Failed to connect to tag"
            }
            
            val allPages = ultralight.readPages(0)
            ultralight.close()

            // Show raw data for debugging
            val result = StringBuilder()
            result.append("Raw data:\n")
            for (i in allPages.indices step 4) {
                val page = allPages.copyOfRange(i, minOf(i + 4, allPages.size))
                result.append("Page ${i/4}: ${bytesToHex(page)}\n")
            }
            result.toString()
            
        } catch (e: Exception) {
            Log.e("NFC", "Error reading tag", e)
            "Error reading tag: ${e.message}"
        }
    }
    
    private fun parseNdefMessage(message: NdefMessage): String {
        val result = StringBuilder()
        
        message.records.forEachIndexed { index, record ->
            result.append("Record $index:\n")
            
            val tnf = record.tnf
            val type = String(record.type)
            val payload = record.payload
            
            when {
                // Text record
                type == "T" -> {
                    val languageCodeLength = payload[0].toInt() and 0x3F
                    val languageCode = String(payload, 1, languageCodeLength)
                    val text = String(payload, languageCodeLength + 1, 
                                    payload.size - languageCodeLength - 1)
                    result.append("  Type: Text\n")
                    result.append("  Language: $languageCode\n")
                    result.append("  Content: $text\n")
                }
                
                // URI record
                type == "U" -> {
                    val prefix = URI_PREFIXES[payload[0].toInt()]
                    val uri = prefix + String(payload, 1, payload.size - 1)
                    result.append("  Type: URI\n")
                    result.append("  Content: $uri\n")
                }
                
                // Other types
                else -> {
                    result.append("  TNF: $tnf\n")
                    result.append("  Type: $type\n")
                    result.append("  Payload: ${bytesToHex(payload)}\n")
                }
            }
            result.append("\n")
        }
        
        return result.toString()
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
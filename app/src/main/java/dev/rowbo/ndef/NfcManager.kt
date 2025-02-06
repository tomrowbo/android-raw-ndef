package dev.rowbo.ndef

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.util.Log

class NfcManager {
    fun readTag(tag: Tag): String {
        Log.d("NFC", "Available technologies: ${tag.techList.joinToString()}")
        
        val ultralight = MifareUltralight.get(tag) ?: return "Not a supported tag"
        
        return try {
            ultralight.connect()
            if (!ultralight.isConnected) {
                return "Failed to connect to tag"
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

            val rawDataLog = StringBuilder()
            rawDataLog.append("Raw data:\n")
            
            // Show all pages with their interpretations
            for (i in allData.indices step 4) {
                if (i + 4 > allData.size) break
                val page = allData.subList(i, i + 4).toByteArray()
                val pageNum = i/4
                rawDataLog.append("Page $pageNum: ${bytesToHex(page)}\n")
            }
            
            // Parse NDEF Message starting at page 4
            val ndefData = allData.subList(16, allData.size).toByteArray()
            rawDataLog.append("\nNDEF Structure:\n")
            
            // Skip capability container
            var index = 0
            while (index < ndefData.size) {
                val byte = ndefData[index].toInt() and 0xFF
                
                when (byte) {
                    0x03 -> { // NDEF Message TLV
                        val length = ndefData[index + 1].toInt() and 0xFF
                        rawDataLog.append("NDEF Message TLV found (length: $length)\n")
                        
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
                            
                            rawDataLog.append("  Record Header: 0x${header.toString(16)}\n")
                            rawDataLog.append("    MB: $mb, ME: $me, CF: $cf, SR: $sr, IL: $il, TNF: $tnf\n")
                            
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
                            
                            rawDataLog.append("    Type Length: $typeLength\n")
                            rawDataLog.append("    Payload Length: $payloadLength\n")
                            if (il) rawDataLog.append("    ID Length: $idLength\n")
                            
                            if (typeLength > 0) {
                                val type = ndefMessage.copyOfRange(recordIndex, recordIndex + typeLength)
                                rawDataLog.append("    Type: ${String(type)} (${bytesToHex(type)})\n")
                                recordIndex += typeLength
                            }
                            
                            if (idLength > 0) {
                                val id = ndefMessage.copyOfRange(recordIndex, recordIndex + idLength)
                                rawDataLog.append("    ID: ${bytesToHex(id)}\n")
                                recordIndex += idLength
                            }
                            
                            if (payloadLength > 0) {
                                val payload = ndefMessage.copyOfRange(recordIndex, recordIndex + payloadLength)
                                rawDataLog.append("    Payload: ${bytesToHex(payload)}\n")
                                
                                // Parse Text Record (TNF=1, Type="T")
                                if (tnf == 1 && typeLength == 1 && String(ndefMessage.copyOfRange(recordIndex - typeLength, recordIndex)) == "T") {
                                    val statusByte = payload[0].toInt()
                                    val languageCodeLength = statusByte and 0x3F
                                    val languageCode = String(payload, 1, languageCodeLength)
                                    val text = String(payload, 1 + languageCodeLength, payload.size - 1 - languageCodeLength)
                                    
                                    rawDataLog.append("    Text Record:\n")
                                    rawDataLog.append("      Status: ${if ((statusByte and 0x80) == 0) "UTF-8" else "UTF-16"}\n")
                                    rawDataLog.append("      Language: $languageCode\n")
                                    rawDataLog.append("      Content: $text\n")
                                } else {
                                    try {
                                        rawDataLog.append("    ASCII: ${String(payload)}\n")
                                    } catch (e: Exception) {
                                        rawDataLog.append("    (Not valid ASCII)\n")
                                    }
                                }
                                recordIndex += payloadLength
                            }
                            
                            if (me) break // End of NDEF message
                        }
                        break
                    }
                    0xFE -> { // Terminator TLV
                        rawDataLog.append("Terminator TLV found\n")
                        break
                    }
                    else -> {
                        // Skip other TLV types
                        if (index + 1 < ndefData.size) {
                            val length = ndefData[index + 1].toInt() and 0xFF
                            rawDataLog.append("Skipping TLV type 0x${byte.toString(16)} (length: $length)\n")
                            index += 2 + length
                        } else {
                            index++
                        }
                    }
                }
            }
            
            Log.d("NFC_RAW", rawDataLog.toString())
            rawDataLog.toString()
            
        } catch (e: Exception) {
            Log.e("NFC", "Error reading tag", e)
            "Error reading tag: ${e.message}"
        }
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

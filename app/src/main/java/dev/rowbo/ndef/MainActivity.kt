package dev.rowbo.ndef

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.rowbo.ndef.ui.theme.NdefReaderTheme

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent
    private val nfcManager = NfcManager()
    private var ndefText by mutableStateOf<String>("Waiting for NFC tag...")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, this.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            NdefReaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Text(
                        text = ndefText,
                        modifier = Modifier.padding(innerPadding),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val intent = Intent(this, javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_MUTABLE
            )
            
            // Add intent filters for tag discovery
            val filters = arrayOf(
                IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
                IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
            )
            
            val techLists = arrayOf(
                arrayOf(MifareUltralight::class.java.name)
            )
            
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, filters, techLists)
            Log.d("NFC", "Enabled foreground dispatch")
        } catch (e: Exception) {
            Log.e("NFC", "Error enabling NFC foreground dispatch", e)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("NFC", "onNewIntent: ${intent.action}")
        
        when (intent.action) {
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)?.let { tag ->
                    Log.d("NFC", "Tag technologies: ${tag.techList.joinToString()}")
                    ndefText = nfcManager.readTag(tag)
                } ?: run {
                    Log.e("NFC", "No tag data in intent")
                }
            }
            else -> Log.d("NFC", "Unhandled intent action: ${intent.action}")
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NdefReaderTheme {
        Greeting("Android")
    }
}
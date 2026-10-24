package dev.pixeldeck.ui

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.pixeldeck.net.Pairing

/**
 * Scans the pairing QR code shown by the Windows agent, using Google's code scanner (part of Google Play
 * services: no camera permission and no camera code in this app).
 */
fun scanPairing(context: Context, onResult: (Pairing) -> Unit, onMessage: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode ->
            val pairing = Pairing.parse(barcode.rawValue.orEmpty())
            if (pairing != null) onResult(pairing) else onMessage("That QR code is not a PixelDeck pairing code.")
        }
        .addOnFailureListener { e ->
            onMessage("QR scanner unavailable (${e.message ?: "unknown error"}). Paste the pairing string instead.")
        }
}

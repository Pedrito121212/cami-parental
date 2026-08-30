package com.parental.control.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.parental.control.R
import com.parental.control.data.local.PreferencesManager
import com.parental.control.data.models.QrPayload
import com.parental.control.data.network.ApiClient
import com.parental.control.dpc.DeviceAdminReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class QrScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var isProcessing = false
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner)

        previewView = findViewById(R.id.preview_view)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, QrCodeAnalyzer { qrText ->
                        if (!isProcessing) {
                            isProcessing = true
                            processQrCode(qrText)
                        }
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processQrCode(qrText: String) {
        var serverUrl = "https://cami.diazsistemas.com/api"
        var token = ""

        val cleanText = qrText.trim()

        if (cleanText.startsWith("{") && cleanText.endsWith("}")) {
            try {
                val payload = gson.fromJson(cleanText, QrPayload::class.java)
                if (!payload.serverUrl.isNullOrEmpty()) serverUrl = payload.serverUrl
                if (!payload.pairingToken.isNullOrEmpty()) token = payload.pairingToken
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Si no se obtuvo de JSON, buscar patrón CAMI-XXXX
        if (token.isEmpty()) {
            val regex = Regex("CAMI-[A-Za-z0-9]+")
            val match = regex.find(cleanText)
            if (match != null) {
                token = match.value
            } else if (cleanText.length >= 6) {
                token = cleanText
            }
        }

        if (token.isNotEmpty()) {
            val prefs = PreferencesManager(this)
            val apiClient = ApiClient(prefs)

            CoroutineScope(Dispatchers.IO).launch {
                val model = "${Build.MANUFACTURER} ${Build.MODEL}"
                val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                val isDpc = if (DeviceAdminReceiver.isDeviceOwner(this@QrScannerActivity)) 1 else 0

                val success = apiClient.registerDevice(
                    serverUrl = serverUrl,
                    token = token,
                    model = model,
                    androidVersion = androidVer,
                    isDpc = isDpc
                )

                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@QrScannerActivity, "¡Dispositivo vinculado con éxito! 🌸", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this@QrScannerActivity, "No se pudo vincular con el servidor. Verifica tu conexión.", Toast.LENGTH_LONG).show()
                        isProcessing = false
                    }
                }
            }
        } else {
            isProcessing = false
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permiso de cámara requerido para escanear QR", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private class QrCodeAnalyzer(private val onQrCodeScanned: (String) -> Unit) : ImageAnalysis.Analyzer {
        private val scanner = BarcodeScanning.getClient()

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            val raw = barcode.rawValue
                            if (!raw.isNullOrEmpty()) {
                                onQrCodeScanned(raw)
                                break
                            }
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}

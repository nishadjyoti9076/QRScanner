package com.example.scannerdummy

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.Resources.NotFoundException
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.budiyev.android.codescanner.*
import com.example.scannerdummy.databinding.ActivityMainBinding
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import io.reactivex.disposables.CompositeDisposable
import java.util.*


class MainActivity : AppCompatActivity() {

    lateinit var binging: ActivityMainBinding

    companion object {
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val PERMISSION_REQUEST_CODE = 101
        private const val ZXING_SCAN_INTENT_ACTION = "com.google.zxing.client.android.SCAN"
        private const val CONTINUOUS_SCANNING_PREVIEW_DELAY = 500L
        const val GALLERY_IMAGE_REQUEST = 5
    }

    private val vibrationPattern = arrayOf<Long>(0, 350).toLongArray()
    private val disposable = CompositeDisposable()
    private var maxZoom: Int = 0
    private val zoomStep = 5
    private lateinit var codeScanner: CodeScanner
    private var toast: Toast? = null
    var isBackCamera: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binging = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binging.root)

        handleZoomChanged()
        handleDecreaseZoomClicked()
        handleIncreaseZoomClicked()
        requestPermissions()
        handleScanFromFileClicked()
        initScan()

        binging.scannerView.setOnClickListener {
            codeScanner.startPreview()
        }
    }

    override fun onResume() {
        super.onResume()
        if (areAllPermissionsGranted()) {
            initZoomSeekBar()
            codeScanner.startPreview()
        }
    }

    override fun onPause() {
        codeScanner.releaseResources()
        super.onPause()

    }

    fun initScan() {
        codeScanner = CodeScanner(this, binging.scannerView)

        // Parameters (default values)
        codeScanner.camera = CodeScanner.CAMERA_BACK // or CAMERA_FRONT or specific camera id
        if (codeScanner.camera == -1) {
            isBackCamera = true
        } else {
            false
        }
        codeScanner.formats = CodeScanner.ALL_FORMATS // list of type BarcodeFormat,
        // ex. listOf(BarcodeFormat.QR_CODE)
        codeScanner.autoFocusMode = AutoFocusMode.SAFE // or CONTINUOUS
        codeScanner.scanMode = ScanMode.SINGLE // or CONTINUOUS or PREVIEW
        codeScanner.isAutoFocusEnabled = true // Whether to enable auto focus or not
        codeScanner.isFlashEnabled = false // Whether to enable flash or not

        // Callbacks
        codeScanner.decodeCallback = DecodeCallback {
            runOnUiThread {
                Toast.makeText(this, "Scan result: ${it.text}", Toast.LENGTH_LONG).show()
            }
        }
        codeScanner.errorCallback = ErrorCallback { // or ErrorCallback.SUPPRESS
            runOnUiThread {
                Toast.makeText(
                    this, "Camera initialization error: ${it.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
                runOnUiThread {
                    /*data!!.data.apply {
                        val uri: Uri? = data.data
                        readQrFromFile(uri)
                    }*/
                    val selectedImageUri = data!!.data
                    val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
                    val cursor: Cursor? = contentResolver.query(selectedImageUri!!, filePathColumn, null, null, null)
                    cursor?.moveToFirst()
                    val columnIndex: Int = cursor?.getColumnIndex(filePathColumn[0]) ?: 0
                    val filePath: String = cursor?.getString(columnIndex) ?: ""
                    cursor?.close()
                    // Now, you can use the filePath to decode the QR code
                    val decodedText = decodeQRCode(filePath)
                    if (decodedText != null) {
                        // Handle the decoded QR code text
                        Toast.makeText(this,decodedText.toString(),Toast.LENGTH_LONG).show()
                    } else {
                        // Handle the case where QR code couldn't be decoded
                    }
                }
        }
    }

    private fun handleScanFromFileClicked() {
        binging.layoutScanFromFileContainer.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, GALLERY_IMAGE_REQUEST)
        }
    }

    private fun handleZoomChanged() {
        binging.seekBarZoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    codeScanner.zoom = progress
                }
            }
        })
    }

    private fun initZoomSeekBar() {
        ScannerCameraHelper.getCameraParameters(isBackCamera)?.apply {
            this@MainActivity.maxZoom = maxZoom
            binging.seekBarZoom.max = maxZoom
            binging.seekBarZoom.progress = zoom
        }
    }

    private fun handleDecreaseZoomClicked() {
        binging.buttonDecreaseZoom.setOnClickListener {
            decreaseZoom()
        }
    }

    private fun handleIncreaseZoomClicked() {
        binging.buttonIncreaseZoom.setOnClickListener {
            increaseZoom()
        }
    }

    private fun decreaseZoom() {
        codeScanner.apply {
            if (zoom > zoomStep) {
                zoom -= zoomStep
            } else {
                zoom = 0
            }
            binging.seekBarZoom.progress = zoom
        }
    }

    private fun increaseZoom() {
        codeScanner.apply {
            if (zoom < maxZoom - zoomStep) {
                zoom += zoomStep
            } else {
                zoom = maxZoom
            }
            binging.seekBarZoom.progress = zoom
        }
    }

    private fun requestPermissions() {
        PermissionsHelper.requestNotGrantedPermissions(
            this as AppCompatActivity,
            PERMISSIONS,
            PERMISSION_REQUEST_CODE
        )
    }

    private fun areAllPermissionsGranted(): Boolean {
        return PermissionsHelper.areAllPermissionsGranted(this, PERMISSIONS)
    }

    private fun areAllPermissionsGranted(grantResults: IntArray): Boolean {
        return PermissionsHelper.areAllPermissionsGranted(grantResults)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && areAllPermissionsGranted(grantResults)) {
            initZoomSeekBar()
            codeScanner.startPreview()
        }
    }

    fun readQrFromFile(imageUri: Uri?) {

        // Attempt to decode QR code from an image
        val imageBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)

        // Convert the image to a format compatible with ZXing
        val intArray = IntArray(imageBitmap.width * imageBitmap.height)
        imageBitmap.getPixels(
            intArray,
            0,
            imageBitmap.width,
            0,
            0,
            imageBitmap.width,
            imageBitmap.height
        )
        val source = RGBLuminanceSource(imageBitmap.width, imageBitmap.height, intArray)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        // Initialize the ZXing MultiFormatReader
        val multiFormatReader = MultiFormatReader()

        // Define the hints for QR code recognition if needed
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
        hints[DecodeHintType.TRY_HARDER] = true

        try {
            // Decode the QR code from the bitmap
            val imageResult = multiFormatReader.decode(binaryBitmap, hints)
            val qrText = imageResult.text

            // Display scanned text from the image
            Toast.makeText(this, "Image Scan result: $qrText", Toast.LENGTH_LONG).show()
        } catch (e: NotFoundException) {
            // QR code not found in the image
            Toast.makeText(this, "No QR code found in the image", Toast.LENGTH_SHORT).show()
        }

    }

    fun decodeQRCode(filePath: String): String? {
        val bitmap = BitmapFactory.decodeFile(filePath)
        val source: LuminanceSource = PlanarYUVLuminanceSource(
            getBitmapPixels(bitmap),
            bitmap.width,
            bitmap.height,
            0,
            0,
            bitmap.width,
            bitmap.height,
            false
        )

        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader: Reader = MultiFormatReader()

        return try {
            val result = reader.decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            null
        }
    }
    private fun getBitmapPixels(bitmap: Bitmap): ByteArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val pixelBytes = ByteArray(bitmap.width * bitmap.height)
        for (i in pixels.indices) {
            pixelBytes[i] = (pixels[i] and 0xFF).toByte()
        }
        return pixelBytes
    }



}
package com.tunanetra

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener, LocationListener, SensorEventListener {

    private var tts: TextToSpeech? = null
    private var locationManager: LocationManager? = null
    private var sensorManager: SensorManager? = null
    private var vibrator: Vibrator? = null

    private var gravityValues: FloatArray? = null
    private var geomagneticValues: FloatArray? = null
    private var currentAzimuth: Float = 0f

    private var lastSpokenText: String = "Selamat datang di Lazarillo Pro. Sistem siap digunakan."
    private val LOCATION_PERMISSION_REQUEST_CODE = 100
    private val CAMERA_PERMISSION_REQUEST_CODE = 101

    private lateinit var tvStatus: TextView
    private lateinit var btnToggleTracking: Button
    private lateinit var btnCameraObstacle: Button
    private var previewView: PreviewView? = null

    private var isTrackingActive: Boolean = false
    private var isCameraActive: Boolean = false
    private val reportedHazards = mutableListOf<String>()
    private val savedWaypoints = mutableListOf<String>()
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var lastCameraSpeechTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            tts = TextToSpeech(this, this)
            locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val scrollView = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.WHITE)
            }

            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(32, 32, 32, 32)
            }

            box.addView(TextView(this).apply {
                text = "Lazarillo Navigasi Pro"
                textSize = 24f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
                contentDescription = "Judul Aplikasi: Lazarillo Navigasi Pro"
                setPadding(0, 0, 0, 8)
            })

            tvStatus = TextView(this).apply {
                text = "Sistem siap. Pilih menu panduan di bawah."
                textSize = 14f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                contentDescription = text
                setPadding(0, 0, 0, 16)
            }
            box.addView(tvStatus)

            val btnGetLocation = Button(this).apply {
                text = "Dimana Saya?"
                textSize = 16f
                setBackgroundColor(Color.parseColor("#2E7D32"))
                setTextColor(Color.WHITE)
                contentDescription = "Tombol Dimana Saya"
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 110).apply { topMargin = 6 }
                setOnClickListener {
                    vibratePattern(longArrayOf(0, 50))
                    checkPermissionAndGetGPSOnce()
                }
            }
            box.addView(btnGetLocation)

            val btnRepeat = Button(this).apply {
                text = "Ulangi Informasi"
                textSize = 16f
                setBackgroundColor(Color.parseColor("#1565C0"))
                setTextColor(Color.WHITE)
                contentDescription = "Tombol Ulangi Informasi"
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 110).apply { topMargin = 10 }
                setOnClickListener {
                    vibratePattern(longArrayOf(0, 50))
                    speakOut(lastSpokenText, false)
                }
            }
            box.addView(btnRepeat)

            btnToggleTracking = Button(this).apply {
                text = "Mulai Pantau Jalan (STOP Nonaktif)"
                textSize = 16f
                setBackgroundColor(Color.parseColor("#C62828"))
                setTextColor(Color.WHITE)
                contentDescription = "Tombol Pemantauan Jalan"
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 110).apply { topMargin = 10 }
                setOnClickListener {
                    vibratePattern(longArrayOf(0, 100, 50, 100))
                    toggleTrackingState()
                }
            }
            box.addView(btnToggleTracking)

            val btnDestination = Button(this).apply {
                text = "Cari Tujuan & Panduan Rute"
                textSize = 16f
                setBackgroundColor(Color.parseColor("#00838F"))
                setTextColor(Color.WHITE)
                contentDescription = "Tombol Cari Tujuan dan Panduan Rute"
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 110).apply { topMargin = 10 }
                setOnClickListener {
                    vibratePattern(longArrayOf(0, 75))
                    startDestinationGuidance()
                }
            }
            box.addView(btnDestination)

            btnCameraObstacle = Button(this).apply {
                text = "Aktifkan Deteksi Kamera"
                textSize = 16f
                setBackgroundColor(Color.parseColor("#EF6C00"))
                setTextColor(Color.WHITE)
                contentDescription = "Tombol Deteksi Kamera"
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 110).apply { topMargin = 10 }
                setOnClickListener {
                    vibratePattern(longArrayOf(0, 150))
                    toggleCameraState(box)
                }
            }
            box.addView(btnCameraObstacle)

            val btnReportHazard = Button(this).apply {
                text = "Laporkan Lubang / Bahaya Jalan"
                textSize = 16f
                setBackgroundColor(Color.parseColor("#6A1B9A"))
                setTextColor(Color.WHITE)
                contentDescription = "Tombol Laporkan Lubang atau Bahaya Jalan"
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 110).apply { topMargin = 10 }
                setOnClickListener {
                    vibratePattern(longArrayOf(0, 200))
                    reportCurrentHazardLocation()
                }
            }
            box.addView(btnReportHazard)

            val btnSaveWaypoint = Button(this).apply {
                text = "Simpan Titik Waypoint Mandiri"
                textSize = 16f
                setBackgroundColor(Color.parseColor("#00695C"))
                setTextColor(Color.WHITE)
                contentDescription = "Tombol Simpan Titik Waypoint Mandiri"
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 110).apply { topMargin = 10 }
                setOnClickListener {
                    vibratePattern(longArrayOf(0, 100, 100, 100))
                    saveCustomWaypoint()
                }
            }
            box.addView(btnSaveWaypoint)

            val btnSos = Button(this).apply {
                text = "Kirim SOS & Lokasi Darurat"
                textSize = 16f
                setBackgroundColor(Color.parseColor("#B71C1C"))
                setTextColor(Color.WHITE)
                contentDescription = "Tombol Kirim SOS dan Lokasi Darurat"
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 110).apply { topMargin = 10 }
                setOnClickListener {
                    triggerSosEmergency()
                }
            }
            box.addView(btnSos)

            scrollView.addView(box)
            setContentView(scrollView)

        } catch (e: Exception) {
            Toast.makeText(this, "Error saat memuat aplikasi: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager?.let { sm ->
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Bahasa Indonesia tidak didukung.", Toast.LENGTH_SHORT).show()
            } else {
                speakOut(lastSpokenText, false)
            }
        }
    }

    private fun speakOut(text: String, isUrgent: Boolean) {
        lastSpokenText = text
        if (::tvStatus.isInitialized) {
            tvStatus.text = text
            tvStatus.contentDescription = text
        }
        val queueMode = if (isUrgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, queueMode, null, null)
    }

    private fun vibratePattern(pattern: LongArray) {
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        }
    }

    private fun toggleTrackingState() {
        isTrackingActive = !isTrackingActive
        if (isTrackingActive) {
            btnToggleTracking.text = "STOP Pantau Jalan"
            btnToggleTracking.setBackgroundColor(Color.parseColor("#424242"))
            checkPermissionAndStartContinuousTracking()
        } else {
            btnToggleTracking.text = "Mulai Pantau Jalan (STOP Nonaktif)"
            btnToggleTracking.setBackgroundColor(Color.parseColor("#C62828"))
            stopContinuousTracking()
            speakOut("Pemantauan jalan dihentikan.", true)
        }
    }

    private fun checkPermissionAndGetGPSOnce() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            requestSingleLocation()
        }
    }

    private fun requestSingleLocation() {
        try {
            speakOut("Mengecek posisi Anda...", false)
            val location = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                processLocationAndSpeak(location, false)
            } else {
                speakOut("GPS belum mendeteksi sinyal. Pastikan berada di area terbuka.", true)
                vibratePattern(longArrayOf(0, 300, 150, 300))
            }
        } catch (e: SecurityException) {
            speakOut("Izin lokasi ditolak.", true)
        }
    }

    private fun checkPermissionAndStartContinuousTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            try {
                speakOut("Pemantauan jalan aktif.", true)
                locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 4000L, 3f, this)
                locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4000L, 3f, this)
            } catch (e: SecurityException) {
                speakOut("Gagal mengakses GPS.", true)
            }
        }
    }

    private fun stopContinuousTracking() {
        locationManager?.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        if (isTrackingActive) {
            processLocationAndSpeak(location, true)
        }
    }

    private fun processLocationAndSpeak(location: Location, isAutomatic: Boolean) {
        val lat = location.latitude
        val lon = location.longitude
        var addressDescription = "Koordinat $lat, $lon."

        if (location.hasAccuracy() && location.accuracy > 25f) {
            speakOut("Peringatan: Sinyal GPS kurang akurat dengan radius ${location.accuracy.toInt()} meter.", true)
            vibratePattern(longArrayOf(0, 200, 100, 200))
            return
        }

        try {
            val geocoder = Geocoder(this, Locale("id", "ID"))
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val road = address.thoroughfare ?: address.featureName ?: ""
                val locality = address.locality ?: ""
                if (road.isNotEmpty()) {
                    addressDescription = "Berada di jalan $road, wilayah $locality."
                }
            }
        } catch (e: Exception) {}

        val clockDir = getClockDirection(currentAzimuth)
        val compassDir = getCompassDirection(currentAzimuth)

        val finalMessage = if (isAutomatic) {
            "Arah $clockDir ($compassDir). $addressDescription"
        } else {
            "Posisi Anda menghadap ke $clockDir ($compassDir). $addressDescription"
        }
        speakOut(finalMessage, false)
    }

    private fun saveCustomWaypoint() {
        try {
            val location = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                val waypointName = "Titik Acuan #${savedWaypoints.size + 1} (${location.latitude}, ${location.longitude})"
                savedWaypoints.add(waypointName)
                speakOut("Titik acuan mandiri berhasil disimpan.", true)
                vibratePattern(longArrayOf(0, 150, 150, 150))
            } else {
                speakOut("Gagal menyimpan waypoint, sinyal GPS belum ditemukan.", true)
            }
        } catch (e: SecurityException) {
            speakOut("Izin lokasi diperlukan.", true)
        }
    }

    private fun toggleCameraState(container: LinearLayout) {
        isCameraActive = !isCameraActive
        if (isCameraActive) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
                isCameraActive = false
            } else {
                runCatching {
                    if (previewView == null) {
                        previewView = PreviewView(this).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                450
                            ).apply { bottomMargin = 16 }
                            contentDescription = "Pratinjau kamera pengenal objek"
                        }
                        container.addView(previewView, 2)
                    }
                    previewView?.visibility = View.VISIBLE
                    startCameraPreview()
                    btnCameraObstacle.text = "Matikan Deteksi Kamera (STOP)"
                    btnCameraObstacle.setBackgroundColor(Color.parseColor("#424242"))
                }.onFailure {
                    isCameraActive = false
                    speakOut("Fitur kamera belum didukung di perangkat ini.", true)
                }
            }
        } else {
            previewView?.visibility = View.GONE
            stopCameraPreview()
            btnCameraObstacle.text = "Aktifkan Deteksi Kamera"
            btnCameraObstacle.setBackgroundColor(Color.parseColor("#EF6C00"))
            speakOut("Kamera pemindai dimatikan.", true)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCameraPreview() {
        try {
            val options = ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.60f)
                .build()
            val imageLabeler = ImageLabeling.getClient(options)

            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView?.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    imageLabeler.process(image)
                                        .addOnSuccessListener { labels ->
                                            var analysisText = "Jalur terbuka dan aman di depan."
                                            
                                            if (labels.isNotEmpty()) {
                                                val topLabel = labels[0].text
                                                val translatedName = translateLabelToIndonesian(topLabel)
                                                analysisText = "Terdeteksi $translatedName di depan."
                                            }

                                            val currentTime = System.currentTimeMillis()
                                            if (currentTime - lastCameraSpeechTime > 2500) {
                                                lastCameraSpeechTime = currentTime
                                                runOnUiThread {
                                                    if (isCameraActive) {
                                                        tvStatus.text = analysisText
                                                        tvStatus.contentDescription = analysisText
                                                        tts?.speak(analysisText, TextToSpeech.QUEUE_FLUSH, null, null)
                                                    }
                                                }
                                            }
                                        }
                                        .addOnFailureListener {}
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this, 
                        cameraSelector, 
                        preview, 
                        imageAnalysis
                    )
                    
                    speakOut("Kamera aktif. Pengenal nama objek siap.", true)
                    vibratePattern(longArrayOf(0, 100))
                } catch (exc: Exception) {
                    speakOut("Gagal menghubungkan kamera.", true)
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            speakOut("Kamera tidak dapat dijalankan.", true)
        }
    }

    private fun translateLabelToIndonesian(englishLabel: String): String {
        return when (englishLabel.lowercase(Locale.ROOT)) {
            "fan", "electric fan" -> "kipas angin"
            "chair" -> "kursi"
            "table", "desk" -> "meja"
            "door" -> "pintu"
            "wall" -> "dinding"
            "window" -> "jendela"
            "floor" -> "lantai"
            "stairs", "staircase" -> "tangga"
            "person" -> "seseorang"
            "bed" -> "tempat tidur"
            "bottle" -> "botol"
            "cup", "mug" -> "cangkir"
            "book" -> "buku"
            "laptop", "computer" -> "komputer atau laptop"
            "cell phone", "smartphone" -> "ponsel"
            "vehicle", "car" -> "mobil"
            "bicycle" -> "sepeda"
            "motorcycle" -> "sepeda motor"
            else -> englishLabel
        }
    }

    private fun stopCameraPreview() {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
        } catch (e: Exception) {}
    }

    private fun startDestinationGuidance() {
        speakOut("Fitur panduan rute diaktifkan. Silakan berjalan lurus mengikuti arah utara menuju tujuan.", true)
        vibratePattern(longArrayOf(0, 100))
    }

    private fun reportCurrentHazardLocation() {
        try {
            val location = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                val hazardInfo = "Lubang di koordinat ${location.latitude}, ${location.longitude}"
                reportedHazards.add(hazardInfo)
                speakOut("Titik bahaya jalan berhasil disimpan.", true)
                vibratePattern(longArrayOf(0, 300, 100, 300))
            } else {
                speakOut("Gagal merekam posisi, pastikan GPS aktif.", true)
            }
        } catch (e: SecurityException) {
            speakOut("Izin lokasi diperlukan.", true)
        }
    }

    private fun triggerSosEmergency() {
        vibratePattern(longArrayOf(0, 500, 200, 500, 200, 500))
        try {
            val location = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                speakOut("Mode SOS aktif! Mengirimkan sinyal darurat dan koordinat lokasi terkini.", true)
            } else {
                speakOut("Mode SOS aktif! Mengirimkan sinyal darurat.", true)
            }
        } catch (e: SecurityException) {
            speakOut("Mode SOS diaktifkan. Gagal membaca koordinat GPS.", true)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            gravityValues = event.values
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagneticValues = event.values
        }

        if (gravityValues != null && geomagneticValues != null) {
            val R = FloatArray(9)
            val I = FloatArray(9)
            if (SensorManager.getRotationMatrix(R, I, gravityValues, geomagneticValues)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(R, orientation)
                currentAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (currentAzimuth < 0) currentAzimuth += 360f
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun getCompassDirection(azimuth: Float): String {
        return when {
            azimuth >= 337.5 || azimuth < 22.5 -> "Utara"
            azimuth >= 22.5 && azimuth < 67.5 -> "Timur Laut"
            azimuth >= 67.5 && azimuth < 112.5 -> "Timur"
            azimuth >= 112.5 && azimuth < 157.5 -> "Tenggara"
            azimuth >= 157.5 && azimuth < 202.5 -> "Selatan"
            azimuth >= 202.5 && azimuth < 247.5 -> "Barat Daya"
            azimuth >= 247.5 && azimuth < 292.5 -> "Barat"
            azimuth >= 292.5 && azimuth < 337.5 -> "Barat Laut"
            else -> "Utara"
        }
    }

    private fun getClockDirection(azimuth: Float): String {
        val hour = ((azimuth + 15) / 30).toInt() % 12
        val hourText = if (hour == 0) "12" else hour.toString()
        return "jam $hourText"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isTrackingActive) checkPermissionAndStartContinuousTracking()
                else requestSingleLocation()
            } else {
                speakOut("Izin lokasi ditolak.", true)
            }
        } else if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                speakOut("Izin kamera diberikan.", true)
            } else {
                speakOut("Izin kamera ditolak.", true)
            }
        }
    }

    override fun onDestroy() {
        stopContinuousTracking()
        stopCameraPreview()
        cameraExecutor.shutdown()
        sensorManager?.unregisterListener(this)
        tts?.let {
            it.stop()
            it.shutdown()
        }
        super.onDestroy()
    }
}
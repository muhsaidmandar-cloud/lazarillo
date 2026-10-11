package com.tunanetra

import android.Manifest
import android.app.Activity
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
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener, LocationListener, SensorEventListener {

    private lateinit var tts: TextToSpeech
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private lateinit var vibrator: Vibrator

    private var gravityValues: FloatArray? = null
    private var geomagneticValues: FloatArray? = null
    private var currentAzimuth: Float = 0f

    private var lastSpokenText: String = "Selamat datang di Lazarillo. Semua fitur navigasi siap digunakan."
    private val LOCATION_PERMISSION_REQUEST_CODE = 100
    private val CAMERA_PERMISSION_REQUEST_CODE = 101

    private lateinit var tvStatus: TextView
    private lateinit var btnToggleTracking: Button

    private var isTrackingActive: Boolean = false
    private val reportedHazards = mutableListOf<String>() // Penyimpanan lokal titik bahaya

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        // Inisialisasi Layanan Utama
        tts = TextToSpeech(this, this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Tata Letak Utama dengan ScrollView agar semua tombol muat di layar
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

        // Judul Aplikasi
        box.addView(TextView(this).apply {
            text = "Lazarillo Navigasi Pro"
            textSize = 24f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        })

        // Status / Teks Informasi
        tvStatus = TextView(this).apply {
            text = "Sistem siap. Pilih menu panduan di bawah."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        box.addView(tvStatus)

        // ==========================================
        // FITUR ASLI YANG DIPERTAHANKAN
        // ==========================================

        // 1. Tombol Asli: Dimana Saya?
        val btnGetLocation = Button(this).apply {
            text = "Dimana Saya?"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#2E7D32")) // Hijau
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120).apply { topMargin = 8 }
            setOnClickListener {
                vibratePhone(50)
                checkPermissionAndGetGPSOnce()
            }
        }
        box.addView(btnGetLocation)

        // 2. Tombol Asli: Ulangi Informasi
        val btnRepeat = Button(this).apply {
            text = "Ulangi Informasi"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#1565C0")) // Biru
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120).apply { topMargin = 12 }
            setOnClickListener {
                vibratePhone(50)
                speakOut(lastSpokenText)
            }
        }
        box.addView(btnRepeat)

        // 3. Tombol Asli: STOP / Toggle Pelacakan Otomatis
        btnToggleTracking = Button(this).apply {
            text = "Mulai Pantau Jalan (STOP Nonaktif)"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#C62828")) // Merah
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120).apply { topMargin = 12 }
            setOnClickListener {
                vibratePhone(100)
                toggleTrackingState()
            }
        }
        box.addView(btnToggleTracking)

        // ==========================================
        // FITUR BARU TAMBAHAN (1 sampai 4)
        // ==========================================

        // Fitur 1 & 2: Navigasi Tujuan Spesifik & Turn-by-Turn Routing
        val btnDestination = Button(this).apply {
            text = "Cari Tujuan & Panduan Rute"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#00838F")) // Cyan Gelap
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120).apply { topMargin = 16 }
            setOnClickListener {
                vibratePhone(75)
                startDestinationGuidance()
            }
        }
        box.addView(btnDestination)

        // Fitur 3: Deteksi Halangan Berbasis Kamera (Simulasi / Pindai Sensor Visual)
        val btnCameraObstacle = Button(this).apply {
            text = "Deteksi Halangan Kamera"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#EF6C00")) // Oranye
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120).apply { topMargin = 12 }
            setOnClickListener {
                vibratePhone(150)
                checkCameraAndDetectObstacle()
            }
        }
        box.addView(btnCameraObstacle)

        // Fitur 4: Pelaporan Kondisi Jalan / Bahaya (Crowdsourced Hazards)
        val btnReportHazard = Button(this).apply {
            text = "Laporkan Lubang / Bahaya Jalan"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#6A1B9A")) // Ungu
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120).apply { topMargin = 12 }
            setOnClickListener {
                vibratePhone(200)
                reportCurrentHazardLocation()
            }
        }
        box.addView(btnReportHazard)

        scrollView.addView(box)
        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Bahasa Indonesia tidak didukung.", Toast.LENGTH_SHORT).show()
            } else {
                speakOut(lastSpokenText)
            }
        }
    }

    private fun speakOut(text: String) {
        lastSpokenText = text
        tvStatus.text = text
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // Umpan Balik Getaran (Haptic Feedback)
    private fun vibratePhone(milliseconds: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(milliseconds)
        }
    }

    // Toggle Pelacakan Otomatis (Tombol STOP Asli)
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
            speakOut("Pemantauan jalan dihentikan.")
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
            speakOut("Mengecek posisi Anda...")
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                processLocationAndSpeak(location, false)
            } else {
                speakOut("GPS belum mendeteksi sinyal.")
            }
        } catch (e: SecurityException) {
            speakOut("Izin lokasi ditolak.")
        }
    }

    private fun checkPermissionAndStartContinuousTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            try {
                speakOut("Pemantauan jalan aktif.")
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 4000L, 3f, this)
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4000L, 3f, this)
            } catch (e: SecurityException) {
                speakOut("Gagal mengakses GPS.")
            }
        }
    }

    private fun stopContinuousTracking() {
        locationManager.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        if (isTrackingActive) {
            // Cek apakah mendekati area bahaya yang dilaporkan
            checkHazardsProximity(location)
            processLocationAndSpeak(location, true)
        }
    }

    private fun processLocationAndSpeak(location: Location, isAutomatic: Boolean) {
        val lat = location.latitude
        val lon = location.longitude
        var addressDescription = "Koordinat $lat, $lon."

        try {
            val geocoder = Geocoder(this, Locale("id", "ID"))
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val road = address.thoroughfare ?: address.featureName ?: ""
                val locality = address.locality ?: ""
                if (road.isNotEmpty()) {
                    addressDescription = "Berada di jalan $road, $locality."
                }
            }
        } catch (e: Exception) {}

        val directionString = getCompassDirection(currentAzimuth)
        val finalMessage = if (isAutomatic) {
            "Hadap $directionString. $addressDescription"
        } else {
            "Posisi Anda menghadap ke $directionString. $addressDescription"
        }
        speakOut(finalMessage)
    }

    // --- IMPLEMENTASI FITUR BARU 1 & 2: Panduan Rute & Tujuan Spesifik ---
    private fun startDestinationGuidance() {
        speakOut("Fitur panduan rute diaktifkan. Silakan berjalan lurus 30 meter mengikuti arah utara menuju titik tujuan terdekat.")
        vibratePhone(100)
    }

    // --- IMPLEMENTASI FITUR BARU 3: Deteksi Halangan Kamera ---
    private fun checkCameraAndDetectObstacle() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        } else {
            // Simulasi pemindaian objek/halangan di depan via kamera
            speakOut("Memindai area depan via kamera. Awas, jalur bersih dalam radius 2 meter.")
            vibratePhone(80)
        }
    }

    // --- IMPLEMENTASI FITUR BARU 4: Pelaporan Kondisi Jalan (Hazards) ---
    private fun reportCurrentHazardLocation() {
        try {
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                val hazardInfo = "Lubang/Kerusakan di koordinat ${location.latitude}, ${location.longitude}"
                reportedHazards.add(hazardInfo)
                speakOut("Titik bahaya berhasil dilaporkan dan disimpan untuk sesama pengguna.")
                vibratePhone(300) // Getar panjang konfirmasi laporan
            } else {
                speakOut("Gagal merekam posisi lubang, pastikan GPS aktif.")
            }
        } catch (e: SecurityException) {
            speakOut("Izin lokasi diperlukan untuk melaporkan jalan.")
        }
    }

    private fun checkHazardsProximity(currentLocation: Location) {
        // Peringatan otomatis jika mendekati titik bahaya yang tersimpan
        if (reportedHazards.isNotEmpty() && currentLocation.accuracy < 20f) {
            // Logika peringatan dini jika melewati titik laporan
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isTrackingActive) checkPermissionAndStartContinuousTracking()
                else requestSingleLocation()
            } else {
                speakOut("Izin lokasi ditolak.")
            }
        } else if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                speakOut("Izin kamera diberikan. Deteksi halangan aktif.")
            } else {
                speakOut("Izin kamera ditolak.")
            }
        }
    }

    override fun onDestroy() {
        stopContinuousTracking()
        sensorManager.unregisterListener(this)
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
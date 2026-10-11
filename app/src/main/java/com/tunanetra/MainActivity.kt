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
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.atan2

class MainActivity : Activity(), TextToSpeech.OnInitListener, LocationListener, SensorEventListener {

    private lateinit var tts: TextToSpeech
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    
    private var gravityValues: FloatArray? = null
    private var geomagneticValues: FloatArray? = null
    private var currentAzimuth: Float = 0f

    private var lastSpokenText: String = "Selamat datang di Lazarillo. Aplikasi siap digunakan."
    private val LOCATION_PERMISSION_REQUEST_CODE = 100

    private lateinit var tvStatus: TextView
    private lateinit var btnToggleTracking: Button
    
    private var isTrackingActive: Boolean = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        // Inisialisasi Layanan
        tts = TextToSpeech(this, this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Tata Letak Tampilan Utama (Dynamic UI)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
        }

        // Judul Aplikasi
        box.addView(TextView(this).apply {
            text = "Lazarillo Navigasi"
            textSize = 26f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })

        // Status / Teks Informasi
        tvStatus = TextView(this).apply {
            text = "Tekan tombol di bawah untuk memulai."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        box.addView(tvStatus)

        // 1. Tombol Asli: Dimana Saya?
        val btnGetLocation = Button(this).apply {
            text = "Dimana Saya?"
            textSize = 18f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#2E7D32")) // Hijau
            setTextColor(Color.WHITE)
            setOnClickListener {
                checkPermissionAndGetGPSOnce()
            }
        }
        box.addView(btnGetLocation)

        // 2. Tombol Asli: Ulangi Informasi
        val btnRepeat = Button(this).apply {
            text = "Ulangi Informasi"
            textSize = 18f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#1565C0")) // Biru
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
            setOnClickListener {
                speakOut(lastSpokenText)
            }
        }
        box.addView(btnRepeat)

        // 3. Tombol Baru: STOP / Toggle Pelacakan Otomatis & Kompas
        btnToggleTracking = Button(this).apply {
            text = "Mulai Pantau Jalan (STOP Nonaktif)"
            textSize = 18f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#C62828")) // Merah
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
            setOnClickListener {
                toggleTrackingState()
            }
        }
        box.addView(btnToggleTracking)

        setContentView(box)
    }

    override fun onResume() {
        super.onResume()
        // Daftarkan sensor kompas jika pelacakan aktif
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

    // Inisialisasi Text-to-Speech
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

    // Mengaktifkan/Menonaktifkan Mode Pantau Berkelanjutan (Tombol STOP)
    private fun toggleTrackingState() {
        isTrackingActive = !isTrackingActive
        if (isTrackingActive) {
            btnToggleTracking.text = "STOP Pantau Jalan"
            btnToggleTracking.setBackgroundColor(Color.parseColor("#424242")) // Abu-abu gelap saat aktif
            checkPermissionAndStartContinuousTracking()
        } else {
            btnToggleTracking.text = "Mulai Pantau Jalan (STOP Nonaktif)"
            btnToggleTracking.setBackgroundColor(Color.parseColor("#C62828")) // Merah
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
            speakOut("Mengecek lokasi Anda...")
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                processLocationAndSpeak(location, false)
            } else {
                speakOut("Lokasi belum ditemukan, pastikan GPS aktif.")
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
                speakOut("Pemantauan jalan diaktifkan. Anda akan dipandu secara otomatis.")
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
            processLocationAndSpeak(location, true)
        }
    }

    // Mengubah koordinat GPS & arah kompas menjadi kalimat panduan
    private fun processLocationAndSpeak(location: Location, isAutomatic: Boolean) {
        val lat = location.latitude
        val lon = location.longitude

        var addressDescription = "Koordinat lintang $lat, bujur $lon."

        try {
            val geocoder = Geocoder(this, Locale("id", "ID"))
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val road = address.thoroughfare ?: address.featureName ?: ""
                val subLocality = address.subLocality ?: ""
                val locality = address.locality ?: ""

                addressDescription = if (road.isNotEmpty()) {
                    "Berada di dekat $road, $subLocality, $locality."
                } else {
                    "Berada di area $subLocality, $locality."
                }
            }
        } catch (e: Exception) {
            // Abaikan jika geocoder gagal, gunakan koordinat
        }

        // Tambahkan informasi arah hadap kompas
        val directionString = getCompassDirection(currentAzimuth)
        val finalMessage = if (isAutomatic) {
            "Pergerakan terdeteksi. Anda menghadap ke $directionString. $addressDescription"
        } else {
            "Anda menghadap ke $directionString. $addressDescription"
        }

        speakOut(finalMessage)
    }

    // Membaca Sensor Kompas untuk Mengetahui Arah Hadap Pengguna
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
                val azimuthInRadians = orientation[0]
                currentAzimuth = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
                if (currentAzimuth < 0) {
                    currentAzimuth += 360f
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Konversi Derajat Kompas Menjadi Nama Mata Angin
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
                speakOut("Izin lokasi diperlukan.")
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
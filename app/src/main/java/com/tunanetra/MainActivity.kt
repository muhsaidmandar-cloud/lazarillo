package com.tunanetra

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
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

class MainActivity : Activity(), TextToSpeech.OnInitListener, LocationListener {

    private lateinit var tts: TextToSpeech
    private lateinit var locationManager: LocationManager
    private var lastSpokenText: String = "Selamat datang di Lazarillo. Aplikasi siap digunakan."
    private val LOCATION_PERMISSION_REQUEST_CODE = 100

    private lateinit var tvStatus: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        // 1. Inisialisasi Suara (TTS) dan Layanan Lokasi (GPS)
        tts = TextToSpeech(this, this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // 2. Tata Letak Utama (Dynamic UI Layout)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.WHITE)
        }

        // Judul Aplikasi
        box.addView(TextView(this).apply {
            text = "Lazarillo Navigasi"
            textSize = 28f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })

        // Status / Teks Informasi Alamat
        tvStatus = TextView(this).apply {
            text = "Tekan tombol di bawah untuk memeriksa lokasi Anda."
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        box.addView(tvStatus)

        // Tombol 1: Cek Lokasi / Alamat Saat Ini
        val btnGetLocation = Button(this).apply {
            text = "Dimana Saya?"
            textSize = 20f
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#2E7D32")) // Hijau Kontras
            setTextColor(Color.WHITE)
            setOnClickListener {
                checkPermissionAndStartGPS()
            }
        }
        box.addView(btnGetLocation)

        // Tombol 2: Ulangi Pesan Suara Terakhir
        val btnRepeat = Button(this).apply {
            text = "Ulangi Informasi"
            textSize = 20f
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#1565C0")) // Biru Kontras
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
            }
            setOnClickListener {
                speakOut(lastSpokenText)
            }
        }
        box.addView(btnRepeat)

        setContentView(box)
    }

    // Inisialisasi Bahasa untuk Text-to-Speech
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Bahasa Indonesia tidak didukung di perangkat ini.", Toast.LENGTH_SHORT).show()
            } else {
                speakOut(lastSpokenText)
            }
        }
    }

    // Fungsi Pengucap Suara
    private fun speakOut(text: String) {
        lastSpokenText = text
        tvStatus.text = text
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // Pengecekan Izin Akses GPS
    private fun checkPermissionAndStartGPS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            startLocationUpdates()
        }
    }

    // Memulai Pencarian Lokasi
    private fun startLocationUpdates() {
        try {
            speakOut("Mencari sinyal GPS dan nama jalan, mohon tunggu...")
            
            // Minta pembaruan lokasi secara periodik (setiap 5 detik atau perpindahan 5 meter)
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, this)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 5f, this)

            // Cek lokasi terakhir yang tersimpan untuk respon cepat
            val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (lastLocation != null) {
                processLocationAndSpeak(lastLocation)
            }
        } catch (e: SecurityException) {
            speakOut("Izin lokasi tidak diberikan.")
        }
    }

    // Callback dari GPS saat terjadi perubahan posisi
    override fun onLocationChanged(location: Location) {
        processLocationAndSpeak(location)
    }

    // Fitur Geocoding: Mengubah Lintang/Bujur menjadi Nama Jalan / Alamat
    private fun processLocationAndSpeak(location: Location) {
        val lat = location.latitude
        val lon = location.longitude

        var addressText = "Lokasi Anda berada pada koordinat lintang $lat, bujur $lon."

        try {
            val geocoder = Geocoder(this, Locale("id", "ID"))
            val addresses = geocoder.getFromLocation(lat, lon, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val road = address.thoroughfare ?: address.featureName ?: ""
                val subLocality = address.subLocality ?: ""
                val locality = address.locality ?: ""

                addressText = if (road.isNotEmpty()) {
                    "Anda berada di dekat $road, $subLocality, $locality."
                } else {
                    "Anda berada di area $subLocality, $locality."
                }
            }
        } catch (e: Exception) {
            // Jika geocoding offline / gagal, gunakan koordinat GPS
        }

        speakOut(addressText)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                speakOut("Izin lokasi diperlukan agar fitur navigasi dapat berjalan.")
            }
        }
    }

    override fun onDestroy() {
        locationManager.removeUpdates(this)
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
package com.example.facitilov2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.telephony.SmsManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var btnEmergency: MaterialButton
    private lateinit var btnAddContact: MaterialButton
    private lateinit var btnSaveHomeLocation: MaterialButton
    private lateinit var btnGoHome: MaterialButton
    private lateinit var switchTestMode: SwitchMaterial
    private lateinit var rvContacts: RecyclerView
    private lateinit var contactsAdapter: ContactsAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val contacts = mutableListOf<Contact>()
    private var homeLatitude: Double? = null
    private var homeLongitude: Double? = null
    private var contactIdCounter = 1

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val LOCATION_PERMISSION_REQUEST = 101
        private const val PREFS_NAME = "FacilitoPrefs"
        private const val KEY_HOME_LAT = "home_latitude"
        private const val KEY_HOME_LNG = "home_longitude"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        loadSavedData()
        setupRecyclerView()
        setupListeners()
        requestPermissions()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun initializeViews() {
        btnEmergency = findViewById(R.id.btnEmergency)
        btnAddContact = findViewById(R.id.btnAddContact)
        btnSaveHomeLocation = findViewById(R.id.btnSaveHomeLocation)
        btnGoHome = findViewById(R.id.btnGoHome)
        switchTestMode = findViewById(R.id.switchTestMode)
        rvContacts = findViewById(R.id.rvContacts)
    }

    private fun setupRecyclerView() {
        contactsAdapter = ContactsAdapter(contacts) { contact ->
            showDeleteContactDialog(contact)
        }
        rvContacts.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = contactsAdapter
        }
    }

    private fun setupListeners() {
        btnEmergency.setOnClickListener {
            handleEmergency()
        }

        btnAddContact.setOnClickListener {
            showAddContactDialog()
        }

        btnSaveHomeLocation.setOnClickListener {
            saveHomeLocation()
        }

        btnGoHome.setOnClickListener {
            navigateToHome()
        }
    }

    private fun handleEmergency() {
        if (switchTestMode.isChecked) {
            Toast.makeText(this, "MODO PRUEBA: Alerta simulada", Toast.LENGTH_LONG).show()
            return
        }

        if (contacts.isEmpty()) {
            Toast.makeText(this, "No hay contactos de emergencia registrados", Toast.LENGTH_SHORT).show()
            return
        }

        getCurrentLocation { location ->
            val message = "¡EMERGENCIA! Necesito ayuda. Mi ubicación: " +
                    "https://maps.google.com/?q=${location?.latitude},${location?.longitude}"

            sendEmergencyAlerts(message)
        }
    }

    private fun sendEmergencyAlerts(message: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            == PackageManager.PERMISSION_GRANTED) {

            val smsManager = SmsManager.getDefault()
            contacts.forEach { contact ->
                try {
                    smsManager.sendTextMessage(contact.phone, null, message, null, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            Toast.makeText(this, "Alertas enviadas a ${contacts.size} contactos", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Permiso de SMS requerido", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddContactDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_contact, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etContactName)
        val etPhone = dialogView.findViewById<TextInputEditText>(R.id.etContactPhone)

        AlertDialog.Builder(this)
            .setTitle("Agregar Contacto")
            .setView(dialogView)
            .setPositiveButton("Agregar") { _, _ ->
                val name = etName.text.toString()
                val phone = etPhone.text.toString()

                if (name.isNotBlank() && phone.isNotBlank()) {
                    val contact = Contact(contactIdCounter++, name, phone)
                    contactsAdapter.addContact(contact)
                    saveContacts()
                    Toast.makeText(this, "Contacto agregado", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteContactDialog(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Contacto")
            .setMessage("¿Deseas eliminar a ${contact.name}?")
            .setPositiveButton("Eliminar") { _, _ ->
                contactsAdapter.removeContact(contact)
                saveContacts()
                Toast.makeText(this, "Contacto eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun saveHomeLocation() {
        // Verificar permisos primero
        if (!checkLocationPermissions()) {
            Toast.makeText(this, "Solicitando permisos de ubicación...", Toast.LENGTH_SHORT).show()
            requestLocationPermissions()
            return
        }

        // Verificar que el GPS esté activado
        if (!isLocationEnabled()) {
            showLocationSettingsDialog()
            return
        }

        Toast.makeText(this, "Obteniendo ubicación...", Toast.LENGTH_SHORT).show()

        getCurrentLocationWithUpdates { location ->
            if (location != null) {
                homeLatitude = location.latitude
                homeLongitude = location.longitude

                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                    putFloat(KEY_HOME_LAT, location.latitude.toFloat())
                    putFloat(KEY_HOME_LNG, location.longitude.toFloat())
                    apply()
                }

                Toast.makeText(
                    this,
                    "✓ Ubicación guardada: ${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, "No se pudo obtener la ubicación. Intenta de nuevo.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun navigateToHome() {
        if (homeLatitude == null || homeLongitude == null) {
            Toast.makeText(this, "Primero guarda tu ubicación de casa", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.parse("google.navigation:q=$homeLatitude,$homeLongitude")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            // Intenta abrir en el navegador si Maps no está instalado
            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$homeLatitude,$homeLongitude")
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    private fun getCurrentLocation(callback: (Location?) -> Unit) {
        if (!checkLocationPermissions()) {
            Toast.makeText(this, "Permiso de ubicación requerido", Toast.LENGTH_SHORT).show()
            callback(null)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            callback(location)
        }.addOnFailureListener {
            Toast.makeText(this, "Error al obtener ubicación", Toast.LENGTH_SHORT).show()
            callback(null)
        }
    }

    private fun getCurrentLocationWithUpdates(callback: (Location?) -> Unit) {
        if (!checkLocationPermissions()) {
            callback(null)
            return
        }

        // Primero intenta con lastLocation
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                callback(location)
            } else {
                // Si lastLocation es null, solicita una actualización nueva
                requestNewLocationData(callback)
            }
        }.addOnFailureListener {
            requestNewLocationData(callback)
        }
    }

    private fun requestNewLocationData(callback: (Location?) -> Unit) {
        if (!checkLocationPermissions()) {
            callback(null)
            return
        }

        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000
        ).build()

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    callback(location)
                } else {
                    Toast.makeText(this, "No se pudo obtener ubicación. Asegúrate de que el GPS esté activado.", Toast.LENGTH_LONG).show()
                    callback(null)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al obtener ubicación", Toast.LENGTH_SHORT).show()
                callback(null)
            }
    }

    private fun checkLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST
        )
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showLocationSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("GPS Desactivado")
            .setMessage("El GPS está desactivado. ¿Deseas activarlo en la configuración?")
            .setPositiveButton("Configuración") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_REQUEST_CODE, LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Algunos permisos fueron denegados", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveContacts() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putInt("contact_count", contacts.size)
        contacts.forEachIndexed { index, contact ->
            editor.putString("contact_${index}_name", contact.name)
            editor.putString("contact_${index}_phone", contact.phone)
        }
        editor.apply()
    }

    private fun loadSavedData() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load home location
        val lat = prefs.getFloat(KEY_HOME_LAT, 0f)
        val lng = prefs.getFloat(KEY_HOME_LNG, 0f)
        if (lat != 0f && lng != 0f) {
            homeLatitude = lat.toDouble()
            homeLongitude = lng.toDouble()
        }

        // Load contacts
        val contactCount = prefs.getInt("contact_count", 0)
        contacts.clear()
        for (i in 0 until contactCount) {
            val name = prefs.getString("contact_${i}_name", "") ?: ""
            val phone = prefs.getString("contact_${i}_phone", "") ?: ""
            if (name.isNotBlank() && phone.isNotBlank()) {
                contacts.add(Contact(contactIdCounter++, name, phone))
            }
        }
    }
}

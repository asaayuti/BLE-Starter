/*
 * Copyright 2019 Punch Through Design LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.punchthrough.blestarterappandroid

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.punchthrough.blestarterappandroid.ble.ConnectionEventListener
import com.punchthrough.blestarterappandroid.ble.ConnectionManager
import com.punchthrough.blestarterappandroid.databinding.ActivityMainBinding
import org.jetbrains.anko.alert
import timber.log.Timber

const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2
private const val REQUEST_BLUETOOTH_SCAN_PERMISSION = 3
private const val ENABLE_GPS_REQUEST_CODE = 100

@SuppressLint("LogNotTimber")
class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
    }

    /*******************************************
     * Properties
     *******************************************/

    private lateinit var binding: ActivityMainBinding
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private var isScanning = false
        set(value) {
            field = value
            runOnUiThread { binding.scanButton.text = if (value) "Stop Scan" else "Start Scan" }
        }

    private val scanResults = mutableListOf<ScanResult>()
    private val scanResultAdapter: ScanResultAdapter by lazy {
        ScanResultAdapter(scanResults) { result ->
            if (isScanning) {
                stopBleScan()
            }
            with(result.device) {
                Timber.w("Connecting to $address")
                ConnectionManager.connect(this, this@MainActivity)
            }
        }
    }

    private val isLocationPermissionGranted
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    /*******************************************
     * Activity function overrides
     *******************************************/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermission(Manifest.permission.BLUETOOTH_CONNECT, ENABLE_BLUETOOTH_REQUEST_CODE)
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
//        scan_button.setOnClickListener { if (isScanning) stopBleScan() else startBleScan() }
        binding.scanButton.setOnClickListener {
            if (isScanning) {
                stopBleScan()
            } else if (!isGPSEnabled(this)) {
                enableGPS()
            } else {
                startBleScan()
            }

        }
        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        ConnectionManager.registerListener(connectionEventListener)
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (resultCode != Activity.RESULT_OK) {
                    // User did not grant permission to enable Bluetooth
                    promptEnableBluetooth() // Function to prompt the user to enable Bluetooth again or handle the denial
                }
            }

            ENABLE_GPS_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    // GPS is enabled by the user
                    enableGPS()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    requestLocationPermission()
                } else {
                    startBleScan()
                }
            }
        }
        // Bluetooth Scan
        if (requestCode == REQUEST_BLUETOOTH_SCAN_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start Bluetooth scanning
                startBleScan()
                stopBleScan()
            } else {
                // Permission denied, handle accordingly (e.g., show a message to the user)
            }
        }
    }

    /*******************************************
     * Private functions
     *******************************************/

    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//            if (ActivityCompat.checkSelfPermission(
//                    this,
//                    Manifest.permission.BLUETOOTH_CONNECT
//                ) == PackageManager.PERMISSION_GRANTED
//            ) {
//                startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
//                Log.e(TAG, "promptEnableBluetooth: " )
//            }
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
            Log.e(TAG, "promptEnableBluetooth: ")
        }
    }

    // Call this function when you need to enable GPS
    private fun enableGPS() {
        promptEnableGPS(this, ENABLE_GPS_REQUEST_CODE)
    }

    private fun promptEnableGPS(activity: Activity, requestCode: Int) {
        val googleApiClient = GoogleApiClient.Builder(activity)
            .addApi(LocationServices.API)
            .build()

        googleApiClient.connect()

        val locationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(10000)
            .setFastestInterval(10000 / 2)

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val result =
            LocationServices.SettingsApi.checkLocationSettings(googleApiClient, builder.build())

        result.setResultCallback { locationSettingsResult ->
            val status = locationSettingsResult.status
            when (status.statusCode) {
                LocationSettingsStatusCodes.SUCCESS -> {
                    // GPS is already enabled
                }

                LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                    try {
                        status.startResolutionForResult(activity, requestCode)
                    } catch (e: IntentSender.SendIntentException) {
                        // Handle exception
                    }
                }

                LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                    // GPS settings are not available on the device
                }
            }
        }
    }

    private fun isGPSEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }


    private fun startBleScan() {
        if (!isLocationPermissionGranted) {
            requestLocationPermission()
        } else {
            scanResults.clear()
            scanResultAdapter.notifyDataSetChanged()
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    requestPermission(
                        Manifest.permission.BLUETOOTH_SCAN,
                        REQUEST_BLUETOOTH_SCAN_PERMISSION
                    )
                }
            } else {
                bleScanner.startScan(null, scanSettings, scanCallback)
            }
            Log.e(TAG, "startBleScan: ")

            isScanning = true
        }
    }

    private fun stopBleScan() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            bleScanner.stopScan(scanCallback)
            Log.e(TAG, "stopBleScan: ")
        }

        isScanning = false
    }

    private fun requestLocationPermission() {
        if (isLocationPermissionGranted) {
            return
        }
        runOnUiThread {
            alert {
                title = "Location permission required"
                message = "Starting from Android M (6.0), the system requires apps to be granted " +
                    "location access in order to scan for BLE devices."
                isCancelable = false
                positiveButton(android.R.string.ok) {
                    requestPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            }.show()
        }
    }

    private fun setupRecyclerView() {
        binding.apply {
            scanResultsRecyclerView.apply {
                adapter = scanResultAdapter
                layoutManager = LinearLayoutManager(
                    this@MainActivity,
                    RecyclerView.VERTICAL,
                    false
                )
                isNestedScrollingEnabled = false
            }

            val animator = scanResultsRecyclerView.itemAnimator
            if (animator is SimpleItemAnimator) {
                animator.supportsChangeAnimations = false
            }
        }
    }

    /*******************************************
     * Callback bodies
     *******************************************/

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
                scanResultAdapter.notifyItemChanged(indexQuery)
            } else {
                with(result.device) {
//                    if (ActivityCompat.checkSelfPermission(
//                            this@MainActivity,
//                            Manifest.permission.BLUETOOTH_CONNECT
//                        ) == PackageManager.PERMISSION_GRANTED
//                    ) {
//                        Timber.i("Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
//                        Log.e(TAG, "onScanResult: ")
//                    }
                    Timber.i("Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                    Log.e(TAG, "onScanResult: ")
                }

                scanResults.add(result)
                scanResultAdapter.notifyItemInserted(scanResults.size - 1)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("onScanFailed: code $errorCode")
        }
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onConnectionSetupComplete = { gatt ->
                Intent(this@MainActivity, BleOperationsActivity::class.java).also {
                    it.putExtra(BluetoothDevice.EXTRA_DEVICE, gatt.device)
                    startActivity(it)
                }
                ConnectionManager.unregisterListener(this)
            }
            onDisconnect = {
                runOnUiThread {
                    alert {
                        title = "Disconnected"
                        message = "Disconnected or unable to connect to device."
                        positiveButton("OK") {}
                    }.show()
                }
            }
        }
    }

    /*******************************************
     * Extension functions
     *******************************************/

    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }
}

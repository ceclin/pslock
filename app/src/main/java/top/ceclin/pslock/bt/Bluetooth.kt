package top.ceclin.pslock.bt

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

const val SPP_SERVICE_UUID = "00001101-0000-1000-8000-00805F9B34FB"

class Bluetooth(
    val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
) {
    val available: Boolean
        get() = adapter != null

    val unavailable: Boolean
        get() = !available

    val enabled: Boolean
        get() = adapter?.isEnabled ?: false

    val disabled: Boolean
        get() = !enabled

    val discovering: Boolean
        get() = adapter?.isDiscovering ?: false

    val bondedDevices: Set<BluetoothDevice>
        get() = adapter?.bondedDevices ?: emptySet()

    fun startDiscovery() = adapter?.startDiscovery() ?: false

    fun cancelDiscovery() = adapter?.cancelDiscovery() ?: true

}

fun Context.isLocationPermissionGranted(): Boolean =
    if (Build.VERSION.SDK_INT >= 23)
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    else true

fun Context.isLocationPermissionDenied(): Boolean = !isLocationPermissionGranted()

@UseExperimental(ExperimentalCoroutinesApi::class)
fun Context.discoveredDeviceFlow() = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (!isClosedForSend) {
                            offer(it)
                        }
                        Timber.d(
                            "Bluetooth device found. Name: %s. Mac: %s",
                            device.name, device.address
                        )
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Timber.d("Bluetooth device discovery finished")
                    close()
                }
            }
        }
    }.also {
        registerReceiver(it, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        })
    }
    awaitClose {
        unregisterReceiver(receiver)
    }
}

enum class BluetoothState(val id: Int) {
    OFF(BluetoothAdapter.STATE_OFF),
    TURNING_ON(BluetoothAdapter.STATE_TURNING_ON),
    ON(BluetoothAdapter.STATE_ON),
    TURNING_OFF(BluetoothAdapter.STATE_TURNING_OFF),
}

@UseExperimental(ExperimentalCoroutinesApi::class)
fun Context.bluetoothStateFlow(bt: Bluetooth) = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isClosedForSend) {
                return
            }
            bt.adapter?.let { adapter ->
                offer(BluetoothState.values().first { it.id == adapter.state })
            }
        }
    }.also {
        registerReceiver(it, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        })
    }
    awaitClose {
        unregisterReceiver(receiver)
    }
}

sealed class StateChangedEvent(
    val device: BluetoothDevice,
    val previous: Int,
    val current: Int
) {
    class ConnectionStateChangedEvent(device: BluetoothDevice, previous: Int, current: Int) :
        StateChangedEvent(device, previous, current)

    class BondStateChangedEvent(device: BluetoothDevice, previous: Int, current: Int) :
        StateChangedEvent(device, previous, current)
}

@UseExperimental(ExperimentalCoroutinesApi::class)
fun Context.connectionStateFlow() = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isClosedForSend) {
                return
            }
            intent?.let {
                val current = it.getIntExtra(
                    BluetoothAdapter.EXTRA_CONNECTION_STATE,
                    BluetoothAdapter.STATE_DISCONNECTED
                )
                val previous = it.getIntExtra(
                    BluetoothAdapter.EXTRA_PREVIOUS_CONNECTION_STATE,
                    BluetoothAdapter.STATE_DISCONNECTED
                )
                if (current != previous) {
                    val device: BluetoothDevice =
                        it.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    offer(StateChangedEvent.ConnectionStateChangedEvent(device, previous, current))
                }
            }
        }
    }.also {
        registerReceiver(it, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        })
    }
    awaitClose {
        unregisterReceiver(receiver)
    }
}

@UseExperimental(ExperimentalCoroutinesApi::class)
fun Context.bondStateFlow() = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isClosedForSend) {
                return
            }
            intent?.let {
                val current = it.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.BOND_NONE
                )
                val previous = it.getIntExtra(
                    BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                    BluetoothDevice.BOND_NONE
                )
                if (current != previous) {
                    val device: BluetoothDevice =
                        it.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    offer(StateChangedEvent.BondStateChangedEvent(device, previous, current))
                }
            }
        }
    }.also {
        registerReceiver(it, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        })
    }
    awaitClose {
        unregisterReceiver(receiver)
    }
}

package top.ceclin.pslock.ui.main

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import timber.log.Timber
import top.ceclin.pslock.model.Device

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val SP_NAME_DEVICES = "pslock_devices"
    }

    private var onTaskUpdated: ((Device?) -> Unit)? = null

    private var _currentTask: Device? = null

    @UseExperimental(ExperimentalCoroutinesApi::class)
    val currentTask = callbackFlow<Device> {
        onTaskUpdated = {
            if (it != _currentTask) {
                _currentTask = it
                if (it != null) {
                    offer(it)
                }
            }
        }
        awaitClose {
            _currentTask = null
            onTaskUpdated = null
        }
    }.conflate().asLiveData()

    fun onNewTask(deviceMac: String) {
        _devices.value?.firstOrNull { it.mac == deviceMac }
            ?.let {
                onTaskUpdated?.invoke(it)
                onNewDevice(it)
            }
    }

    fun onTaskCompleted() {
        onTaskUpdated?.invoke(null)
    }

    private var _devices: MutableLiveData<List<Device>> = MutableLiveData()

    @UseExperimental(ExperimentalCoroutinesApi::class)
    val devices: LiveData<List<Device>>
        get() = _devices

    init {
        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().getSharedPreferences(
                SP_NAME_DEVICES,
                Context.MODE_PRIVATE
            ).run {
                Timber.d("Devices loading begins")
                val macSet = getStringSet("all_mac", setOf())!!
                Timber.d("Devices amount: %s", macSet.size)
                val res = MutableList<Device?>(macSet.size) { null }
                for (mac in macSet) {
                    getInt("index_$mac", -1)
                        .takeUnless { it == -1 }
                        ?.let {
                            val key =
                                getString("key_$mac", null) ?: throw RuntimeException()
                            val name =
                                getString("name_$mac", null) ?: throw RuntimeException()
                            res[it] = Device(mac, key, name)
                            Timber.d("Load device { mac: %s, key: %s, name: %s }", mac, key, name)
                        }
                }
                _devices.postValue(res.filterNotNull())
                Timber.d("Devices loading ends")
            }
        }


    }

    private fun storeDevices(devices: List<Device>) = viewModelScope.launch(Dispatchers.IO) {
        getApplication<Application>().getSharedPreferences(
            SP_NAME_DEVICES,
            Context.MODE_PRIVATE
        ).edit {
            Timber.d("Devices storing begins")
            clear()
            val macSet = mutableSetOf<String>()
            devices.forEachIndexed { i, device ->
                macSet.add(device.mac)
                putInt("index_${device.mac}", i)
                putString("name_${device.mac}", device.name)
                putString("key_${device.mac}", device.key)
                Timber.d(
                    "Put device { mac: %s, key: %s, name: %s }",
                    device.mac,
                    device.key,
                    device.name
                )
            }
            putStringSet("all_mac", macSet)
            Timber.d("Devices storing ends")
        }
    }

    fun onNewDevice(device: Device) {
        Timber.d("onNewDevice { mac: %s, key: %s, name: %s }", device.mac, device.key, device.name)
        viewModelScope.launch(Dispatchers.Default) {
            val twin = _devices.value?.toMutableList() ?: mutableListOf()
            twin.indexOfFirst { it.mac == device.mac }
                .takeUnless { it == -1 }
                ?.let {
                    val newDevice = twin[it].copy(key = device.key)
                    twin[it] = twin[0]
                    twin[0] = newDevice
                }
                ?: twin.add(0, device)
            _devices.postValue(twin)
            storeDevices(twin)
        }
    }

    fun updateDeviceName(mac: String, newName: String) {
        Timber.d("updateDeviceName { mac: %s, new_name: %s }", mac, newName)
        viewModelScope.launch(Dispatchers.Default) {
            val twin = _devices.value?.toMutableList() ?: mutableListOf()
            twin.indexOfFirst { it.mac == mac }
                .takeUnless { it == -1 }
                ?.let {
                    val newDevice = twin[it].copy(name = newName)
                    twin[it] = newDevice
                    _devices.postValue(twin)
                    storeDevices(twin)
                }
        }
    }
}
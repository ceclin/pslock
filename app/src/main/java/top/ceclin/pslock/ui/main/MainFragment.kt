package top.ceclin.pslock.ui.main

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import kotlinx.android.synthetic.main.history_list_item.view.*
import kotlinx.android.synthetic.main.main_fragment.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okio.ByteString
import timber.log.Timber
import top.ceclin.pslock.R
import top.ceclin.pslock.bt.Bluetooth
import top.ceclin.pslock.bt.SPP_SERVICE_UUID
import top.ceclin.pslock.bt.bondStateFlow
import top.ceclin.pslock.bt.discoveredDeviceFlow
import top.ceclin.pslock.model.Device
import top.ceclin.pslock.model.QRCode
import top.ceclin.pslock.ui.base.BaseFragment
import top.ceclin.pslock.ui.clickEventFlow
import top.ceclin.pslock.widget.DeviceEditorDialog
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

class MainFragment : BaseFragment() {

    companion object {

        fun newInstance() = MainFragment()

        private const val REQUEST_CODE_ENABLE_BLUETOOTH = 0x00005060
    }

    private val viewModel: MainViewModel by viewModels()

    private val bluetooth = Bluetooth()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        device_history.apply {
            val linearLayoutManager = LinearLayoutManager(context)
            layoutManager = linearLayoutManager
            adapter = DeviceListAdapter().apply {
                onItemClick = {
                    viewModel.onNewTask(it.device_mac.text.toString())
                }
                onItemLongClick = { view ->
                    val mac = view.device_mac.text.toString()
                    Timber.i("Long click occurred and opening editor for %s", mac)
                    DeviceEditorDialog.newInstance(view.device_name.text.toString()) {
                        viewModel.updateDeviceName(mac, it)
                    }.show(requireActivity().supportFragmentManager, "device_editor")
                    false
                }
            }
            addItemDecoration(DividerItemDecoration(context, linearLayoutManager.orientation))
        }
    }

    @UseExperimental(ExperimentalCoroutinesApi::class)
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel.devices.observe(viewLifecycleOwner, Observer {
            (device_history.adapter as DeviceListAdapter?)?.submitList(it)
        })

        fab.clickEventFlow()
            .map {
                activityResultAsync(IntentIntegrator.REQUEST_CODE) {
                    IntentIntegrator.forSupportFragment(this)
                        .setOrientationLocked(false)
                        .setBeepEnabled(false)
                        .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                        .initiateScan()
                }.await()
            }
            .flowOn(Dispatchers.Main)
            .map {
                IntentIntegrator.parseActivityResult(it.resultCode, it.data)
            }.transform {
                if (it.contents != null) {
                    emit(it)
                } else {
                    Timber.i("User cancelled the QR code scan")
                    showShortToast("您取消了二维码扫描")
                }
            }.transform<IntentResult, QRCode> {
                val qr = QRCode.parse(it.contents)
                if (qr != null) {
                    showLongToast("扫描成功 正在处理中...")
                    emit(qr)
                } else {
                    Timber.i("Scan result is unsupported")
                    showLongToast("二维码内容为不支持的格式")
                }
            }
            .flowOn(Dispatchers.Default)
            .transform {
                if (bluetooth.disabled) {
                    val result = activityResultAsync(
                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                        REQUEST_CODE_ENABLE_BLUETOOTH
                    ).await()
                    if (result.resultCode != Activity.RESULT_OK) {
                        return@transform
                    }
                }
                emit(it)
            }
            .flowOn(Dispatchers.Main)
            .onEach { qr ->
                val device = bluetooth.bondedDevices.firstOrNull {
                    it.address == qr.mac
                } ?: run {
                    bluetooth.startDiscovery()
                    requireContext().discoveredDeviceFlow()
                        .filter { it.address == qr.mac }
                        .take(1)
                        .singleOrNull().also {
                            bluetooth.cancelDiscovery()
                        }
                }
                ?: run {
                    Timber.d("Bluetooth device was not found")
                    showShortToast("无法找到对应的蓝牙设备")
                    return@onEach
                }

                val initialBonded = device.bondState == BluetoothDevice.BOND_BONDED
                if (!initialBonded) {
                    Timber.i("Creating bond with remote device")
                    if (Class.forName("android.bluetooth.BluetoothDevice")
                            .getMethod("createBond").invoke(device) as Boolean
                    ) {
                        requireContext().bondStateFlow()
                            .filter {
                                it.device.address == device.address &&
                                        it.previous == BluetoothDevice.BOND_BONDING
                            }.take(1)
                            .singleOrNull()
                            ?.takeIf {
                                it.current == BluetoothDevice.BOND_BONDED
                            } ?: return@onEach
                    } else {
                        return@onEach
                    }
                    Timber.i("Bond created")
                    showLongToast("配对成功 正在连接中...")
                }
                val dialog = withContext(Dispatchers.Main) {
                    android.app.ProgressDialog.show(
                        requireContext(), "正在连接中",
                        if (initialBonded) "稍等，即将完成" else "初次连接需要较长时间",
                        true, false
                    )
                }
                try {
                    val socket =
                        device.createRfcommSocketToServiceRecord(UUID.fromString(SPP_SERVICE_UUID))
                    bluetooth.cancelDiscovery()
                    socket.use {
                        Timber.i("Connecting to bluetooth device")
                        it.connect()
                        Timber.i("Connected to bluetooth device")
                        Timber.i("Sending auth key to bluetooth device")
                        it.outputStream.write(ByteString.decodeBase64(qr.key)!!.toByteArray())
                        Timber.i("Auth key is sent and listening for response")
                        val keyHex = withTimeoutOrNull(300) {
                            val buffer = ByteBuffer.allocate(32)
                            while (true) {
                                val available = it.inputStream.available()
                                if (available > 0) {
                                    val bytes = ByteArray(available)
                                    it.inputStream.read(bytes)
                                    // TODO
                                    Timber.d("Received: %s", bytes.joinToString())
                                    buffer.put(bytes)
                                    if (bytes.last() == 32.toByte()) {
                                        break
                                    }
                                }
                                delay(5)
                            }
                            buffer.flip()
                            ByteString.of(buffer).hex()
                        } ?: throw IOException("Timeout during listening for response")
                        Timber.i("Received key from bluetooth: %s", keyHex)
                        showShortToast("获取授权成功")
                        viewModel.onNewDevice(Device(device.address, keyHex, "无名锁"))
                    }
                } catch (e: IOException) {
                    Timber.w(e, "Bluetooth IO failed")
                    showShortToast("无法连接到蓝牙设备")
                }
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.currentTask.observe(viewLifecycleOwner, Observer { device ->
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                if (bluetooth.disabled) {
                    val result = withContext(Dispatchers.Main) {
                        activityResultAsync(
                            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                            REQUEST_CODE_ENABLE_BLUETOOTH
                        )
                    }.await()
                    if (result.resultCode != Activity.RESULT_OK) {
                        return@launch
                    }
                }
                showLongToast("正在处理中...")
                val bluetoothDevice = bluetooth.bondedDevices.firstOrNull {
                    it.address == device.mac
                } ?: run {
                    bluetooth.startDiscovery()
                    requireContext().discoveredDeviceFlow()
                        .filter { it.address == device.mac }
                        .take(1)
                        .singleOrNull().also {
                            bluetooth.cancelDiscovery()
                        }
                }
                ?: run {
                    Timber.d("Bluetooth device was not found")
                    showShortToast("无法找到对应的蓝牙设备")
                    return@launch
                }

                val initialBonded = bluetoothDevice.bondState == BluetoothDevice.BOND_BONDED
                if (!initialBonded) {
                    Timber.i("Creating bond with remote device")
                    if (Class.forName("android.bluetooth.BluetoothDevice")
                            .getMethod("createBond").invoke(bluetoothDevice) as Boolean
                    ) {
                        requireContext().bondStateFlow()
                            .filter {
                                it.device.address == bluetoothDevice.address &&
                                        it.previous == BluetoothDevice.BOND_BONDING
                            }.take(1)
                            .singleOrNull()
                            ?.takeIf {
                                it.current == BluetoothDevice.BOND_BONDED
                            } ?: return@launch
                    } else {
                        return@launch
                    }
                    Timber.i("Bond created")
                    showLongToast("配对成功 正在连接中...")
                }

                val dialog = withContext(Dispatchers.Main) {
                    android.app.ProgressDialog.show(
                        requireContext(), "正在连接中",
                        if (initialBonded) "稍等，即将完成" else "初次连接需要较长时间",
                        true, false
                    )
                }
                try {
                    val socket =
                        bluetoothDevice.createRfcommSocketToServiceRecord(
                            UUID.fromString(SPP_SERVICE_UUID)
                        )
                    bluetooth.cancelDiscovery()
                    socket.use {
                        Timber.i("Connecting to bluetooth device")
                        it.connect()
                        Timber.i("Connected to bluetooth device")
                        Timber.i("Sending device key to bluetooth device")
                        it.outputStream.write(ByteString.decodeHex(device.key).toByteArray())
                        Timber.i("Device key is sent and listening for response")
                        val hex = withTimeoutOrNull(200) {
                            val buffer = ByteBuffer.allocate(16)
                            while (true) {
                                val available = it.inputStream.available()
                                if (available > 0) {
                                    val bytes = ByteArray(available)
                                    it.inputStream.read(bytes)
                                    Timber.d("Received: %s", bytes.joinToString())
                                    buffer.put(bytes)
                                    if (bytes.last() == 32.toByte()) {
                                        break
                                    }
                                }
                                delay(5)
                            }
                            buffer.flip()
                            ByteString.of(buffer).hex()
                        } ?: throw IOException("Timeout during listening for response")
                        if (hex != "20") {
                            throw IOException("Unexpected response hex: $hex")
                        }
                        Timber.i("Success response")
                        showShortToast("开锁成功")
                    }
                } catch (e: IOException) {
                    Timber.w(e, "Failed to open remote lock")
                    showShortToast("开锁失败")
                }
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                }
            }.invokeOnCompletion {
                viewModel.onTaskCompleted()
            }
        })
    }

    private fun showLongToast(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showShortToast(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}
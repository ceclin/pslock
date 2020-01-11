package top.ceclin.pslock.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import top.ceclin.pslock.R
import top.ceclin.pslock.bt.Bluetooth
import top.ceclin.pslock.bt.isLocationPermissionDenied
import top.ceclin.pslock.ui.main.MainFragment
import top.ceclin.pslock.widget.FatalErrorDialog

class MainActivity : AppCompatActivity() {

    companion object {

        private const val REQUEST_CODE_COARSE_LOCATION = 0x00000506
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        if (Bluetooth().unavailable) {
            FatalErrorDialog.newInstance("您的设备不支持蓝牙功能").show(
                supportFragmentManager,
                "bluetooth_unavailable"
            )
            return
        }
        if (isLocationPermissionDenied()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_CODE_COARSE_LOCATION
            )
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, MainFragment.newInstance())
            .commitNow()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_COARSE_LOCATION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    applicationContext,
                    "蓝牙设备扫描需要获取位置权限",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}

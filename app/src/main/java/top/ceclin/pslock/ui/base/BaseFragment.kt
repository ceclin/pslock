package top.ceclin.pslock.ui.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel

abstract class BaseFragment : Fragment() {

    private val viewModel: BaseViewModel by viewModels()

    protected fun activityResultAsync(
        intent: Intent?,
        requestCode: Int,
        options: Bundle? = null
    ): Deferred<ActivityResult> =
        if (requestCode in 0..0xffff) {
            try {
                startActivityForResult(intent, requestCode, options)
                CompletableDeferred<ActivityResult>().also {
                    viewModel.activityResultDeferredMap[requestCode] = it
                }
            } catch (e: Exception) {
                CompletableDeferred<ActivityResult>().apply { completeExceptionally(e) }
            }
        } else
            CompletableDeferred<ActivityResult>().apply {
                completeExceptionally(
                    IllegalArgumentException("Can only use lower 16 bits for requestCode")
                )
            }

    protected fun activityResultAsync(
        requestCode: Int,
        sendRequest: () -> Unit
    ): Deferred<ActivityResult> =
        if (requestCode in 0..0xffff) {
            try {
                sendRequest()
                CompletableDeferred<ActivityResult>().also {
                    viewModel.activityResultDeferredMap[requestCode] = it
                }
            } catch (e: Exception) {
                CompletableDeferred<ActivityResult>().apply { completeExceptionally(e) }
            }
        } else
            CompletableDeferred<ActivityResult>().apply {
                completeExceptionally(
                    IllegalArgumentException("Can only use lower 16 bits for requestCode")
                )
            }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        viewModel.activityResultDeferredMap.remove(requestCode)?.complete(
            ActivityResult(resultCode, data)
        ) ?: super.onActivityResult(requestCode, resultCode, data)
    }

    @UseExperimental(ExperimentalCoroutinesApi::class)
    protected fun broadcastChannel(
        intentFilter: IntentFilter
    ) = Channel<Intent?>().apply {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                offer(intent)
            }
        }.also {
            requireContext().registerReceiver(it, intentFilter)
        }
        invokeOnClose {
            requireContext().unregisterReceiver(receiver)
        }
    }

    // TODO: use new version in newborn project
    protected fun checkPermissions(permissions: Array<out String>) = permissions.map {
        PermissionResult(
            it, ContextCompat.checkSelfPermission(
                requireContext(), it
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    protected fun requestPermissionsAsync(
        requestCode: Int,
        permissions: Array<out String>
    ): Deferred<List<PermissionResult>> =
        try {
            requestPermissions(permissions, requestCode)
            CompletableDeferred<List<PermissionResult>>().also {
                viewModel.permissionResultDeferredMap[requestCode] = it
            }
        } catch (e: Exception) {
            CompletableDeferred<List<PermissionResult>>().apply {
                completeExceptionally(e)
            }
        }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        viewModel.permissionResultDeferredMap.remove(requestCode)?.let { deferred ->
            deferred.complete(permissions.mapIndexed { i, p ->
                PermissionResult(
                    p, grantResults.isNotEmpty()
                            && grantResults[i] == PackageManager.PERMISSION_GRANTED
                )
            })
        } ?: super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
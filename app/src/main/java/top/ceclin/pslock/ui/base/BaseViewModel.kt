package top.ceclin.pslock.ui.base

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CompletableDeferred

class BaseViewModel : ViewModel() {
    internal val activityResultDeferredMap =
        mutableMapOf<Int, CompletableDeferred<ActivityResult>>()
    internal val permissionResultDeferredMap =
        mutableMapOf<Int, CompletableDeferred<List<PermissionResult>>>()
}
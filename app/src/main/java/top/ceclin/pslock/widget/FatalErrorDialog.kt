package top.ceclin.pslock.widget

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import timber.log.Timber

class FatalErrorDialog : DialogFragment() {
    companion object {
        fun newInstance(message: String) = FatalErrorDialog().apply {
            arguments = Bundle().apply {
                putString("message", message)
            }
            isCancelable = false
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val message = arguments?.getString("message") ?: "发生未知的严重错误"
        Timber.i("FatalErrorDialog creating with message: %s", message)
        return activity?.let {
            MaterialAlertDialogBuilder(
                it, R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog
            )
                .setMessage(arguments?.getString("message") ?: "发生未知的严重错误")
                .setPositiveButton("确定") { _, _ ->
                    Timber.v("User clicked the button and activity finishing")
                    it.finish()
                }
                .create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}
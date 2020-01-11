package top.ceclin.pslock.widget

import android.app.Dialog
import android.os.Bundle
import android.util.TypedValue
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import com.google.android.material.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class DeviceEditorDialog : DialogFragment() {
    companion object {

        fun newInstance(deviceName: String, afterEditCallback: ((String) -> Unit)? = null) =
            DeviceEditorDialog().apply {
                arguments = Bundle().apply {
                    putString("device_name", deviceName)
                }
                isCancelable = false
                afterEdit = afterEditCallback
            }
    }

    private lateinit var textInputEditText: TextInputEditText

    var afterEdit: ((String) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog_Centered
        )
            .setTitle("修改名称")
            .setView(
                TextInputLayout(
                    requireContext(), null,
                    R.style.Widget_MaterialComponents_TextInputLayout_FilledBox
                ).apply {
                    fun px(dp: Int) =
                        TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
                        ).toInt()
                    endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
                    val px16 = px(16)
                    updatePadding(left = px16, right = px16)
                    addView(
                        TextInputEditText(context).apply {
                            text?.append(arguments?.getString("device_name", null) ?: "")
                        }.also { textInputEditText = it }
                    )
                })
            .setPositiveButton("确定") { _, _ ->
                afterEdit?.invoke(textInputEditText.text.toString())
            }
            .setNegativeButton("取消", null)
            .create()
    }
}
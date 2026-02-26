package com.peiyu.reader.ui.association

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import com.peiyu.reader.R
import com.peiyu.reader.base.BaseDialogFragment
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.databinding.DialogOpenUrlConfirmBinding
import com.peiyu.reader.lib.dialogs.alert
import com.peiyu.reader.lib.theme.primaryColor
import com.peiyu.reader.utils.applyTint
import com.peiyu.reader.utils.setLayout
import com.peiyu.reader.utils.toastOnUi
import com.peiyu.reader.utils.viewbindingdelegate.viewBinding
import splitties.init.appCtx

class OpenUrlConfirmDialog() : BaseDialogFragment(R.layout.dialog_open_url_confirm),
    Toolbar.OnMenuItemClickListener {

    constructor(
        uri: String,
        mimeType: String?,
        sourceOrigin: String? = null,
        sourceName: String? = null,
        sourceType: Int
    ) : this() {
        arguments = Bundle().apply {
            putString("uri", uri)
            putString("mimeType", mimeType)
            putString("sourceOrigin", sourceOrigin)
            putString("sourceName", sourceName)
            putInt("sourceType", sourceType)
        }
    }

    val binding by viewBinding(DialogOpenUrlConfirmBinding::bind)
    val viewModel by viewModels<OpenUrlConfirmViewModel>()

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initMenu()
        val arguments = arguments ?: return
        viewModel.initData(arguments)
        if (viewModel.uri.isBlank()) {
            dismiss()
            return
        }
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.subtitle = viewModel.sourceName
        initView()
    }

    private fun initMenu() {
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.inflateMenu(R.menu.open_url_confirm)
        binding.toolBar.menu.applyTint(requireContext())
    }

    private fun initView() {
        binding.message.text = "${viewModel.sourceName} 正在请求跳转链接/应用，是否跳转？"
        binding.btnNegative.setOnClickListener { dismiss() }
        binding.btnPositive.setOnClickListener {
            openUrl()
            dismiss()
        }
    }

    private fun openUrl() {
        try {
            val uri = viewModel.uri.toUri()
            val mimeType = viewModel.mimeType
            // 创建目标 Intent 并设置类�?            val targetIntent = Intent(Intent.ACTION_VIEW).apply {
                // 同时设置 Data �?Type
                if (!mimeType.isNullOrBlank()) {
                    setDataAndType(uri, mimeType)
                } else {
                    data = uri
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // 验证是否有应用可以处�?            if (targetIntent.resolveActivity(appCtx.packageManager) != null) {
                startActivity(targetIntent)
            } else {
                toastOnUi(R.string.can_not_open)
            }
        } catch (e: Exception) {
            AppLog.put("打开链接失败", e, true)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_disable_source -> {
                viewModel.disableSource {
                    dismiss()
                }
            }

            R.id.menu_delete_source -> {
                alert(R.string.draw) {
                    setMessage(getString(R.string.sure_del) + "\n" + viewModel.sourceName)
                    noButton()
                    yesButton {
                        viewModel.deleteSource {
                            dismiss()
                        }
                    }
                }
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.finish()
    }

}

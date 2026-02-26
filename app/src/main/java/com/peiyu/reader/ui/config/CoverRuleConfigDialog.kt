package com.peiyu.reader.ui.config

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.peiyu.reader.R
import com.peiyu.reader.base.BaseDialogFragment
import com.peiyu.reader.databinding.DialogCoverRuleConfigBinding
import com.peiyu.reader.lib.theme.primaryColor
import com.peiyu.reader.model.BookCover
import com.peiyu.reader.utils.GSON
import com.peiyu.reader.utils.setLayout
import com.peiyu.reader.utils.toastOnUi
import com.peiyu.reader.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.onClick

class CoverRuleConfigDialog : BaseDialogFragment(R.layout.dialog_cover_rule_config) {

    val binding by viewBinding(DialogCoverRuleConfigBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        initData()
        binding.tvCancel.onClick {
            dismissAllowingStateLoss()
        }
        binding.tvOk.onClick {
            val enable = binding.cbEnable.isChecked
            val searchUrl = binding.editSearchUrl.text?.toString()
            val coverRule = binding.editCoverUrlRule.text?.toString()
            if (searchUrl.isNullOrBlank() || coverRule.isNullOrBlank()) {
                toastOnUi("搜索url和cover规则不能为空")
            } else {
                BookCover.CoverRule(enable, searchUrl, coverRule).let { config ->
                    BookCover.saveCoverRule(config)
                }
                dismissAllowingStateLoss()
            }
        }
        binding.tvFooterLeft.onClick {
            BookCover.delCoverRule()
            dismissAllowingStateLoss()
        }
    }

    private fun initData() {
        lifecycleScope.launch {
            val rule = withContext(IO) {
                BookCover.getCoverRule()
            }
            Log.e("coverRule", GSON.toJson(rule))
            binding.cbEnable.isChecked = rule.enable
            binding.editSearchUrl.setText(rule.searchUrl)
            binding.editCoverUrlRule.setText(rule.coverRule)
        }
    }

}

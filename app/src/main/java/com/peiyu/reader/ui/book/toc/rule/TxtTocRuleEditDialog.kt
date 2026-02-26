package com.peiyu.reader.ui.book.toc.rule

import android.app.Application
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import com.peiyu.reader.R
import com.peiyu.reader.base.BaseDialogFragment
import com.peiyu.reader.base.BaseViewModel
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.data.appDb
import com.peiyu.reader.data.entities.TxtTocRule
import com.peiyu.reader.databinding.DialogTocRegexEditBinding
import com.peiyu.reader.exception.NoStackTraceException
import com.peiyu.reader.lib.theme.primaryColor
import com.peiyu.reader.utils.*
import com.peiyu.reader.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class TxtTocRuleEditDialog() : BaseDialogFragment(R.layout.dialog_toc_regex_edit, true),
    Toolbar.OnMenuItemClickListener {

    constructor(id: Long?) : this() {
        id ?: return
        arguments = Bundle().apply {
            putLong("id", id)
        }
    }

    private val binding by viewBinding(DialogTocRegexEditBinding::bind)
    private val viewModel by viewModels<ViewModel>()
    private val callback get() = (parentFragment as? Callback) ?: activity as? Callback

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        initMenu()
        viewModel.initData(arguments?.getLong("id")) {
            upRuleView(it)
        }
    }

    private fun initMenu() {
        binding.toolBar.inflateMenu(R.menu.txt_toc_rule_edit)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_save -> {
                val tocRule = getRuleFromView()
                if (checkValid(tocRule)) {
                    callback?.saveTxtTocRule(getRuleFromView())
                    dismissAllowingStateLoss()
                }
            }
            R.id.menu_copy_rule -> context?.sendToClip(GSON.toJson(getRuleFromView()))
            R.id.menu_paste_rule -> viewModel.pasteRule {
                upRuleView(it)
            }
        }
        return true
    }

    private fun checkValid(tocRule: TxtTocRule): Boolean {
        if (tocRule.name.isEmpty()) {
            toastOnUi("名称不能为空")
            return false
        }

        try {
            Pattern.compile(tocRule.rule, Pattern.MULTILINE)
        } catch (ex: PatternSyntaxException) {
            AppLog.put("正则语法错误或不支持(txt)�?{ex.localizedMessage}", ex, true)
            return false
        }

        return true
    }

    private fun upRuleView(tocRule: TxtTocRule?) {
        binding.tvRuleName.setText(tocRule?.name)
        binding.tvRuleRegex.setText(tocRule?.rule)
        binding.tvRuleExample.setText(tocRule?.example)
    }

    private fun getRuleFromView(): TxtTocRule {
        val tocRule = viewModel.tocRule ?: TxtTocRule().apply {
            viewModel.tocRule = this
        }
        binding.run {
            tocRule.name = tvRuleName.text.toString()
            tocRule.rule = tvRuleRegex.text.toString()
            tocRule.example = tvRuleExample.text.toString()
        }
        return tocRule
    }

    class ViewModel(application: Application) : BaseViewModel(application) {

        var tocRule: TxtTocRule? = null

        fun initData(id: Long?, finally: (tocRule: TxtTocRule?) -> Unit) {
            if (tocRule != null) return
            execute {
                if (id == null) return@execute
                tocRule = appDb.txtTocRuleDao.get(id)
            }.onFinally {
                finally.invoke(tocRule)
            }
        }

        fun pasteRule(success: (TxtTocRule) -> Unit) {
            execute(context = Dispatchers.Main) {
                val text = context.getClipText()
                if (text.isNullOrBlank()) {
                    throw NoStackTraceException("剪贴板为�?)
                }
                GSON.fromJsonObject<TxtTocRule>(text).getOrNull()
                    ?: throw NoStackTraceException("格式不对")
            }.onSuccess {
                success.invoke(it)
            }.onError {
                context.toastOnUi(it.localizedMessage ?: "Error")
                it.printOnDebug()
            }
        }

    }

    interface Callback {

        fun saveTxtTocRule(txtTocRule: TxtTocRule)

    }

}

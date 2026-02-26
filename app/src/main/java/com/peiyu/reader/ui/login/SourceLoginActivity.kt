package com.peiyu.reader.ui.login

import android.os.Bundle
import androidx.activity.viewModels
import com.peiyu.reader.R
import com.peiyu.reader.base.VMBaseActivity
import com.peiyu.reader.data.entities.BaseSource
import com.peiyu.reader.databinding.ActivitySourceLoginBinding
import com.peiyu.reader.utils.showDialogFragment
import com.peiyu.reader.utils.viewbindingdelegate.viewBinding


class SourceLoginActivity : VMBaseActivity<ActivitySourceLoginBinding, SourceLoginViewModel>() {

    override val binding by viewBinding(ActivitySourceLoginBinding::inflate)
    override val viewModel by viewModels<SourceLoginViewModel>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        viewModel.initData(intent, success = { source ->
            initView(source)
        }, error = {
            finish()
        })
    }

    private fun initView(source: BaseSource) {
        if (source.loginUi.isNullOrEmpty()) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fl_fragment, WebViewLoginFragment(), "webViewLogin")
                .commit()
        } else {
            showDialogFragment<SourceLoginDialog>()
        }
    }

}

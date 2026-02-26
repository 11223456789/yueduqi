package com.peiyu.reader.ui.association

import android.os.Bundle
import com.peiyu.reader.base.BaseActivity
import com.peiyu.reader.constant.SourceType
import com.peiyu.reader.databinding.ActivityTranslucenceBinding
import com.peiyu.reader.utils.showDialogFragment
import com.peiyu.reader.utils.viewbindingdelegate.viewBinding

/**
 * 验证�? */
class VerificationCodeActivity :
    BaseActivity<ActivityTranslucenceBinding>() {

    override val binding by viewBinding(ActivityTranslucenceBinding::inflate)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        intent.getStringExtra("imageUrl")?.let {
            val sourceOrigin = intent.getStringExtra("sourceOrigin")
            val sourceName = intent.getStringExtra("sourceName")
            val sourceType = intent.getIntExtra("sourceType", SourceType.book)
            showDialogFragment(
                VerificationCodeDialog(it, sourceOrigin, sourceName, sourceType)
            )
        } ?: finish()
    }

}

package com.peiyu.reader.ui.widget.keyboard

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.widget.PopupWindow
import com.peiyu.reader.R
import com.peiyu.reader.base.adapter.ItemViewHolder
import com.peiyu.reader.base.adapter.RecyclerAdapter
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.data.appDb
import com.peiyu.reader.data.entities.KeyboardAssist
import com.peiyu.reader.databinding.ItemFilletTextBinding
import com.peiyu.reader.databinding.PopupKeyboardToolBinding
import com.peiyu.reader.lib.dialogs.SelectItem
import com.peiyu.reader.lib.dialogs.selector
import com.peiyu.reader.utils.activity
import com.peiyu.reader.utils.showDialogFragment
import com.peiyu.reader.utils.windowSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import splitties.systemservices.layoutInflater
import splitties.systemservices.windowManager
import kotlin.math.abs

/**
 * 键盘帮助浮窗
 */
class KeyboardToolPop(
    private val context: Context,
    private val scope: CoroutineScope,
    private val rootView: View,
    private val callBack: CallBack
) : PopupWindow(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    ViewTreeObserver.OnGlobalLayoutListener {

    private val helpChar = "�?

    private val binding = PopupKeyboardToolBinding.inflate(LayoutInflater.from(context))
    private val adapter = Adapter(context)
    private var mIsSoftKeyBoardShowing = false
    var initialPadding = 0

    init {
        contentView = binding.root

        isTouchable = true
        isOutsideTouchable = false
        isFocusable = false
        inputMethodMode = INPUT_METHOD_NEEDED //解决遮盖输入�?        initRecyclerView()
        upAdapterData()
    }

    fun attachToWindow(window: Window) {
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener(this)
        contentView.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED,
        )
    }

    override fun onGlobalLayout() {
        val rect = Rect()
        // 获取当前页面窗口的显示范�?        rootView.getWindowVisibleDisplayFrame(rect)
        val screenHeight = windowManager.windowSize.heightPixels
        val keyboardHeight = screenHeight - rect.bottom // 输入法的高度
        val preShowing = mIsSoftKeyBoardShowing
        if (abs(keyboardHeight) > screenHeight / 5) {
            mIsSoftKeyBoardShowing = true // 超过屏幕五分之一则表示弹出了输入�?            rootView.setPadding(0, 0, 0, initialPadding + contentView.measuredHeight)
            if (!isShowing) {
                showAtLocation(rootView, Gravity.BOTTOM, 0, 0)
            }
        } else {
            mIsSoftKeyBoardShowing = false
            rootView.setPadding(0, 0, 0, 0)
            if (preShowing) {
                dismiss()
            }
        }
    }

    private fun initRecyclerView() {
        binding.recyclerView.adapter = adapter
        adapter.addHeaderView {
            ItemFilletTextBinding.inflate(context.layoutInflater, it, false).apply {
                textView.text = helpChar
                root.setOnClickListener {
                    helpAlert()
                }
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun upAdapterData() {
        scope.launch {
            appDb.keyboardAssistsDao.flowByType(0).catch {
                AppLog.put("键盘帮助浮窗获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).collect {
                adapter.setItems(it)
            }
        }
    }

    private fun helpAlert() {
        val items = arrayListOf(
            SelectItem(context.getString(R.string.assists_key_config), "keyConfig")
        )
        items.addAll(callBack.helpActions())
        context.selector(context.getString(R.string.help), items) { _, selectItem, _ ->
            when (selectItem.value) {
                "keyConfig" -> config()
                else -> callBack.onHelpActionSelect(selectItem.value)
            }
        }
    }

    private fun config() {
        contentView.activity?.showDialogFragment<KeyboardAssistsConfig>()
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<KeyboardAssist, ItemFilletTextBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemFilletTextBinding {
            return ItemFilletTextBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemFilletTextBinding,
            item: KeyboardAssist,
            payloads: MutableList<Any>
        ) {
            binding.run {
                textView.text = item.key
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemFilletTextBinding) {
            holder.itemView.apply {
                setOnClickListener {
                    getItemByLayoutPosition(holder.layoutPosition)?.let {
                        callBack.sendText(it.value)
                    }
                }
            }
        }
    }

    interface CallBack {

        fun helpActions(): List<SelectItem<String>> = arrayListOf()

        fun onHelpActionSelect(action: String)

        fun sendText(text: String)

    }

}

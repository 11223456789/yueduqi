package com.peiyu.reader.ui.book.group

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.peiyu.reader.R
import com.peiyu.reader.base.BaseDialogFragment
import com.peiyu.reader.base.adapter.ItemViewHolder
import com.peiyu.reader.base.adapter.RecyclerAdapter
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.data.appDb
import com.peiyu.reader.data.entities.BookGroup
import com.peiyu.reader.databinding.DialogRecyclerViewBinding
import com.peiyu.reader.databinding.ItemBookGroupManageBinding
import com.peiyu.reader.lib.theme.accentColor
import com.peiyu.reader.lib.theme.backgroundColor
import com.peiyu.reader.lib.theme.primaryColor
import com.peiyu.reader.ui.widget.recycler.ItemTouchCallback
import com.peiyu.reader.ui.widget.recycler.VerticalDivider
import com.peiyu.reader.utils.applyTint
import com.peiyu.reader.utils.setLayout
import com.peiyu.reader.utils.showDialogFragment
import com.peiyu.reader.utils.toastOnUi
import com.peiyu.reader.utils.viewbindingdelegate.viewBinding
import com.peiyu.reader.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 书籍分组管理
 */
class GroupManageDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val viewModel: GroupViewModel by viewModels()
    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { GroupAdapter(requireContext()) }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.group_manage)
        initView()
        initData()
        initMenu()
    }

    private fun initView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
        val itemTouchCallback = ItemTouchCallback(adapter)
        itemTouchCallback.isCanDrag = true
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
        binding.tvOk.setTextColor(requireContext().accentColor)
        binding.tvOk.visible()
        binding.tvOk.setOnClickListener {
            dismissAllowingStateLoss()
        }
    }

    private fun initData() {
        lifecycleScope.launch {
            appDb.bookGroupDao.flowAll().catch {
                AppLog.put("书籍分组管理界面获取分组数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect {
                adapter.setItems(it)
            }
        }
    }

    private fun initMenu() {
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.inflateMenu(R.menu.book_group_manage)
        binding.toolBar.menu.applyTint(requireContext())
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_add -> {
                if (appDb.bookGroupDao.canAddGroup) {
                    showDialogFragment(GroupEditDialog())
                } else {
                    toastOnUi("分组已达上限(64�?")
                }
            }
        }
        return true
    }

    private inner class GroupAdapter(context: Context) :
        RecyclerAdapter<BookGroup, ItemBookGroupManageBinding>(context),
        ItemTouchCallback.Callback {

        private var isMoved = false

        override fun getViewBinding(parent: ViewGroup): ItemBookGroupManageBinding {
            return ItemBookGroupManageBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemBookGroupManageBinding,
            item: BookGroup,
            payloads: MutableList<Any>
        ) {
            binding.run {
                root.setBackgroundColor(context.backgroundColor)
                tvGroup.text = item.getManageName(context)
                swShow.isChecked = item.show
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemBookGroupManageBinding) {
            binding.run {
                tvEdit.setOnClickListener {
                    getItem(holder.layoutPosition)?.let { bookGroup ->
                        showDialogFragment(
                            GroupEditDialog(bookGroup)
                        )
                    }
                }
                swShow.setOnUserCheckedChangeListener { isChecked ->
                    getItem(holder.layoutPosition)?.let {
                        viewModel.upGroup(it.copy(show = isChecked))
                    }
                }
            }
        }

        override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
            swapItem(srcPosition, targetPosition)
            isMoved = true
            return true
        }

        override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            if (isMoved) {
                for ((index, item) in getItems().withIndex()) {
                    item.order = index + 1
                }
                viewModel.upGroup(*getItems().toTypedArray())
            }
            isMoved = false
        }
    }

}

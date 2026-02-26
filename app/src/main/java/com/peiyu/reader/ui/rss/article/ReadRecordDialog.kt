package com.peiyu.reader.ui.rss.article

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.peiyu.reader.R
import com.peiyu.reader.base.BaseDialogFragment
import com.peiyu.reader.base.adapter.ItemViewHolder
import com.peiyu.reader.base.adapter.RecyclerAdapter
import com.peiyu.reader.data.entities.RssReadRecord
import com.peiyu.reader.databinding.DialogRecyclerViewBinding
import com.peiyu.reader.databinding.ItemRssReadRecordBinding
import com.peiyu.reader.lib.dialogs.alert
import com.peiyu.reader.lib.theme.primaryColor
import com.peiyu.reader.utils.setLayout
import com.peiyu.reader.utils.viewbindingdelegate.viewBinding

class ReadRecordDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val viewModel by viewModels<RssSortViewModel>()
    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy {
        ReadRecordAdapter(requireContext())
    }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.run {
            toolBar.setBackgroundColor(primaryColor)
            toolBar.setTitle(R.string.read_record)
            toolBar.inflateMenu(R.menu.rss_read_record)
            toolBar.setOnMenuItemClickListener(this@ReadRecordDialog)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
        }
        adapter.setItems(viewModel.getRecords())
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_clear -> {
                alert(R.string.draw) {
                    val countRead = viewModel.countRecords()
                    setMessage(getString(R.string.sure_del) + "\n" + countRead + " " + getString(R.string.read_record))
                    noButton()
                    yesButton {
                        viewModel.deleteAllRecord()
                        adapter.clearItems()
                    }
                }
            }
        }
        return true
    }

    inner class ReadRecordAdapter(context: Context) :
        RecyclerAdapter<RssReadRecord, ItemRssReadRecordBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemRssReadRecordBinding {
            return ItemRssReadRecordBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemRssReadRecordBinding,
            item: RssReadRecord,
            payloads: MutableList<Any>
        ) {
            binding.textTitle.text = item.title
            binding.textRecord.text = item.record
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemRssReadRecordBinding) {
        }

    }

}

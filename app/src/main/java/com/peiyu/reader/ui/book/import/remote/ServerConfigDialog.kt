package com.peiyu.reader.ui.book.import.remote

import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import com.peiyu.reader.R
import com.peiyu.reader.base.BaseDialogFragment
import com.peiyu.reader.data.entities.Server
import com.peiyu.reader.data.entities.rule.RowUi
import com.peiyu.reader.databinding.DialogWebdavServerBinding
import com.peiyu.reader.databinding.ItemSourceEditBinding
import com.peiyu.reader.lib.theme.primaryColor
import com.peiyu.reader.utils.GSON
import com.peiyu.reader.utils.applyTint
import com.peiyu.reader.utils.setLayout
import com.peiyu.reader.utils.viewbindingdelegate.viewBinding
import org.json.JSONObject

class ServerConfigDialog() : BaseDialogFragment(R.layout.dialog_webdav_server, true),
    Toolbar.OnMenuItemClickListener {

    constructor(id: Long) : this() {
        arguments = Bundle().apply {
            putLong("id", id)
        }
    }

    private val binding by viewBinding(DialogWebdavServerBinding::bind)
    private val viewModel by viewModels<ServerConfigViewModel>()

    private val webDavServerUi = listOf(
        RowUi("url"),
        RowUi("username"),
        RowUi("password", RowUi.Type.password)
    )

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.inflateMenu(R.menu.server_config)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        viewModel.init(arguments?.getLong("id")) {
            upConfigView(viewModel.mServer)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> getServer().let {
                viewModel.save(it) {
                    dismissAllowingStateLoss()
                }
            }
        }
        return true
    }

    private fun upConfigView(server: Server?) {
        binding.etName.setText(server?.name)
        binding.spType.setSelection(
            when (server?.type) {
                else -> 0
            }
        )
        when (server?.type) {
            else -> upWebDavServerUi(server?.getConfigJsonObject())
        }
    }

    private fun upWebDavServerUi(config: JSONObject?) {
        webDavServerUi.forEachIndexed { index, rowUi ->
            when (rowUi.type) {
                RowUi.Type.text -> ItemSourceEditBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false
                ).let {
                    binding.flexbox.addView(it.root)
                    it.root.id = index + 1000
                    it.textInputLayout.hint = rowUi.name
                    it.editText.setText(config?.getString(rowUi.name))
                }
                RowUi.Type.password -> ItemSourceEditBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false
                ).let {
                    binding.flexbox.addView(it.root)
                    it.root.id = index + 1000
                    it.textInputLayout.hint = rowUi.name
                    it.editText.inputType =
                        InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT
                    it.editText.setText(config?.getString(rowUi.name))
                }
            }
        }
    }

    private fun getServer(): Server {
        val server = viewModel.mServer?.copy() ?: Server()
        server.name = binding.etName.text.toString()
        server.type = when (binding.spType.selectedItemPosition) {
            else -> Server.TYPE.WEBDAV
        }
        server.config = when (server.type) {
            else -> GSON.toJson(getWebDavConfig())
        }
        return server
    }

    private fun getWebDavConfig(): HashMap<String, String> {
        val data = hashMapOf<String, String>()
        webDavServerUi.forEachIndexed { index, rowUi ->
            val rowView = binding.root.findViewById<View>(index + 1000)
            ItemSourceEditBinding.bind(rowView).editText.text?.let {
                data[rowUi.name] = it.toString()
            }
        }
        return data
    }

}

package com.peiyu.reader.ui.book.read.config

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isGone
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.peiyu.reader.R
import com.peiyu.reader.base.BaseDialogFragment
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.constant.EventBus
import com.peiyu.reader.databinding.DialogEditTextBinding
import com.peiyu.reader.databinding.DialogReadBgTextBinding
import com.peiyu.reader.databinding.ItemBgImageBinding
import com.peiyu.reader.help.DefaultData
import com.peiyu.reader.help.book.isImage
import com.peiyu.reader.help.config.ReadBookConfig
import com.peiyu.reader.help.http.newCallResponseBody
import com.peiyu.reader.help.http.okHttpClient
import com.peiyu.reader.lib.dialogs.SelectItem
import com.peiyu.reader.lib.dialogs.alert
import com.peiyu.reader.lib.dialogs.selector
import com.peiyu.reader.lib.theme.bottomBackground
import com.peiyu.reader.lib.theme.getPrimaryTextColor
import com.peiyu.reader.lib.theme.getSecondaryTextColor
import com.peiyu.reader.model.ReadBook
import com.peiyu.reader.ui.book.read.ReadBookActivity
import com.peiyu.reader.ui.file.HandleFileContract
import com.peiyu.reader.ui.widget.seekbar.SeekBarChangeListener
import com.peiyu.reader.utils.ColorUtils
import com.peiyu.reader.utils.FileDoc
import com.peiyu.reader.utils.FileUtils
import com.peiyu.reader.utils.GSON
import com.peiyu.reader.utils.MD5Utils
import com.peiyu.reader.utils.compress.ZipUtils
import com.peiyu.reader.utils.createFileIfNotExist
import com.peiyu.reader.utils.createFileReplace
import com.peiyu.reader.utils.createFolderReplace
import com.peiyu.reader.utils.delete
import com.peiyu.reader.utils.externalCache
import com.peiyu.reader.utils.externalFiles
import com.peiyu.reader.utils.find
import com.peiyu.reader.utils.getFile
import com.peiyu.reader.utils.inputStream
import com.peiyu.reader.utils.longToast
import com.peiyu.reader.utils.openInputStream
import com.peiyu.reader.utils.openOutputStream
import com.peiyu.reader.utils.outputStream
import com.peiyu.reader.utils.postEvent
import com.peiyu.reader.utils.printOnDebug
import com.peiyu.reader.utils.readBytes
import com.peiyu.reader.utils.readUri
import com.peiyu.reader.utils.stackTraceStr
import com.peiyu.reader.utils.toastOnUi
import com.peiyu.reader.utils.viewbindingdelegate.viewBinding
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream

class BgTextConfigDialog : BaseDialogFragment(R.layout.dialog_read_bg_text) {

    companion object {
        const val TEXT_COLOR = 121
        const val BG_COLOR = 122
    }

    private val binding by viewBinding(DialogReadBgTextBinding::bind)
    private val configFileName = "readConfig.zip"
    private val adapter by lazy { BgAdapter(requireContext(), secondaryTextColor) }
    private var primaryTextColor = 0
    private var secondaryTextColor = 0
    private val importFormNet = "网络导入"
    private val selectBgImage = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            setBgFromUri(uri)
        }
    }
    private val selectExportDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            exportConfig(uri)
        }
    }
    private val selectImportDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            if (uri.path == "/$importFormNet") {
                importNetConfigAlert()
            } else {
                importConfig(uri)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.background)
            decorView.setPadding(0, 0, 0, 0)
            val attr = attributes
            attr.dimAmount = 0.0f
            attr.gravity = Gravity.BOTTOM
            attributes = attr
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (activity as ReadBookActivity).bottomDialog++
        initView()
        initData()
        initEvent()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
        (activity as ReadBookActivity).bottomDialog--
    }

    private fun initView() = binding.run {
        val bg = requireContext().bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        primaryTextColor = requireContext().getPrimaryTextColor(isLight)
        secondaryTextColor = requireContext().getSecondaryTextColor(isLight)
        rootView.setBackgroundColor(bg)
        tvNameTitle.setTextColor(primaryTextColor)
        tvName.setTextColor(secondaryTextColor)
        ivEdit.setColorFilter(secondaryTextColor, PorterDuff.Mode.SRC_IN)
        tvRestore.setTextColor(primaryTextColor)
        swDarkStatusIcon.setTextColor(primaryTextColor)
        swUnderline.setTextColor(primaryTextColor)
        ivImport.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
        ivExport.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
        ivDelete.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
        tvBgAlpha.setTextColor(primaryTextColor)
        tvBgImage.setTextColor(primaryTextColor)
        swUnderline.isGone = ReadBook.book?.isImage == true
        recyclerView.adapter = adapter
        adapter.addHeaderView {
            ItemBgImageBinding.inflate(layoutInflater, it, false).apply {
                tvName.setTextColor(secondaryTextColor)
                tvName.text = getString(R.string.select_image)
                ivBg.setImageResource(R.drawable.ic_image)
                ivBg.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
                root.setOnClickListener {
                    selectBgImage.launch {
                        mode = HandleFileContract.IMAGE
                    }
                }
            }
        }
        requireContext().assets.list("bg")?.let {
            adapter.setItems(it.toList())
        }
    }

    @SuppressLint("InflateParams")
    private fun initData() = with(ReadBookConfig.durConfig) {
        binding.tvName.text = name.ifBlank { "文字" }
        binding.swDarkStatusIcon.isChecked = curStatusIconDark()
        binding.swUnderline.isChecked = underline
        binding.sbBgAlpha.progress = bgAlpha
    }

    @SuppressLint("InflateParams")
    private fun initEvent() = with(ReadBookConfig.durConfig) {
        binding.ivEdit.setOnClickListener {
            alert(R.string.style_name) {
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = "name"
                    editView.setText(ReadBookConfig.durConfig.name)
                }
                customView { alertBinding.root }
                okButton {
                    alertBinding.editView.text?.toString()?.let {
                        binding.tvName.text = it
                        ReadBookConfig.durConfig.name = it
                    }
                }
                cancelButton()
            }
        }
        binding.tvRestore.setOnClickListener {
            val defaultConfigs = DefaultData.readConfigs
            val layoutNames = defaultConfigs.map { it.name }
            context?.selector("选择预设布局", layoutNames) { _, i ->
                if (i >= 0) {
                    ReadBookConfig.durConfig = defaultConfigs[i].copy()
                    initData()
                    postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
                }
            }
        }
        binding.swDarkStatusIcon.setOnCheckedChangeListener { _, isChecked ->
            setCurStatusIconDark(isChecked)
            (activity as? ReadBookActivity)?.upSystemUiVisibility()
        }
        binding.swUnderline.setOnCheckedChangeListener { _, isChecked ->
            underline = isChecked
            postEvent(EventBus.UP_CONFIG, arrayListOf(6, 9, 11))
        }
        binding.tvTextColor.setOnClickListener {
            ColorPickerDialog.newBuilder()
                .setColor(curTextColor())
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(TEXT_COLOR)
                .show(requireActivity())
        }
        binding.tvBgColor.setOnClickListener {
            val bgColor =
                if (curBgType() == 0) curBgStr().toColorInt()
                else "#015A86".toColorInt()
            ColorPickerDialog.newBuilder()
                .setColor(bgColor)
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(BG_COLOR)
                .show(requireActivity())
        }
        binding.tvBgColor.apply {
            TooltipCompat.setTooltipText(this, text)
        }
        binding.ivImport.setOnClickListener {
            selectImportDoc.launch {
                mode = HandleFileContract.FILE
                title = getString(R.string.import_str)
                allowExtensions = arrayOf("zip")
                otherActions = arrayListOf(SelectItem(importFormNet, -1))
            }
        }
        binding.ivExport.setOnClickListener {
            selectExportDir.launch {
                title = getString(R.string.export_str)
            }
        }
        binding.ivDelete.setOnClickListener {
            if (ReadBookConfig.deleteDur()) {
                postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
                dismissAllowingStateLoss()
            } else {
                toastOnUi("数量已是最�?不能删除.")
            }
        }
        binding.sbBgAlpha.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                ReadBookConfig.bgAlpha = progress
                postEvent(EventBus.UP_CONFIG, arrayListOf(3))
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                postEvent(EventBus.UP_CONFIG, arrayListOf(3))
            }
        })
    }

    private fun exportConfig(uri: Uri) {
        val exportFileName = if (ReadBookConfig.config.name.isBlank()) {
            configFileName
        } else {
            "${ReadBookConfig.config.name}.zip"
        }
        execute {
            val exportFiles = arrayListOf<File>()
            val configDir = requireContext().externalCache.getFile("readConfig")
            configDir.createFolderReplace()
            val configFile = configDir.getFile("readConfig.json")
            configFile.createFileReplace()
            val config = ReadBookConfig.getExportConfig()
            val fontPath = ReadBookConfig.textFont
            if (fontPath.isNotEmpty()) {
                val fontDoc = FileDoc.fromFile(fontPath)
                val fontName = fontDoc.name
                val fontInputStream = fontDoc.openInputStream().getOrNull()
                fontInputStream?.use {
                    val fontExportFile = FileUtils.createFileIfNotExist(configDir, fontName)
                    fontExportFile.outputStream().use { out ->
                        it.copyTo(out)
                    }
                    config.textFont = fontName
                    exportFiles.add(fontExportFile)
                }
            }
            configFile.writeText(GSON.toJson(config))
            exportFiles.add(configFile)
            repeat(3) {
                val path = ReadBookConfig.durConfig.getBgPath(it) ?: return@repeat
                val bgExportFile = copyBgImage(path, configDir) ?: return@repeat
                exportFiles.add(bgExportFile)
            }
            val configZipPath = FileUtils.getPath(requireContext().externalCache, configFileName)
            if (ZipUtils.zipFiles(exportFiles, File(configZipPath))) {
                val exportDir = FileDoc.fromDir(uri)
                exportDir.find(exportFileName)?.delete()
                val exportFileDoc = exportDir.createFileIfNotExist(exportFileName)
                exportFileDoc.openOutputStream().getOrThrow().use { out ->
                    File(configZipPath).inputStream().use {
                        it.copyTo(out)
                    }
                }
            }
        }.onSuccess {
            toastOnUi("导出成功, 文件名为 $exportFileName")
        }.onError {
            it.printOnDebug()
            AppLog.put("导出失败:${it.localizedMessage}", it)
            longToast("导出失败:${it.localizedMessage}")
        }
    }

    private fun copyBgImage(path: String, configDir: File): File? {
        val bgName = FileUtils.getName(path)
        val bgFile = File(path)
        if (bgFile.exists()) {
            val bgExportFile = File(FileUtils.getPath(configDir, bgName))
            if (!bgExportFile.exists()) {
                bgFile.copyTo(bgExportFile)
                return bgExportFile
            }
        }
        return null
    }

    @SuppressLint("InflateParams")
    private fun importNetConfigAlert() {
        alert("输入地址") {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater)
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let { url ->
                    importNetConfig(url)
                }
            }
            cancelButton()
        }
    }

    private fun importNetConfig(url: String) {
        execute {
            okHttpClient.newCallResponseBody {
                url(url)
            }.bytes().let {
                importConfig(it)
            }
        }.onError {
            longToast(it.stackTraceStr)
        }
    }

    private fun importConfig(uri: Uri) {
        execute {
            importConfig(uri.readBytes(requireContext()))
        }.onError {
            it.printOnDebug()
            longToast("导入失败:${it.localizedMessage}")
        }
    }

    private fun importConfig(byteArray: ByteArray) {
        execute {
            ReadBookConfig.import(byteArray)
        }.onSuccess {
            ReadBookConfig.durConfig = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
            toastOnUi("导入成功")
        }.onError {
            it.printOnDebug()
            longToast("导入失败:${it.localizedMessage}")
        }
    }

    private fun setBgFromUri(uri: Uri) {
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = fileDoc.name.substringAfterLast(".")
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + ".$suffix"
                }
                file = FileUtils.createFileIfNotExist(file, "bg", fileName)
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                ReadBookConfig.durConfig.setCurBg(2, fileName)
                postEvent(EventBus.UP_CONFIG, arrayListOf(1))
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }
}

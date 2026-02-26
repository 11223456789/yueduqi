package com.peiyu.reader.ui.association

import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.os.postDelayed
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.peiyu.reader.R
import com.peiyu.reader.base.VMBaseActivity
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.databinding.ActivityTranslucenceBinding
import com.peiyu.reader.exception.InvalidBooksDirException
import com.peiyu.reader.help.config.AppConfig
import com.peiyu.reader.lib.dialogs.alert
import com.peiyu.reader.lib.permission.Permissions
import com.peiyu.reader.lib.permission.PermissionsCompat
import com.peiyu.reader.ui.file.HandleFileContract
import com.peiyu.reader.utils.FileUtils
import com.peiyu.reader.utils.buildMainHandler
import com.peiyu.reader.utils.canRead
import com.peiyu.reader.utils.checkWrite
import com.peiyu.reader.utils.getFile
import com.peiyu.reader.utils.isContentScheme
import com.peiyu.reader.utils.readUri
import com.peiyu.reader.utils.showDialogFragment
import com.peiyu.reader.utils.startActivity
import com.peiyu.reader.utils.startActivityForBook
import com.peiyu.reader.utils.toastOnUi
import com.peiyu.reader.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream

class FileAssociationActivity :
    VMBaseActivity<ActivityTranslucenceBinding, FileAssociationViewModel>() {

    private val localBookTreeSelect = registerForActivityResult(HandleFileContract()) {
        intent.data?.let { uri ->
            it.uri?.let { treeUri ->
                AppConfig.defaultBookTreeUri = treeUri.toString()
                importBook(treeUri, uri)
            } ?: let {
                val storageHelp = String(assets.open("storageHelp.md").readBytes())
                toastOnUi(storageHelp)
                importBook(null, uri)
            }
        }
    }

    override val binding by viewBinding(ActivityTranslucenceBinding::inflate)

    override val viewModel by viewModels<FileAssociationViewModel>()

    private val handler by lazy {
        buildMainHandler()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.rotateLoading.visible()
        viewModel.importBookLiveData.observe(this) { uri ->
            importBook(uri)
        }
        viewModel.onLineImportLive.observe(this) {
            startActivity<OnLineImportActivity> {
                data = it
            }
            finish()
        }
        viewModel.successLive.observe(this) {
            when (it.first) {
                "bookSource" -> showDialogFragment(ImportBookSourceDialog(it.second, true))
                "rssSource" -> showDialogFragment(ImportRssSourceDialog(it.second, true))
                "replaceRule" -> showDialogFragment(ImportReplaceRuleDialog(it.second, true))
                "httpTts" -> showDialogFragment(ImportHttpTtsDialog(it.second, true))
                "theme" -> showDialogFragment(ImportThemeDialog(it.second, true))
                "txtRule" -> showDialogFragment(ImportTxtTocRuleDialog(it.second, true))
                "dictRule" -> showDialogFragment(ImportDictRuleDialog(it.second, true))
            }
        }
        viewModel.errorLive.observe(this) {
            binding.rotateLoading.gone()
            toastOnUi(it)
            handler.postDelayed(2000) {
                finish()
            }
        }
        viewModel.openBookLiveData.observe(this) {
            binding.rotateLoading.gone()
            startActivityForBook(it)
            finish()
        }
        viewModel.notSupportedLiveData.observe(this) { data ->
            binding.rotateLoading.gone()
            alert(
                title = appCtx.getString(R.string.draw),
                message = appCtx.getString(R.string.file_not_supported, data.second)
            ) {
                yesButton {
                    importBook(data.first)
                }
                noButton {
                    finish()
                }
                onCancelled {
                    finish()
                }
            }
        }
        intent.data?.let { data ->
            if (data.isContentScheme() && data.canRead()) {
                viewModel.dispatchIntent(data)
            } else {
                PermissionsCompat.Builder()
                    .addPermissions(*Permissions.Group.STORAGE)
                    .rationale(R.string.tip_perm_request_storage)
                    .onGranted {
                        viewModel.dispatchIntent(data)
                    }.onDenied {
                        toastOnUi("请求存储权限失败�?)
                        handler.postDelayed(2000) {
                            finish()
                        }
                    }.request()
            }
        } ?: finish()
    }

    private fun importBook(uri: Uri) {
        if (uri.isContentScheme()) {
            val treeUriStr = AppConfig.defaultBookTreeUri
            if (treeUriStr.isNullOrEmpty()) {
                localBookTreeSelect.launch {
                    title = getString(R.string.select_book_folder)
                    mode = HandleFileContract.DIR_SYS
                }
            } else {
                importBook(Uri.parse(treeUriStr), uri)
            }
        } else {
            importBook(null, uri)
        }
    }

    private fun importBook(treeUri: Uri?, uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                withContext(IO) {
                    if (treeUri == null) {
                        viewModel.importBook(uri)
                    } else if (treeUri.isContentScheme()) {
                        val treeDoc =
                            DocumentFile.fromTreeUri(this@FileAssociationActivity, treeUri)
                        if (!treeDoc!!.checkWrite()) {
                            throw InvalidBooksDirException(
                                "请重新设置书籍保存位置\nPermission Denial"
                            )
                        }
                        readUri(uri) { fileDoc, inputStream ->
                            val name = fileDoc.name
                            var doc = treeDoc.findFile(name)
                            if (doc == null || fileDoc.lastModified > doc.lastModified()) {
                                if (doc == null) {
                                    doc = treeDoc.createFile(FileUtils.getMimeType(name), name)
                                        ?: throw InvalidBooksDirException(
                                            "请重新设置书籍保存位置\nPermission Denial"
                                        )
                                }
                                contentResolver.openOutputStream(doc.uri)!!.use { oStream ->
                                    inputStream.copyTo(oStream)
                                    oStream.flush()
                                }
                            }
                            viewModel.importBook(doc.uri)
                        }
                    } else {
                        val treeFile = File(treeUri.path ?: treeUri.toString())
                        if (!treeFile.checkWrite()) {
                            throw InvalidBooksDirException(
                                "请重新设置书籍保存位置\nPermission Denial"
                            )
                        }
                        readUri(uri) { fileDoc, inputStream ->
                            val name = fileDoc.name
                            val file = treeFile.getFile(name)
                            if (!file.exists() || fileDoc.lastModified > file.lastModified()) {
                                FileOutputStream(file).use { oStream ->
                                    inputStream.copyTo(oStream)
                                    oStream.flush()
                                }
                            }
                            viewModel.importBook(Uri.fromFile(file))
                        }
                    }
                }
            }.onFailure {
                when (it) {
                    is InvalidBooksDirException -> localBookTreeSelect.launch {
                        title = getString(R.string.select_book_folder)
                        mode = HandleFileContract.DIR_SYS
                    }

                    else -> {
                        val msg = "导入书籍失败\n${it.localizedMessage}"
                        AppLog.put(msg, it)
                        toastOnUi(msg)
                        handler.postDelayed(2000) {
                            finish()
                        }
                    }
                }
            }
        }
    }

}

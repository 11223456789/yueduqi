package com.peiyu.reader.ui.association

import android.annotation.SuppressLint
import android.app.Application
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import com.peiyu.reader.R
import com.peiyu.reader.base.BaseDialogFragment
import com.peiyu.reader.base.BaseViewModel
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.data.appDb
import com.peiyu.reader.data.entities.Book
import com.peiyu.reader.data.entities.BookSource
import com.peiyu.reader.databinding.DialogAddToBookshelfBinding
import com.peiyu.reader.exception.NoStackTraceException
import com.peiyu.reader.model.analyzeRule.AnalyzeUrl
import com.peiyu.reader.model.webBook.WebBook
import com.peiyu.reader.ui.book.info.BookInfoActivity
import com.peiyu.reader.utils.GSON
import com.peiyu.reader.utils.NetworkUtils
import com.peiyu.reader.utils.fromJsonObject
import com.peiyu.reader.utils.setLayout
import com.peiyu.reader.utils.startActivity
import com.peiyu.reader.utils.toastOnUi
import com.peiyu.reader.utils.viewbindingdelegate.viewBinding

/**
 * 添加书籍链接到书架，需要对应网站书�? * ${origin}/${path}, {origin: bookSourceUrl}
 * 按以下顺序尝试匹配书源并添加网址
 * - UrlOption中的指定的书源网址bookSourceUrl
 * - 在所有启用的书源中匹配orgin
 * - 在所有启用的书源中使用详情页正则匹配${origin}/${path}, {origin: bookSourceUrl}
 */
class AddToBookshelfDialog() : BaseDialogFragment(R.layout.dialog_add_to_bookshelf) {

    constructor(bookUrl: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("bookUrl", bookUrl)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    val binding by viewBinding(DialogAddToBookshelfBinding::bind)
    val viewModel by viewModels<ViewModel>()

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (arguments?.getBoolean("finishOnDismiss") == true) {
            activity?.finish()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val bookUrl = arguments?.getString("bookUrl")
        if (bookUrl.isNullOrBlank()) {
            toastOnUi("url不能为空")
            dismiss()
            return
        }
        viewModel.loadStateLiveData.observe(this) {
            if (it) {
                binding.rotateLoading.visible()
            } else {
                binding.rotateLoading.gone()
            }
        }
        viewModel.loadErrorLiveData.observe(this) {
            toastOnUi(it)
            dismiss()
        }
        viewModel.load(bookUrl) {
            viewModel.saveSearchBook(it) {
                startActivity<BookInfoActivity> {
                    putExtra("name", it.name)
                    putExtra("author", it.author)
                    putExtra("bookUrl", it.bookUrl)
                }
                dismiss()
            }
        }
        binding.tvCancel.setOnClickListener {
            dismiss()
        }
    }

    class ViewModel(application: Application) : BaseViewModel(application) {

        val loadStateLiveData = MutableLiveData<Boolean>()
        val loadErrorLiveData = MutableLiveData<String>()
        var book: Book? = null

        fun load(bookUrl: String, success: (book: Book) -> Unit) {
            execute {
                appDb.bookDao.getBook(bookUrl)?.let {
                    throw NoStackTraceException("${it.name} 已在书架")
                }
                val baseUrl = NetworkUtils.getBaseUrl(bookUrl)
                    ?: throw NoStackTraceException("书籍地址格式不对")
                val urlMatcher = AnalyzeUrl.paramPattern.matcher(bookUrl)
                if (urlMatcher.find()) {
                    val origin = GSON.fromJsonObject<AnalyzeUrl.UrlOption>(
                        bookUrl.substring(urlMatcher.end())
                    ).getOrNull()?.getOrigin()
                    origin?.let {
                        val source = appDb.bookSourceDao.getBookSource(it)
                        source?.let {
                            getBookInfo(bookUrl, source)?.let { book ->
                                return@execute book
                            }
                        }
                    }
                }
                appDb.bookSourceDao.getBookSourceAddBook(baseUrl)?.let { source ->
                    getBookInfo(bookUrl, source)?.let { book ->
                        return@execute book
                    }
                }
                appDb.bookSourceDao.hasBookUrlPattern.forEach { source ->
                    try {
                        val bs = source.getBookSource()!!
                        if (bookUrl.matches(bs.bookUrlPattern!!.toRegex())) {
                            getBookInfo(bookUrl, bs)?.let { book ->
                                return@execute book
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
                throw NoStackTraceException("未找到匹配书�?)
            }.onError {
                AppLog.put("添加书籍 $bookUrl 出错", it)
                loadErrorLiveData.postValue(it.localizedMessage)
            }.onSuccess {
                book = it
                success.invoke(it)
            }.onStart {
                loadStateLiveData.postValue(true)
            }.onFinally {
                loadStateLiveData.postValue(false)
            }
        }

        private suspend fun getBookInfo(bookUrl: String, source: BookSource): Book? {
            return kotlin.runCatching {
                val book = Book(
                    bookUrl = bookUrl,
                    origin = source.bookSourceUrl,
                    originName = source.bookSourceName
                )
                WebBook.getBookInfoAwait(source, book)
            }.getOrNull()
        }

        fun saveSearchBook(book: Book, success: () -> Unit) {
            execute {
                val searchBook = book.toSearchBook()
                appDb.searchBookDao.insert(searchBook)
                searchBook
            }.onSuccess {
                success.invoke()
            }
        }

    }

}

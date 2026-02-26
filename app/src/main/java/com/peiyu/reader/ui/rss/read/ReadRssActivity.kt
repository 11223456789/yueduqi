package com.peiyu.reader.ui.rss.read

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.size
import androidx.lifecycle.lifecycleScope
import com.script.rhino.runScriptWithContext
import com.peiyu.reader.R
import com.peiyu.reader.base.VMBaseActivity
import com.peiyu.reader.constant.AppConst
import com.peiyu.reader.constant.AppConst.imagePathKey
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.data.entities.RssSource
import com.peiyu.reader.databinding.ActivityRssReadBinding
import com.peiyu.reader.help.config.AppConfig
import com.peiyu.reader.help.http.CookieManager
import com.peiyu.reader.lib.dialogs.SelectItem
import com.peiyu.reader.lib.dialogs.selector
import com.peiyu.reader.lib.theme.accentColor
import com.peiyu.reader.lib.theme.primaryTextColor
import com.peiyu.reader.model.Download
import com.peiyu.reader.ui.association.OnLineImportActivity
import com.peiyu.reader.ui.file.HandleFileContract
import com.peiyu.reader.ui.login.SourceLoginActivity
import com.peiyu.reader.ui.rss.favorites.RssFavoritesDialog
import com.peiyu.reader.utils.ACache
import com.peiyu.reader.utils.NetworkUtils
import com.peiyu.reader.utils.gone
import com.peiyu.reader.utils.invisible
import com.peiyu.reader.utils.isTrue
import com.peiyu.reader.utils.keepScreenOn
import com.peiyu.reader.utils.longSnackbar
import com.peiyu.reader.utils.openUrl
import com.peiyu.reader.utils.setDarkeningAllowed
import com.peiyu.reader.utils.setOnApplyWindowInsetsListenerCompat
import com.peiyu.reader.utils.setTintMutate
import com.peiyu.reader.utils.share
import com.peiyu.reader.utils.showDialogFragment
import com.peiyu.reader.utils.splitNotBlank
import com.peiyu.reader.utils.startActivity
import com.peiyu.reader.utils.textArray
import com.peiyu.reader.utils.toastOnUi
import com.peiyu.reader.utils.toggleSystemBar
import com.peiyu.reader.utils.viewbindingdelegate.viewBinding
import com.peiyu.reader.utils.visible
import kotlinx.coroutines.launch
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup
import splitties.views.bottomPadding
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.util.regex.PatternSyntaxException

/**
 * rss阅读界面
 */
class ReadRssActivity : VMBaseActivity<ActivityRssReadBinding, ReadRssViewModel>(),
    RssFavoritesDialog.Callback {

    override val binding by viewBinding(ActivityRssReadBinding::inflate)
    override val viewModel by viewModels<ReadRssViewModel>()

    private var starMenuItem: MenuItem? = null
    private var ttsMenuItem: MenuItem? = null
    private var customWebViewCallback: WebChromeClient.CustomViewCallback? = null
    private val selectImageDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            ACache.get().put(imagePathKey, uri.toString())
            viewModel.saveImage(it.value, uri)
        }
    }
    private val rssJsExtensions by lazy { RssJsExtensions(this) }

    fun getSource(): RssSource? {
        return viewModel.rssSource
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        viewModel.upStarMenuData.observe(this) { upStarMenu() }
        viewModel.upTtsMenuData.observe(this) { upTtsMenu(it) }
        binding.titleBar.title = intent.getStringExtra("title")
        initView()
        initWebView()
        initLiveData()
        viewModel.initData(intent)
        onBackPressedDispatcher.addCallback(this) {
            if (binding.customWebView.size > 0) {
                customWebViewCallback?.onCustomViewHidden()
                return@addCallback
            } else if (binding.webView.canGoBack()
                && binding.webView.copyBackForwardList().size > 1
            ) {
                binding.webView.goBack()
                return@addCallback
            }
            finish()
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("SwitchIntDef")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.rss_read, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        starMenuItem = menu.findItem(R.id.menu_rss_star)
        ttsMenuItem = menu.findItem(R.id.menu_aloud)
        upStarMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_login)?.isVisible = !viewModel.rssSource?.loginUrl.isNullOrBlank()
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_rss_refresh -> viewModel.refresh {
                binding.webView.reload()
            }

            R.id.menu_rss_star -> {
                viewModel.addFavorite()
                viewModel.rssArticle?.let {
                    showDialogFragment(RssFavoritesDialog(it))
                }
            }

            R.id.menu_share_it -> {
                binding.webView.url?.let {
                    share(it)
                } ?: viewModel.rssArticle?.let {
                    share(it.link)
                } ?: toastOnUi(R.string.null_url)
            }

            R.id.menu_aloud -> readAloud()
            R.id.menu_login -> startActivity<SourceLoginActivity> {
                putExtra("type", "rssSource")
                putExtra("key", viewModel.rssSource?.sourceUrl)
            }

            R.id.menu_browser_open -> binding.webView.url?.let {
                openUrl(it)
            } ?: toastOnUi("url null")
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun updateFavorite(title: String?, group: String?) {
        viewModel.rssArticle?.let {
            if (title != null) {
                it.title = title
            }
            if (group != null) {
                it.group = group
            }
        }
        viewModel.updateFavorite()
    }

    override fun deleteFavorite() {
        viewModel.delFavorite()
    }

    @JavascriptInterface
    fun isNightTheme(): Boolean {
        return AppConfig.isNightTheme
    }

    private fun initView() {
        binding.root.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            val typeMask = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            val insets = windowInsets.getInsets(typeMask)
            view.bottomPadding = insets.bottom
            windowInsets
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun initWebView() {
        binding.progressBar.fontColor = accentColor
        binding.webView.webChromeClient = CustomWebChromeClient()
        binding.webView.webViewClient = CustomWebViewClient()
        binding.webView.settings.apply {
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            domStorageEnabled = true
            allowContentAccess = true
            builtInZoomControls = true
            displayZoomControls = false
            setDarkeningAllowed(AppConfig.isNightTheme)
        }
        binding.webView.addJavascriptInterface(this, "thisActivity")
        binding.webView.setOnLongClickListener {
            val hitTestResult = binding.webView.hitTestResult
            if (hitTestResult.type == WebView.HitTestResult.IMAGE_TYPE ||
                hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            ) {
                hitTestResult.extra?.let { webPic ->
                    selector(
                        arrayListOf(
                            SelectItem(getString(R.string.action_save), "save"),
                            SelectItem(getString(R.string.select_folder), "selectFolder")
                        )
                    ) { _, charSequence, _ ->
                        when (charSequence.value) {
                            "save" -> saveImage(webPic)
                            "selectFolder" -> selectSaveFolder(null)
                        }
                    }
                    return@setOnLongClickListener true
                }
            }
            return@setOnLongClickListener false
        }
        binding.webView.setDownloadListener { url, _, contentDisposition, _, _ ->
            var fileName = URLUtil.guessFileName(url, contentDisposition, null)
            fileName = URLDecoder.decode(fileName, "UTF-8")
            binding.llView.longSnackbar(fileName, getString(R.string.action_download)) {
                Download.start(this, url, fileName)
            }
        }

    }

    private fun saveImage(webPic: String) {
        val path = ACache.get().getAsString(imagePathKey)
        if (path.isNullOrEmpty()) {
            selectSaveFolder(webPic)
        } else {
            viewModel.saveImage(webPic, Uri.parse(path))
        }
    }

    private fun selectSaveFolder(webPic: String?) {
        val default = arrayListOf<SelectItem<Int>>()
        val path = ACache.get().getAsString(imagePathKey)
        if (!path.isNullOrEmpty()) {
            default.add(SelectItem(path, -1))
        }
        selectImageDir.launch {
            otherActions = default
            value = webPic
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initLiveData() {
        viewModel.contentLiveData.observe(this) { content ->
            viewModel.rssArticle?.let {
                upJavaScriptEnable()
                val url = NetworkUtils.getAbsoluteURL(it.origin, it.link)
                val html = viewModel.clHtml(content)
                binding.webView.settings.userAgentString =
                    viewModel.headerMap[AppConst.UA_NAME] ?: AppConfig.userAgent
                if (viewModel.rssSource?.loadWithBaseUrl == true) {
                    binding.webView
                        .loadDataWithBaseURL(url, html, "text/html", "utf-8", url)//不想用baseUrl进else
                } else {
                    binding.webView
                        .loadDataWithBaseURL(null, html, "text/html;charset=utf-8", "utf-8", url)
                }
            }
        }
        viewModel.urlLiveData.observe(this) {
            upJavaScriptEnable()
            CookieManager.applyToWebView(it.url)
            binding.webView.settings.userAgentString = it.getUserAgent()
            binding.webView.loadUrl(it.url, it.headerMap)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun upJavaScriptEnable() {
        if (viewModel.rssSource?.enableJs == true) {
            binding.webView.settings.javaScriptEnabled = true
        }
    }

    private fun upStarMenu() {
        starMenuItem?.isVisible = viewModel.rssArticle != null
        if (viewModel.rssStar != null) {
            starMenuItem?.setIcon(R.drawable.ic_star)
            starMenuItem?.setTitle(R.string.in_favorites)
        } else {
            starMenuItem?.setIcon(R.drawable.ic_star_border)
            starMenuItem?.setTitle(R.string.out_favorites)
        }
        starMenuItem?.icon?.setTintMutate(primaryTextColor)
    }

    private fun upTtsMenu(isPlaying: Boolean) {
        lifecycleScope.launch {
            if (isPlaying) {
                ttsMenuItem?.setIcon(R.drawable.ic_stop_black_24dp)
                ttsMenuItem?.setTitle(R.string.aloud_stop)
            } else {
                ttsMenuItem?.setIcon(R.drawable.ic_volume_up)
                ttsMenuItem?.setTitle(R.string.read_aloud)
            }
            ttsMenuItem?.icon?.setTintMutate(primaryTextColor)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun readAloud() {
        if (viewModel.tts?.isSpeaking == true) {
            viewModel.tts?.stop()
            upTtsMenu(false)
        } else {
            binding.webView.settings.javaScriptEnabled = true
            binding.webView.evaluateJavascript("document.documentElement.outerHTML") {
                val html = StringEscapeUtils.unescapeJson(it)
                    .replace("^\"|\"$".toRegex(), "")
                viewModel.readAloud(
                    Jsoup.parse(html)
                        .textArray()
                        .joinToString("\n")
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.webView.destroy()
    }

    inner class CustomWebChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            binding.progressBar.setDurProgress(newProgress)
            binding.progressBar.gone(newProgress == 100)
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            binding.llView.invisible()
            binding.customWebView.addView(view)
            customWebViewCallback = callback
            keepScreenOn(true)
            toggleSystemBar(false)
        }

        override fun onHideCustomView() {
            binding.customWebView.removeAllViews()
            binding.llView.visible()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            keepScreenOn(false)
            toggleSystemBar(true)
        }
    }

    inner class CustomWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            return shouldOverrideUrlLoading(request.url)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "KotlinRedundantDiagnosticSuppress")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            return shouldOverrideUrlLoading(Uri.parse(url))
        }

        /**
         * 如果有黑名单,黑名单匹配返回空�?
         * 没有黑名单再判断白名�?在白名单中的才通过,
         * 都没有不做处�?         */
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url.toString()
            val source = viewModel.rssSource ?: return super.shouldInterceptRequest(view, request)
            val blacklist = source.contentBlacklist?.splitNotBlank(",")
            if (!blacklist.isNullOrEmpty()) {
                blacklist.forEach {
                    try {
                        if (url.startsWith(it) || url.matches(it.toRegex())) {
                            return createEmptyResource()
                        }
                    } catch (e: PatternSyntaxException) {
                        AppLog.put("黑名单规则正则语法错�?源名�?${source.sourceName} 正则:$it", e)
                    }
                }
            } else {
                val whitelist = source.contentWhitelist?.splitNotBlank(",")
                if (!whitelist.isNullOrEmpty()) {
                    whitelist.forEach {
                        try {
                            if (url.startsWith(it) || url.matches(it.toRegex())) {
                                return super.shouldInterceptRequest(view, request)
                            }
                        } catch (e: PatternSyntaxException) {
                            val msg = "白名单规则正则语法错�?源名�?${source.sourceName} 正则:$it"
                            AppLog.put(msg, e)
                        }
                    }
                    return createEmptyResource()
                }
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            view.title?.let { title ->
                if (title != url
                    && title != view.url
                    && title.isNotBlank()
                    && url != "about:blank"
                    && !url.contains(title)
                ) {
                    binding.titleBar.title = title
                } else {
                    binding.titleBar.title = intent.getStringExtra("title")
                }
            }
            viewModel.rssSource?.injectJs?.let {
                if (it.isNotBlank()) {
                    view.evaluateJavascript(it, null)
                }
            }
        }

        private fun createEmptyResource(): WebResourceResponse {
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                ByteArrayInputStream("".toByteArray())
            )
        }

        private fun shouldOverrideUrlLoading(url: Uri): Boolean {
            val source = viewModel.rssSource
            val js = source?.shouldOverrideUrlLoading
            if (!js.isNullOrBlank()) {
                val t = SystemClock.uptimeMillis()
                val result = kotlin.runCatching {
                    runScriptWithContext(lifecycleScope.coroutineContext) {
                        source.evalJS(js) {
                            put("java", rssJsExtensions)
                            put("url", url.toString())
                        }.toString()
                    }
                }.onFailure {
                    AppLog.put("${source.getTag()}: url跳转拦截js出错", it)
                }.getOrNull()
                if (SystemClock.uptimeMillis() - t > 30) {
                    AppLog.put("${source.getTag()}: url跳转拦截js执行耗时过长")
                }
                if (result.isTrue()) {
                    return true
                }
            }
            when (url.scheme) {
                "http", "https", "jsbridge" -> {
                    return false
                }

                "legado", "yuedu" -> {
                    startActivity<OnLineImportActivity> {
                        data = url
                    }
                    return true
                }

                else -> {
                    binding.root.longSnackbar(R.string.jump_to_another_app, R.string.confirm) {
                        openUrl(url)
                    }
                    return true
                }
            }
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?
        ) {
            handler?.proceed()
        }

    }

}

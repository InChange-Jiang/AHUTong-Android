package com.ahu.ahutong.data.notice

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONTokener

/**
 * Renders only public graduate notice lists with the normal Android browser engine.
 * The private process/profile is separate from login WebViews; no cookies are read or exported.
 */
class PostgraduateNoticeWebProvider : ContentProvider() {
    private val fetchLock = Any()

    override fun onCreate(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix("postgraduate_notices")
        }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = synchronized(fetchLock) {
        if (method != METHOD_FETCH || !PostgraduateNoticeParser.isTrustedListUrl(arg)) {
            return@synchronized result(error = "公告请求地址不在研究生院栏目内")
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return@synchronized result(error = "当前系统版本不支持隔离网页数据")
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return@synchronized result(error = "网页请求不能在主线程执行")
        }

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val active = AtomicBoolean(true)
        val html = AtomicReference<String?>(null)
        val error = AtomicReference<String?>(null)
        val mainFrameHttpCode = AtomicReference<Int?>(null)
        val webView = AtomicReference<WebView?>(null)

        fun finish(message: String? = null, page: String? = null) {
            if (!active.compareAndSet(true, false)) return
            error.set(message)
            html.set(page)
            latch.countDown()
        }

        handler.post {
            runCatching {
                val view = WebView(ContextThemeWrapper(context, android.R.style.Theme_Material_Light_NoActionBar))
                webView.set(view)
                view.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
                view.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                        val address = Uri.parse(url)
                        val sameOfficialHost = address.scheme == "https" &&
                            address.host == "graschool.ahu.edu.cn" && address.port == -1
                        if (!sameOfficialHost && url != "about:blank") {
                            view.stopLoading()
                            finish(message = "研究生院公告页跳转到非受信任地址")
                        }
                    }

                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                        if (request.isForMainFrame) mainFrameHttpCode.set(response.statusCode)
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, received: WebResourceError) {
                        if (request.isForMainFrame) finish(message = "研究生院公告网页加载失败")
                    }
                }
                val check = object : Runnable {
                    override fun run() {
                        if (!active.get()) return
                        view.evaluateJavascript(EXTRACT_LIST_JS) { raw ->
                            val extracted = runCatching { JSONTokener(raw).nextValue() as String }.getOrNull()
                            if (!extracted.isNullOrBlank()) {
                                if (extracted.length > MAX_HTML_LENGTH) finish(message = "研究生院公告页面过大")
                                else finish(page = extracted)
                            } else if (active.get()) {
                                handler.postDelayed(this, POLL_INTERVAL_MS)
                            }
                        }
                    }
                }
                view.loadUrl(arg!!)
                handler.postDelayed(check, POLL_INTERVAL_MS)
            }.onFailure { finish(message = "研究生院公告网页初始化失败") }
        }

        val completed = latch.await(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!completed) {
            finish(message = if (mainFrameHttpCode.get() == 412) "访问受限（HTTP 412）" else "研究生院公告网页加载超时")
        }
        handler.post { webView.getAndSet(null)?.destroy() }
        result(html = html.get(), error = error.get())
    }

    private fun result(html: String? = null, error: String? = null): Bundle = Bundle().apply {
        putString(KEY_HTML, html)
        putString(KEY_ERROR, error)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val METHOD_FETCH = "fetch_list"
        const val KEY_HTML = "html"
        const val KEY_ERROR = "error"
        private const val FETCH_TIMEOUT_SECONDS = 25L
        private const val POLL_INTERVAL_MS = 500L
        private const val MAX_HTML_LENGTH = 500_000
        private val EXTRACT_LIST_JS = """
            (function() {
              var list = document.querySelector('ul.wp_article_list');
              if (!list || !list.querySelector('li.list_item')) return '';
              var next = document.querySelector('a.next');
              return '<html><body>' + list.outerHTML + (next ? next.outerHTML : '') + '</body></html>';
            })()
        """.trimIndent()
    }
}

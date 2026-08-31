package com.ahu.ahutong.ui.screen.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.os.MessageQueue
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import android.webkit.WebChromeClient
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.Role
import com.ahu.ahutong.data.crawler.manager.CookieManager as YcardCookieManager
import com.ahu.ahutong.data.crawler.manager.TokenManager
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.CardRechargeBank
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.ui.rememberBehaviorActionReporter
import com.google.gson.Gson
import com.ahu.ahutong.ui.components.AppPageHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import java.net.URI
import kotlin.coroutines.resume

internal data class CmbRechargeNormalizedBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

private const val CMB_NATIVE_BOOTSTRAP_TIMEOUT_MS = 15_000L
private const val CMB_PASSWORD_DISPATCH_TIMEOUT_MS = 15_000L
private const val CMB_SUCCESS_CONFIRMATION_TIMEOUT_MS = 8_000L
internal const val CMB_RECHARGE_PRELOAD_VALIDITY_MS = 3 * 60 * 1_000L

private const val CMB_SUBMIT_OBSERVER_SCRIPT = """
(function(){
  if (window.__ahutongSubmitObserverInstalled) return;
  window.__ahutongSubmitObserverInstalled = true;
  var lastNotice = 0;
  function notify(){
    var now = Date.now();
    if (now - lastNotice < 1000) return;
    lastNotice = now;
    window.AhuTongBehaviorBridge.onSubmitIntent();
  }
  document.addEventListener('submit', notify, true);
  document.addEventListener('click', function(event){
    var target = event.target && event.target.closest
      ? event.target.closest('button[type="submit"],input[type="submit"]')
      : null;
    if (target) notify();
  }, true);
})();
"""

private val CMB_NATIVE_STATE_BRIDGE_SCRIPT = """
(function(){
  function findRechargeComponent(){
    var root = document.querySelector('#app');
    var queue = root && root.__vue__ ? [root.__vue__] : [];
    var seen = [];
    while (queue.length) {
      var current = queue.shift();
      if (!current || seen.indexOf(current) >= 0) continue;
      seen.push(current);
      if (typeof current.rechargeOrders === 'function' && Array.isArray(current.cardList)) {
        return current;
      }
      if (current.${'$'}children) queue = queue.concat(current.${'$'}children);
    }
    return null;
  }
  function publish(){
    var component = findRechargeComponent();
    if (!component || !component.cardList.length) return;
    var account = component.cardList[component.cardIndex || 0] || component.cardList[0];
    var methods = (component.payType || []).map(function(item, index){
      return {
        pageIndex: index,
        name: String(item.payPrdName || ('支付方式 ' + (index + 1)))
      };
    });
    var payload = JSON.stringify({
      studentNumber: String(account.empno || ''),
      balance: Number(account.balance || 0),
      paymentMethods: methods
    });
    if (payload === window.__ahutongRechargeLastPayload) return;
    window.__ahutongRechargeLastPayload = payload;
    window.AhuTongRechargeBridge.onRechargeState(payload);
  }
  window.__ahutongPublishRechargeState = publish;
  if (!window.__ahutongRechargeStateObserverInstalled) {
    window.__ahutongRechargeStateObserverInstalled = true;
    window.setInterval(publish, 500);
  }
  publish();
})();
"""

private const val CMB_NATIVE_PAYMENT_UI_SCRIPT = """
(function(){
  function visible(node){
    if (!node) return false;
    var style = window.getComputedStyle(node);
    return style.display !== 'none' && style.visibility !== 'hidden' &&
      node.getClientRects().length > 0;
  }
  function notify(){
    var sheets = Array.from(document.querySelectorAll('.van-action-sheet'));
    var sheet = sheets.find(visible);
    var title = sheet
      ? ((sheet.querySelector('.van-action-sheet__header') || {}).innerText || '')
      : '';
    var passwordDots = sheet
      ? Array.from(sheet.querySelectorAll('.van-password-input__security i')).filter(visible).length
      : 0;
    var toast = Array.from(document.querySelectorAll('.van-toast--fail')).find(visible);
    window.AhuTongRechargeBridge.onPaymentUiState(JSON.stringify({
      passwordRequired: title.indexOf('查询密码') >= 0 && passwordDots === 0,
      error: toast ? (toast.innerText || '') : ''
    }));
  }
  if (!window.__ahutongPaymentUiObserverInstalled) {
    if (!document.body) return 'body-not-ready';
    var observer = new MutationObserver(notify);
    observer.observe(document.body, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ['style', 'class']
    });
    window.__ahutongPaymentUiObserverInstalled = true;
    window.__ahutongPaymentUiObserver = observer;
    window.setInterval(notify, 250);
  }
  notify();
})();
"""

internal enum class CmbRechargePaymentPhase {
    IDLE,
    LOADING,
    PASSWORD_REQUIRED,
    SUCCESS,
    ERROR
}

internal data class CmbRechargeAutomationState(
    val phase: CmbRechargePaymentPhase = CmbRechargePaymentPhase.IDLE,
    val errorMessage: String? = null
)

internal fun isCmbRechargeSessionFresh(
    readyAtElapsedMs: Long,
    nowElapsedMs: Long
): Boolean = readyAtElapsedMs > 0L &&
    nowElapsedMs >= readyAtElapsedMs &&
    nowElapsedMs - readyAtElapsedMs < CMB_RECHARGE_PRELOAD_VALIDITY_MS

internal fun canDispatchCmbRecharge(
    amount: String?,
    password: String?,
    hasFreshSession: Boolean
): Boolean = !amount.isNullOrBlank() &&
    !password.isNullOrBlank() &&
    hasFreshSession

internal fun canDispatchCmbPassword(
    password: String?,
    dispatchInProgress: Boolean,
    hasWebView: Boolean
): Boolean = !password.isNullOrBlank() && !dispatchInProgress && hasWebView

internal fun isCmbSessionExpiredMessage(message: String): Boolean {
    val normalized = message.trim().lowercase()
    return listOf(
        "登录失效",
        "登录已失效",
        "登录过期",
        "登录已过期",
        "登录超时",
        "会话失效",
        "会话已失效",
        "会话过期",
        "会话已过期",
        "请重新登录",
        "token失效",
        "token已失效"
    ).any(normalized::contains)
}

internal fun shouldRecoverCmbSession(
    message: String,
    recoveryAttempted: Boolean,
    amount: String?,
    password: String?
): Boolean = !recoveryAttempted &&
    !amount.isNullOrBlank() &&
    !password.isNullOrBlank() &&
    isCmbSessionExpiredMessage(message)

/**
 * Owns the hidden CMB WebView so the visible recharge screen can stay identical to the
 * existing Agricultural Bank flow. The WebView is attached invisibly to keep its Vue/JS
 * runtime alive, and every ready session is destroyed after three minutes.
 */
internal object CmbRechargeAutomationController {
    private const val TAG = "CmbRechargeAutomation"
    private const val PRELOAD_START_DELAY_MS = 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(CmbRechargeAutomationState())
    val state: StateFlow<CmbRechargeAutomationState> = _state.asStateFlow()

    private var webView: WebView? = null
    private var hostRoot: ViewGroup? = null
    private var nativeData: CmbRechargeNativeData? = null
    private var readyAtElapsedMs = 0L
    private var generation = 0
    private var pendingAmount: String? = null
    private var pendingPassword: String? = null
    private var submissionAmount: String? = null
    private var submissionPassword: String? = null
    private var submissionContext: Context? = null
    private var officialPasswordPromptVisible = false
    private var passwordDispatchInProgress = false
    private var sessionRecoveryAttempted = false
    private var userSubmissionActive = false
    private var scheduledPreloadJob: Job? = null
    private var sessionLoadJob: Job? = null
    private var expiryJob: Job? = null
    private var bootstrapTimeoutJob: Job? = null
    private var paymentTimeoutJob: Job? = null

    fun schedulePreload(activity: Activity) {
        if (
            AHUCache.getCardRechargeBank() != CardRechargeBank.CHINA_MERCHANTS_BANK ||
            !AHUCache.isLogin() ||
            hasFreshSession() ||
            sessionLoadJob?.isActive == true ||
            scheduledPreloadJob?.isActive == true
        ) {
            return
        }

        scheduledPreloadJob = scope.launch {
            delay(PRELOAD_START_DELAY_MS)
            if (
                AHUCache.getCardRechargeBank() != CardRechargeBank.CHINA_MERCHANTS_BANK ||
                !AHUCache.isLogin()
            ) {
                return@launch
            }
            loadSession(activity, deferWebViewCreationUntilIdle = true)
        }
    }

    fun onBankSelected(context: Context, bank: CardRechargeBank) {
        if (bank == CardRechargeBank.CHINA_MERCHANTS_BANK) {
            (context as? Activity)?.let(::schedulePreload)
        }
        // Keep a fresh CMB session alive while the user compares banks. Its existing
        // three-minute expiry remains authoritative and prevents repeated login traffic.
    }

    fun submit(context: Context, amount: String) {
        scope.launch {
            pendingAmount = amount
            pendingPassword = null
            submissionAmount = amount
            submissionPassword = null
            submissionContext = context
            officialPasswordPromptVisible = false
            passwordDispatchInProgress = false
            sessionRecoveryAttempted = false
            userSubmissionActive = true
            _state.value = CmbRechargeAutomationState(
                CmbRechargePaymentPhase.PASSWORD_REQUIRED
            )
        }
    }

    fun submitPassword(password: String) {
        if (!userSubmissionActive || pendingAmount == null && !officialPasswordPromptVisible) {
            failUserSubmission("招商银行充值会话已失效，请重试")
            return
        }

        pendingPassword = password
        submissionPassword = password
        _state.value = CmbRechargeAutomationState(CmbRechargePaymentPhase.LOADING)
        if (officialPasswordPromptVisible) {
            dispatchPendingPassword()
            return
        }

        val context = submissionContext
        if (context == null) {
            failUserSubmission("招商银行充值会话已失效，请重试")
        } else if (!hasFreshSession()) {
            destroySession()
            loadSession(context, deferWebViewCreationUntilIdle = false)
        } else {
            dispatchPendingRecharge()
        }
    }

    fun cancelPassword() {
        paymentTimeoutJob?.cancel()
        if (officialPasswordPromptVisible) webView?.cancelCmbRechargePassword()
        pendingAmount = null
        pendingPassword = null
        submissionAmount = null
        submissionPassword = null
        submissionContext = null
        officialPasswordPromptVisible = false
        passwordDispatchInProgress = false
        sessionRecoveryAttempted = false
        userSubmissionActive = false
        _state.value = CmbRechargeAutomationState()
    }

    fun resetPaymentState() {
        paymentTimeoutJob?.cancel()
        pendingAmount = null
        pendingPassword = null
        submissionAmount = null
        submissionPassword = null
        submissionContext = null
        officialPasswordPromptVisible = false
        passwordDispatchInProgress = false
        sessionRecoveryAttempted = false
        userSubmissionActive = false
        _state.value = CmbRechargeAutomationState()
    }

    fun discard() {
        scope.launch {
            scheduledPreloadJob?.cancel()
            sessionLoadJob?.cancel()
            pendingAmount = null
            pendingPassword = null
            submissionAmount = null
            submissionPassword = null
            submissionContext = null
            officialPasswordPromptVisible = false
            passwordDispatchInProgress = false
            sessionRecoveryAttempted = false
            userSubmissionActive = false
            destroySession()
            _state.value = CmbRechargeAutomationState()
        }
    }

    private fun hasFreshSession(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean =
        webView != null &&
            nativeData != null &&
            isCmbRechargeNativeEntryUrl(webView?.url) &&
            isCmbRechargeSessionFresh(readyAtElapsedMs, nowElapsedMs)

    private fun loadSession(context: Context, deferWebViewCreationUntilIdle: Boolean) {
        if (sessionLoadJob?.isActive == true) return
        val activity = context as? Activity
        val applicationContext = context.applicationContext
        sessionLoadJob = scope.launch {
            val token = withContext(Dispatchers.IO) { TokenManager.awaitToken() }
            if (token.isNullOrBlank()) {
                handleSessionLoadFailure("校园卡登录凭证暂未就绪，请稍后重试")
                return@launch
            }

            if (deferWebViewCreationUntilIdle && pendingAmount == null) {
                awaitMainThreadIdle()
            }
            if (
                pendingAmount == null &&
                AHUCache.getCardRechargeBank() != CardRechargeBank.CHINA_MERCHANTS_BANK
            ) {
                return@launch
            }

            createAndLoadSession(
                context = activity ?: applicationContext,
                entryUrl = buildCmbRechargeEntryUrl(token)
            )
        }
    }

    private fun createAndLoadSession(context: Context, entryUrl: String) {
        destroySession()
        generation += 1
        val sessionGeneration = generation
        lateinit var createdView: WebView
        createdView = createCmbRechargeWebView(
            context = context,
            pageBackgroundColor = android.graphics.Color.TRANSPARENT,
            pageStyleScript = { "" },
            onLoadingChanged = {},
            onProgressChanged = {},
            onSuccessPageChanged = {},
            onSuccessReturnBoundsChanged = { bounds ->
                if (
                    sessionGeneration == generation &&
                    shouldConfirmCmbRechargeSuccess(createdView.url, bounds)
                ) {
                    completeUserSubmission()
                }
            },
            onNativeDataChanged = { data ->
                if (sessionGeneration != generation) return@createCmbRechargeWebView
                nativeData = data
                readyAtElapsedMs = SystemClock.elapsedRealtime()
                bootstrapTimeoutJob?.cancel()
                scheduleExpiry(sessionGeneration)
                dispatchPendingRecharge()
            },
            onPaymentUiStateChanged = { requiresPassword, pageError ->
                if (sessionGeneration != generation || !userSubmissionActive) {
                    return@createCmbRechargeWebView
                }
                if (pageError.isNotBlank()) {
                    if (!recoverSubmissionAfterSessionExpiry(pageError)) {
                        failUserSubmission(pageError)
                    }
                } else if (requiresPassword) {
                    officialPasswordPromptVisible = true
                    dispatchPendingPassword()
                }
            },
            onPageChanged = { url ->
                Log.d(TAG, "CMB navigation: ${safeCmbPageLocation(url)}")
            },
            onMainFrameError = { message ->
                if (sessionGeneration == generation) handleSessionLoadFailure(message)
            },
            onExternalLink = {
                if (sessionGeneration == generation) {
                    handleSessionLoadFailure("招商银行充值需要打开未受支持的外部页面，请重试")
                }
            },
            onSubmitIntent = {}
        )
        createdView.updateCmbRechargeWebViewVisibility(false)
        attachHiddenWebView(context as? Activity, createdView)
        syncYcardCookiesToWebView(createdView)
        createdView.cmbRechargeState?.requestVersion = sessionGeneration
        webView = createdView
        createdView.loadUrl(entryUrl)

        bootstrapTimeoutJob = scope.launch {
            delay(CMB_NATIVE_BOOTSTRAP_TIMEOUT_MS)
            if (sessionGeneration == generation && nativeData == null) {
                handleSessionLoadFailure("招商银行充值页面加载超时，请重试")
            }
        }
    }

    private fun attachHiddenWebView(activity: Activity?, view: WebView) {
        val root = activity?.findViewById<ViewGroup>(android.R.id.content) ?: return
        hostRoot = root
        root.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun dispatchPendingRecharge() {
        val amount = pendingAmount ?: return
        if (!canDispatchCmbRecharge(amount, pendingPassword, hasFreshSession())) {
            if (pendingPassword == null) {
                _state.value = CmbRechargeAutomationState(
                    CmbRechargePaymentPhase.PASSWORD_REQUIRED
                )
            }
            return
        }
        val data = nativeData ?: return
        val currentView = webView ?: return
        val paymentMethod = data.paymentMethods.firstOrNull()
        if (paymentMethod == null) {
            failUserSubmission("招商银行未找到可用的绑定银行卡")
            return
        }

        pendingAmount = null
        _state.value = CmbRechargeAutomationState(CmbRechargePaymentPhase.LOADING)
        startPaymentTimeout("招商银行充值请求超时，请重试")
        currentView.submitCmbRecharge(
            amount = amount,
            paymentMethodIndex = paymentMethod.pageIndex,
            onRejected = ::failUserSubmission
        )
    }

    private fun dispatchPendingPassword() {
        if (passwordDispatchInProgress) return
        val password = pendingPassword
        val currentView = webView
        if (
            !canDispatchCmbPassword(
                password = password,
                dispatchInProgress = passwordDispatchInProgress,
                hasWebView = currentView != null
            )
        ) {
            _state.value = CmbRechargeAutomationState(
                CmbRechargePaymentPhase.PASSWORD_REQUIRED
            )
            return
        }
        val dispatchPassword = password ?: return
        val dispatchView = currentView ?: return

        passwordDispatchInProgress = true
        pendingPassword = null
        _state.value = CmbRechargeAutomationState(CmbRechargePaymentPhase.LOADING)
        startPaymentTimeout("查询密码提交超时，请重试")
        dispatchView.submitCmbRechargePassword(
            password = dispatchPassword,
            onRejected = ::failUserSubmission
        )
    }

    private fun completeUserSubmission() {
        if (!userSubmissionActive) return
        paymentTimeoutJob?.cancel()
        pendingAmount = null
        pendingPassword = null
        submissionAmount = null
        submissionPassword = null
        submissionContext = null
        officialPasswordPromptVisible = false
        passwordDispatchInProgress = false
        sessionRecoveryAttempted = false
        userSubmissionActive = false
        _state.value = CmbRechargeAutomationState(CmbRechargePaymentPhase.SUCCESS)
        scope.launch {
            destroySession()
        }
    }

    private fun startPaymentTimeout(message: String) {
        paymentTimeoutJob?.cancel()
        paymentTimeoutJob = scope.launch {
            delay(CMB_PASSWORD_DISPATCH_TIMEOUT_MS)
            if (userSubmissionActive) failUserSubmission(message)
        }
    }

    private fun recoverSubmissionAfterSessionExpiry(message: String): Boolean {
        val amount = submissionAmount
        val password = submissionPassword
        val context = submissionContext
        if (
            context == null ||
            !shouldRecoverCmbSession(
                message = message,
                recoveryAttempted = sessionRecoveryAttempted,
                amount = amount,
                password = password
            )
        ) {
            return false
        }

        sessionRecoveryAttempted = true
        paymentTimeoutJob?.cancel()
        pendingAmount = amount
        pendingPassword = password
        officialPasswordPromptVisible = false
        passwordDispatchInProgress = false
        _state.value = CmbRechargeAutomationState(CmbRechargePaymentPhase.LOADING)
        destroySession()
        loadSession(context, deferWebViewCreationUntilIdle = false)
        return true
    }

    private fun failUserSubmission(message: String) {
        paymentTimeoutJob?.cancel()
        pendingAmount = null
        pendingPassword = null
        submissionAmount = null
        submissionPassword = null
        submissionContext = null
        officialPasswordPromptVisible = false
        passwordDispatchInProgress = false
        sessionRecoveryAttempted = false
        userSubmissionActive = false
        _state.value = CmbRechargeAutomationState(
            phase = CmbRechargePaymentPhase.ERROR,
            errorMessage = message
        )
        scope.launch { destroySession() }
    }

    private fun handleSessionLoadFailure(message: String) {
        if (userSubmissionActive) {
            failUserSubmission(message)
        } else {
            Log.w(TAG, message)
            scope.launch { destroySession() }
        }
    }

    private fun scheduleExpiry(sessionGeneration: Int) {
        expiryJob?.cancel()
        expiryJob = scope.launch {
            delay(CMB_RECHARGE_PRELOAD_VALIDITY_MS)
            if (sessionGeneration == generation && !userSubmissionActive) {
                Log.d(TAG, "Discarding expired three-minute CMB preload session")
                destroySession()
            }
        }
    }

    private fun destroySession() {
        expiryJob?.cancel()
        expiryJob = null
        bootstrapTimeoutJob?.cancel()
        bootstrapTimeoutJob = null
        paymentTimeoutJob?.cancel()
        paymentTimeoutJob = null
        passwordDispatchInProgress = false
        nativeData = null
        readyAtElapsedMs = 0L
        generation += 1

        val currentView = webView
        webView = null
        currentView?.cmbRechargeState?.dispose()
        (currentView?.parent as? ViewGroup)?.removeView(currentView)
        hostRoot = null
        currentView?.stopLoading()
        currentView?.removeAllViews()
        currentView?.destroy()
    }

    private suspend fun awaitMainThreadIdle() {
        suspendCancellableCoroutine { continuation ->
            val queue = Looper.myQueue()
            val idleHandler = MessageQueue.IdleHandler {
                if (continuation.isActive) continuation.resume(Unit)
                false
            }
            queue.addIdleHandler(idleHandler)
            continuation.invokeOnCancellation { queue.removeIdleHandler(idleHandler) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CmbCardRecharge(
    onExit: () -> Unit,
    onRechargeSuccessExit: () -> Unit
) {
    val context = LocalContext.current
    val behaviorReporter = rememberBehaviorActionReporter()
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val pageBackgroundColor = colorScheme.background
    val pageStylePalette = CmbRechargePagePalette(
        colorScheme = if (isDarkTheme) "dark" else "light",
        background = pageBackgroundColor.toCssColor(),
        surface = colorScheme.surface.toCssColor(),
        surfaceVariant = colorScheme.surfaceVariant.toCssColor(),
        text = colorScheme.onBackground.toCssColor(),
        secondaryText = colorScheme.onSurfaceVariant.toCssColor(),
        outline = colorScheme.outline.toCssColor(),
        accent = colorScheme.primary.toCssColor(),
        onAccent = colorScheme.onPrimary.toCssColor(),
        success = (if (isDarkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)).toCssColor(),
        scrim = if (isDarkTheme) "rgba(0, 0, 0, 0.62)" else "rgba(0, 0, 0, 0.38)"
    )
    val pageStyleScript = remember(pageStylePalette) {
        buildCmbRechargeStyleScript(pageStylePalette)
    }
    val latestPageStyleScript = rememberUpdatedState(pageStyleScript)
    val latestRechargeSuccessExit = rememberUpdatedState(onRechargeSuccessExit)
    var entryUrl by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var tokenRequestVersion by remember { mutableIntStateOf(0) }
    var loadRequestVersion by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isRechargeSuccessPage by remember { mutableStateOf(false) }
    var nativeData by remember { mutableStateOf<CmbRechargeNativeData?>(null) }
    var showWebContent by remember { mutableStateOf(false) }
    var forceWebContent by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var successReturnBounds by remember {
        mutableStateOf<CmbRechargeNormalizedBounds?>(null)
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var queryPasswordRequired by remember { mutableStateOf(false) }
    var nativeSuccess by remember { mutableStateOf(false) }
    var isPasswordDispatching by remember { mutableStateOf(false) }
    var allowWebContentReveal by remember { mutableStateOf(false) }
    val isWebContentVisible = showWebContent || forceWebContent

    fun reloadEntry() {
        progress = 0
        isLoading = true
        errorMessage = null
        isRechargeSuccessPage = false
        nativeData = null
        showWebContent = false
        forceWebContent = false
        isSubmitting = false
        queryPasswordRequired = false
        nativeSuccess = false
        isPasswordDispatching = false
        allowWebContentReveal = false
        successReturnBounds = null
        webView?.stopLoading()
        loadRequestVersion += 1
    }

    val handleBack: () -> Unit = {
        val currentWebView = webView
        if (nativeSuccess) {
            latestRechargeSuccessExit.value()
        } else if (forceWebContent && isCmbRechargeNativeEntryUrl(currentWebView?.url)) {
            forceWebContent = false
            allowWebContentReveal = false
        } else if (isWebContentVisible && currentWebView?.canGoBack() == true) {
            currentWebView.goBack()
        } else if (isWebContentVisible) {
            reloadEntry()
        } else {
            onExit()
        }
    }
    BackHandler(onBack = handleBack)

    LaunchedEffect(tokenRequestVersion) {
        progress = 0
        isLoading = true
        errorMessage = null
        entryUrl = null
        isRechargeSuccessPage = false
        nativeData = null
        showWebContent = false
        forceWebContent = false
        isSubmitting = false
        queryPasswordRequired = false
        nativeSuccess = false
        isPasswordDispatching = false
        allowWebContentReveal = false
        successReturnBounds = null
        val token = withContext(Dispatchers.IO) { TokenManager.awaitToken() }
        if (token.isNullOrBlank()) {
            errorMessage = "校园卡登录凭证暂未就绪，请稍后重试"
            isLoading = false
            return@LaunchedEffect
        }
        entryUrl = buildCmbRechargeEntryUrl(token)
        loadRequestVersion += 1
    }

    DisposableEffect(Unit) {
        onDispose {
            val currentView = webView
            currentView?.stopLoading()
            currentView?.cmbRechargeState?.dispose()
            currentView?.destroy()
            webView = null
        }
    }

    LaunchedEffect(pageStyleScript) {
        webView?.let { currentView ->
            applyCmbRechargePageStyle(currentView, currentView.url, pageStyleScript)
            currentView.cmbRechargeState?.boundsLocator?.locate(currentView.url)
        }
    }

    LaunchedEffect(entryUrl, loadRequestVersion, nativeData, errorMessage, nativeSuccess) {
        if (
            entryUrl == null ||
            nativeData != null ||
            errorMessage != null ||
            nativeSuccess
        ) {
            return@LaunchedEffect
        }
        delay(CMB_NATIVE_BOOTSTRAP_TIMEOUT_MS)
        if (nativeData == null && errorMessage == null && !nativeSuccess) {
            isLoading = false
            errorMessage = "充值信息加载超时，请检查网络后重试"
        }
    }

    LaunchedEffect(isPasswordDispatching) {
        if (!isPasswordDispatching) return@LaunchedEffect
        delay(CMB_PASSWORD_DISPATCH_TIMEOUT_MS)
        if (isPasswordDispatching) {
            webView?.cancelCmbRechargePassword()
            isPasswordDispatching = false
            isSubmitting = false
            allowWebContentReveal = false
            errorMessage = "查询密码提交超时，请重试"
        }
    }

    LaunchedEffect(isRechargeSuccessPage, nativeSuccess) {
        if (!isRechargeSuccessPage || nativeSuccess) return@LaunchedEffect
        delay(CMB_SUCCESS_CONFIRMATION_TIMEOUT_MS)
        if (isRechargeSuccessPage && !nativeSuccess) {
            isSubmitting = false
            allowWebContentReveal = true
            showWebContent = true
            forceWebContent = true
        }
    }

    val pageContentColor = colorScheme.onBackground
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = pageBackgroundColor,
        contentColor = pageContentColor,
        topBar = {
            AppPageHeader(
                title = "招商银行充值",
                onBack = handleBack,
                modifier = Modifier
                    .zIndex(1f)
                    .statusBarsPadding()
            )
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .background(pageBackgroundColor)
                .clipToBounds()
        ) {
            entryUrl?.let { url ->
                val requestVersion = loadRequestVersion
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                    factory = { viewContext ->
                        createCmbRechargeWebView(
                            context = viewContext,
                            pageBackgroundColor = pageBackgroundColor.toArgb(),
                            pageStyleScript = { latestPageStyleScript.value },
                            onLoadingChanged = { isLoading = it },
                            onProgressChanged = { progress = it },
                            onSuccessPageChanged = { isSuccessPage ->
                                isRechargeSuccessPage = isSuccessPage
                                if (!isSuccessPage) {
                                    nativeSuccess = false
                                    successReturnBounds = null
                                }
                            },
                            onSuccessReturnBoundsChanged = { bounds ->
                                successReturnBounds = bounds
                                if (shouldConfirmCmbRechargeSuccess(webView?.url, bounds)) {
                                    nativeSuccess = true
                                    errorMessage = null
                                    isSubmitting = false
                                    queryPasswordRequired = false
                                    isPasswordDispatching = false
                                    allowWebContentReveal = false
                                    showWebContent = false
                                    forceWebContent = false
                                }
                            },
                            onNativeDataChanged = { pageData ->
                                nativeData = pageData
                                errorMessage = null
                                showWebContent = false
                            },
                            onPaymentUiStateChanged = { requiresPassword, pageError ->
                                queryPasswordRequired = requiresPassword && !isPasswordDispatching
                                if (pageError.isNotBlank()) {
                                    errorMessage = pageError
                                    isSubmitting = false
                                    isPasswordDispatching = false
                                    allowWebContentReveal = false
                                }
                            },
                            onPageChanged = { currentUrl ->
                                if (!isCmbRechargeNativeEntryUrl(currentUrl)) {
                                    queryPasswordRequired = false
                                    isPasswordDispatching = false
                                }
                                if (
                                    isCmbRechargeHiddenFlowUrl(currentUrl) ||
                                    isCmbRechargeSuccessUrl(currentUrl)
                                ) {
                                    if (showWebContent) {
                                        forceWebContent = false
                                        allowWebContentReveal = false
                                    }
                                    showWebContent = false
                                } else if (isCmbRechargeInsecureEntryUrl(currentUrl)) {
                                    allowWebContentReveal = true
                                    showWebContent = true
                                    forceWebContent = true
                                    isSubmitting = false
                                } else if (
                                    shouldRevealCmbRechargeWebContent(
                                        url = currentUrl,
                                        revealAllowed = allowWebContentReveal
                                    )
                                ) {
                                    showWebContent = true
                                    isSubmitting = false
                                } else {
                                    showWebContent = false
                                }
                            },
                            onMainFrameError = { error ->
                                errorMessage = error
                                isRechargeSuccessPage = false
                                nativeSuccess = false
                                successReturnBounds = null
                                isSubmitting = false
                                queryPasswordRequired = false
                                isPasswordDispatching = false
                                showWebContent = false
                                forceWebContent = false
                                allowWebContentReveal = false
                            },
                            onExternalLink = { externalUrl ->
                                openExternalLink(context, externalUrl)
                            },
                            onSubmitIntent = {
                                behaviorReporter.organic(AppActionId.SUBMIT_CMB_CARD_RECHARGE)
                            }
                        ).also { created ->
                            created.updateCmbRechargeWebViewVisibility(isWebContentVisible)
                            syncYcardCookiesToWebView(created)
                            created.cmbRechargeState?.requestVersion = requestVersion
                            created.loadUrl(url)
                            webView = created
                        }
                    },
                    update = { currentView ->
                        currentView.setBackgroundColor(pageBackgroundColor.toArgb())
                        currentView.updateCmbRechargeWebViewVisibility(isWebContentVisible)
                        if (currentView.cmbRechargeState?.requestVersion != requestVersion) {
                            syncYcardCookiesToWebView(currentView)
                            currentView.cmbRechargeState?.requestVersion = requestVersion
                            currentView.loadUrl(url)
                        }
                        webView = currentView
                    }
                )
            }

            if (isRechargeSuccessPage && isWebContentVisible) {
                successReturnBounds?.let { bounds ->
                    CmbRechargeSuccessReturnOverlay(
                        bounds = bounds,
                        onClick = {
                            successReturnBounds = null
                            latestRechargeSuccessExit.value()
                        }
                    )
                }
            }

            if (!isWebContentVisible && nativeSuccess) {
                CmbRechargeNativeSuccessPanel(onDone = latestRechargeSuccessExit.value)
            } else if (!isWebContentVisible) {
                CmbRechargeNativePanel(
                    data = nativeData,
                    errorMessage = errorMessage,
                    isSubmitting = isSubmitting,
                    onRetry = {
                        if (entryUrl == null) {
                            tokenRequestVersion += 1
                        } else {
                            reloadEntry()
                        }
                    },
                    onManagePaymentMethods = {
                        errorMessage = null
                        allowWebContentReveal = true
                        forceWebContent = true
                    },
                    onSubmit = { amount, paymentMethodIndex ->
                        behaviorReporter.organic(AppActionId.SUBMIT_CMB_CARD_RECHARGE)
                        errorMessage = null
                        isSubmitting = true
                        allowWebContentReveal = true
                        forceWebContent = false
                        webView?.submitCmbRecharge(
                            amount = amount,
                            paymentMethodIndex = paymentMethodIndex,
                            onRejected = { message ->
                                isSubmitting = false
                                allowWebContentReveal = false
                                forceWebContent = false
                                errorMessage = message
                            }
                        )
                    }
                )
            }

            if (queryPasswordRequired) {
                CmbRechargeQueryPasswordDialog(
                    onCancel = {
                        queryPasswordRequired = false
                        isSubmitting = false
                        isPasswordDispatching = false
                        allowWebContentReveal = false
                        webView?.cancelCmbRechargePassword()
                    },
                    onConfirm = { password ->
                        queryPasswordRequired = false
                        isPasswordDispatching = true
                        webView?.submitCmbRechargePassword(
                            password = password,
                            onRejected = { message ->
                                isSubmitting = false
                                isPasswordDispatching = false
                                allowWebContentReveal = false
                                errorMessage = message
                            }
                        )
                    }
                )
            }

            if (isWebContentVisible && isLoading) {
                AppCircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = colorScheme.primary
                )
            }

            if (isWebContentVisible && progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                )
            }

            if (isWebContentVisible) errorMessage?.let { message ->
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .fillMaxWidth()
                        .background(colorScheme.surface, SmoothRoundedCornerShape(24.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = message,
                        color = pageContentColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "重试",
                        modifier = Modifier.clickable {
                            if (entryUrl == null) {
                                errorMessage = null
                                isLoading = true
                                tokenRequestVersion += 1
                            } else {
                                reloadEntry()
                            }
                        },
                        color = colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun CmbRechargeSuccessReturnOverlay(
    bounds: CmbRechargeNormalizedBounds,
    onClick: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .absoluteOffset(
                    x = maxWidth * bounds.left,
                    y = maxHeight * bounds.top
                )
                .width(maxWidth * bounds.width)
                .height(maxHeight * bounds.height)
                .clip(SmoothRoundedCornerShape(20.dp))
                .clickable(
                    onClickLabel = "返回应用首页",
                    role = Role.Button,
                    onClick = onClick
                )
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createCmbRechargeWebView(
    context: android.content.Context,
    pageBackgroundColor: Int,
    pageStyleScript: () -> String,
    onLoadingChanged: (Boolean) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onSuccessPageChanged: (Boolean) -> Unit,
    onSuccessReturnBoundsChanged: (CmbRechargeNormalizedBounds?) -> Unit,
    onNativeDataChanged: (CmbRechargeNativeData) -> Unit,
    onPaymentUiStateChanged: (requiresPassword: Boolean, error: String) -> Unit,
    onPageChanged: (String?) -> Unit,
    onMainFrameError: (String) -> Unit,
    onExternalLink: (String) -> Unit,
    onSubmitIntent: () -> Unit
): WebView {
    return WebView(context).apply {
        setBackgroundColor(pageBackgroundColor)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.saveFormData = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        }
        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
        addJavascriptInterface(CmbBehaviorBridge(this, onSubmitIntent), "AhuTongBehaviorBridge")
        addJavascriptInterface(
            CmbRechargeStateBridge(this, onNativeDataChanged, onPaymentUiStateChanged),
            "AhuTongRechargeBridge"
        )
        val boundsLocator = CmbRechargeBoundsLocator(this, onSuccessReturnBoundsChanged)
        tag = CmbRechargeWebViewState(boundsLocator = boundsLocator)

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgressChanged(newProgress)
                if (newProgress >= 100) {
                    onLoadingChanged(false)
                    if (view != null && isCmbRechargeNativeEntryUrl(view.url)) {
                        view.evaluateJavascript(CMB_NATIVE_STATE_BRIDGE_SCRIPT, null)
                    }
                }
            }
        }

        webViewClient = object : WebViewClient() {
            private fun updateSuccessPage(url: String?): Boolean {
                val isSuccessPage = isCmbRechargeSuccessUrl(url)
                onSuccessPageChanged(isSuccessPage)
                if (!isSuccessPage) boundsLocator.clear()
                return isSuccessPage
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val targetUri = request?.url ?: return false
                val scheme = targetUri.scheme?.lowercase().orEmpty()
                if (scheme.isBlank()) return false
                if (scheme != "http" && scheme != "https") {
                    onExternalLink(targetUri.toString())
                    return true
                }
                val upgradedCashierUrl = buildCmbHttpsCashierUrl(targetUri.toString())
                if (upgradedCashierUrl != null && upgradedCashierUrl != targetUri.toString()) {
                    view?.loadUrl(upgradedCashierUrl)
                    return true
                }
                return if (isCmbRechargeAllowedMainFrameUrl(targetUri.toString())) {
                    false
                } else {
                    Log.w(
                        "CmbRechargeNavigation",
                        "Blocked main-frame navigation to ${safeCmbPageLocation(targetUri.toString())}"
                    )
                    onExternalLink(targetUri.toString())
                    true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                onLoadingChanged(true)
                boundsLocator.clear()
                updateSuccessPage(url)
                onPageChanged(url)
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                onLoadingChanged(false)
                updateSuccessPage(url)
                onPageChanged(url)
                if (view != null) {
                    if (url?.contains("synjones-auth", ignoreCase = true) == false) {
                        // Do not retain the short-lived bootstrap token URL in WebView history.
                        view.clearHistory()
                    }
                    applyCmbRechargePageStyle(view, url, pageStyleScript())
                    if (url?.let(Uri::parse)?.let(::isAuditedCmbSubmitPage) == true) {
                        view.evaluateJavascript(CMB_SUBMIT_OBSERVER_SCRIPT, null)
                    }
                    if (isCmbRechargeNativeEntryUrl(url)) {
                        view.evaluateJavascript(CMB_NATIVE_STATE_BRIDGE_SCRIPT, null)
                        view.evaluateJavascript(CMB_NATIVE_PAYMENT_UI_SCRIPT, null)
                    }
                    boundsLocator.locate(url)
                }
                super.onPageFinished(view, url)
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                onPageChanged(url)
                if (view != null && isCmbRechargeNativeEntryUrl(url)) {
                    view.evaluateJavascript(CMB_NATIVE_STATE_BRIDGE_SCRIPT, null)
                    view.evaluateJavascript(CMB_NATIVE_PAYMENT_UI_SCRIPT, null)
                }
                super.onPageCommitVisible(view, url)
            }

            override fun doUpdateVisitedHistory(
                view: WebView?,
                url: String?,
                isReload: Boolean
            ) {
                onPageChanged(url)
                if (view != null && isCmbRechargeNativeEntryUrl(url)) {
                    view.evaluateJavascript(CMB_NATIVE_STATE_BRIDGE_SCRIPT, null)
                    view.evaluateJavascript(CMB_NATIVE_PAYMENT_UI_SCRIPT, null)
                }
                if (updateSuccessPage(url) && view != null) boundsLocator.locate(url)
                super.doUpdateVisitedHistory(view, url, isReload)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    onLoadingChanged(false)
                    boundsLocator.clear()
                    onSuccessPageChanged(false)
                    onMainFrameError(error?.description?.toString() ?: "页面加载失败，请稍后重试")
                }
                super.onReceivedError(view, request, error)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request?.isForMainFrame == true) {
                    val statusCode = errorResponse?.statusCode
                    val pageLocation = safeCmbPageLocation(request.url?.toString())
                    if (statusCode == 412 && isCmbLoginRedirectUrl(request.url?.toString())) {
                        Log.w(
                            "CmbRechargeHttp",
                            "CMB login redirect returned HTTP 412; awaiting page retry"
                        )
                        onLoadingChanged(true)
                        super.onReceivedHttpError(view, request, errorResponse)
                        return
                    }

                    val upgradedCashierUrl = if (statusCode == 412) {
                        buildCmbHttpsCashierUrl(request.url?.toString())
                    } else {
                        null
                    }
                    if (upgradedCashierUrl != null) {
                        Log.w(
                            "CmbRechargeHttp",
                            "Upgrading CMB cashier navigation to HTTPS after HTTP 412"
                        )
                        view?.loadUrl(upgradedCashierUrl)
                        super.onReceivedHttpError(view, request, errorResponse)
                        return
                    }

                    Log.w("CmbRechargeHttp", "main-frame HTTP $statusCode at $pageLocation")
                    onLoadingChanged(false)
                    boundsLocator.clear()
                    onSuccessPageChanged(false)
                    onMainFrameError(
                        if (statusCode != null) {
                            "页面加载失败（HTTP $statusCode，$pageLocation），请稍后重试"
                        } else {
                            "页面加载失败，请稍后重试"
                        }
                    )
                }
                super.onReceivedHttpError(view, request, errorResponse)
            }
        }
    }
}

private data class CmbRechargeBridgePayload(
    val studentNumber: String = "",
    val balance: Double = 0.0,
    val paymentMethods: List<CmbRechargeBridgePaymentMethod> = emptyList()
)

private data class CmbRechargeBridgePaymentMethod(
    val pageIndex: Int = -1,
    val name: String = ""
)

private class CmbRechargeStateBridge(
    private val webView: WebView,
    private val onNativeDataChanged: (CmbRechargeNativeData) -> Unit,
    private val onPaymentUiStateChanged: (Boolean, String) -> Unit
) {
    private val gson = Gson()

    @JavascriptInterface
    fun onRechargeState(payload: String) {
        webView.post {
            if (!isCmbRechargeNativeEntryUrl(webView.url)) return@post
            val parsed = runCatching {
                gson.fromJson(payload, CmbRechargeBridgePayload::class.java)
            }.getOrNull() ?: return@post
            val methods = parsed.paymentMethods
                .filter { it.pageIndex >= 0 && it.name.isNotBlank() }
                .distinctBy { it.pageIndex }
                .map { CmbRechargePaymentMethod(pageIndex = it.pageIndex, name = it.name) }
            onNativeDataChanged(
                CmbRechargeNativeData(
                    studentNumber = parsed.studentNumber,
                    balance = normalizeCmbRechargeBalance(parsed.balance),
                    paymentMethods = methods
                )
            )
        }
    }

    @JavascriptInterface
    fun onPaymentUiState(payload: String) {
        webView.post {
            if (!isCmbRechargeNativeEntryUrl(webView.url)) return@post
            val parsed = runCatching {
                gson.fromJson(payload, CmbPaymentUiPayload::class.java)
            }.getOrNull() ?: return@post
            onPaymentUiStateChanged(parsed.passwordRequired, parsed.error.orEmpty())
        }
    }
}

private data class CmbPaymentUiPayload(
    val passwordRequired: Boolean = false,
    val error: String? = null
)

private class CmbBehaviorBridge(
    private val webView: WebView,
    private val onSubmitIntent: () -> Unit
) {
    private var lastAcceptedAtElapsedMs = 0L

    @JavascriptInterface
    fun onSubmitIntent() {
        webView.post {
            val current = webView.url?.let(Uri::parse)
            val now = SystemClock.elapsedRealtime()
            if (current?.let(::isAuditedCmbSubmitPage) == true &&
                now - lastAcceptedAtElapsedMs >= NATIVE_SUBMIT_DEBOUNCE_MS
            ) {
                lastAcceptedAtElapsedMs = now
                onSubmitIntent()
            }
        }
    }

    private companion object { const val NATIVE_SUBMIT_DEBOUNCE_MS = 1_000L }
}

private fun WebView.submitCmbRecharge(
    amount: String,
    paymentMethodIndex: Int,
    onRejected: (String) -> Unit
) {
    val amountValue = amount.toDoubleOrNull()
    if (
        !isCmbRechargeNativeEntryUrl(url) ||
        amountValue == null ||
        amountValue <= 0.0 ||
        amountValue > 1_000.0 ||
        paymentMethodIndex < 0
    ) {
        onRejected("充值页面状态已变化，请重试")
        return
    }
    val script = """
        (function(){
          var root = document.querySelector('#app');
          var queue = root && root.__vue__ ? [root.__vue__] : [];
          var seen = [];
          var component = null;
          while (queue.length) {
            var current = queue.shift();
            if (!current || seen.indexOf(current) >= 0) continue;
            seen.push(current);
            if (typeof current.rechargeOrders === 'function' && Array.isArray(current.payType)) {
              component = current;
              break;
            }
            var children = current[String.fromCharCode(36) + 'children'];
            if (children) queue = queue.concat(children);
          }
          if (!component || !component.payType[$paymentMethodIndex]) return 'not-ready';
          component.tranAmt = $amountValue;
          component.payTypeIndex = $paymentMethodIndex;
          component.cardIndex = 0;
          component.charge();
          return 'submitted';
        })();
    """.trimIndent()
    evaluateJavascript(script) { result ->
        if (result != "\"submitted\"") {
            onRejected("充值页面尚未准备好，请稍后重试")
        }
    }
}

private fun WebView.submitCmbRechargePassword(
    password: String,
    onRejected: (String) -> Unit
) {
    if (
        !isCmbRechargeNativeEntryUrl(url) ||
        password.length != 6 ||
        !password.all(Char::isDigit)
    ) {
        onRejected("请输入 6 位校园卡查询密码")
        return
    }
    val escapedPassword = password
        .replace("\\", "\\\\")
        .replace("'", "\\'")
    val script = """
        (function(){
          function visible(node) {
            if (!node) return false;
            var style = window.getComputedStyle(node);
            return style.display !== 'none' &&
              style.visibility !== 'hidden' &&
              node.getClientRects().length > 0;
          }
          var sheet = Array.from(document.querySelectorAll('.van-action-sheet')).find(visible);
          if (!sheet || !(sheet.innerText || '').includes('查询密码')) return 'not-ready';
          var existingDots = Array.from(
            sheet.querySelectorAll('.van-password-input__security i')
          ).filter(visible).length;
          if (existingDots !== 0) {
            return 'password-not-empty';
          }
          function currentSheet() {
            return Array.from(document.querySelectorAll('.van-action-sheet')).find(visible);
          }
          function findCurrentKey(value) {
            var current = currentSheet();
            return current && Array.from(current.querySelectorAll('.keyboard td')).find(function(node) {
              return (node.innerText || '').trim() === value;
            });
          }
          function visiblePasswordDots() {
            var current = currentSheet();
            return current
              ? Array.from(current.querySelectorAll('.van-password-input__security i'))
                  .filter(visible).length
              : 0;
          }
          function fail(message) {
            window.AhuTongRechargeBridge.onPaymentUiState(JSON.stringify({
              passwordRequired: false,
              error: message
            }));
          }
          var password = '$escapedPassword';
          if (Array.from(password).some(function(value) { return !findCurrentKey(value); })) {
            return 'key-not-found';
          }
          if (!findCurrentKey('确认')) return 'confirm-not-found';
          function pressAt(index) {
            if (index >= password.length) {
              var confirm = findCurrentKey('确认');
              if (confirm && visiblePasswordDots() === password.length) {
                confirm.click();
              } else {
                fail('查询密码键盘状态异常，请重试');
              }
              return;
            }
            var key = findCurrentKey(password[index]);
            if (!key) {
              fail('查询密码键盘已变化，请重试');
              return;
            }
            key.click();
            var attempts = 0;
            function waitForDot() {
              if (visiblePasswordDots() >= index + 1) {
                window.setTimeout(function() { pressAt(index + 1); }, 120);
              } else if (attempts++ < 15) {
                window.setTimeout(waitForDot, 50);
              } else {
                fail('查询密码键盘响应超时，请重试');
              }
            }
            window.setTimeout(waitForDot, 50);
          }
          pressAt(0);
          return 'scheduled';
        })();
    """.trimIndent()
    evaluateJavascript(script) { result ->
        if (result != "\"scheduled\"") {
            onRejected("查询密码键盘尚未准备好，请重试")
        }
    }
}

private fun WebView.cancelCmbRechargePassword() {
    if (!isCmbRechargeNativeEntryUrl(url)) return
    evaluateJavascript(
        """
            (function(){
              var sheet = Array.from(document.querySelectorAll('.van-action-sheet'))
                .find(function(node){ return (node.innerText || '').includes('查询密码'); });
              var cancel = sheet && sheet.querySelector('.van-action-sheet__cancel');
              if (cancel) {
                cancel.click();
              } else {
                var overlays = Array.from(document.querySelectorAll('.van-overlay'));
                var overlay = overlays.find(function(node) {
                  return window.getComputedStyle(node).display !== 'none';
                });
                if (overlay) overlay.click();
              }
            })();
        """.trimIndent(),
        null
    )
}

private class CmbRechargeWebViewState(
    val boundsLocator: CmbRechargeBoundsLocator,
    var requestVersion: Int = -1
) {
    fun dispose() {
        boundsLocator.dispose()
    }
}

private val WebView.cmbRechargeState: CmbRechargeWebViewState?
    get() = tag as? CmbRechargeWebViewState

private class CmbRechargeBoundsLocator(
    private val webView: WebView,
    private val onBoundsChanged: (CmbRechargeNormalizedBounds?) -> Unit
) {
    private var generation = 0
    private var consecutiveMisses = 0
    private var lastBounds: CmbRechargeNormalizedBounds? = null
    private var pendingPoll: Runnable? = null
    private var isDisposed = false

    fun clear() {
        if (isDisposed) return
        generation += 1
        cancelPendingPoll()
        consecutiveMisses = 0
        publish(null)
    }

    fun locate(url: String?) {
        if (isDisposed) return
        generation += 1
        cancelPendingPoll()
        consecutiveMisses = 0
        val currentGeneration = generation
        if (!isCmbRechargeSuccessUrl(url)) {
            publish(null)
            return
        }
        publish(null)
        locate(currentGeneration)
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        generation += 1
        cancelPendingPoll()
        lastBounds = null
    }

    private fun locate(currentGeneration: Int) {
        if (
            isDisposed ||
            currentGeneration != generation ||
            !isCmbRechargeSuccessUrl(webView.url)
        ) {
            return
        }
        webView.evaluateJavascript(buildCmbRechargeSuccessReturnBoundsScript()) { rawResult ->
            if (
                isDisposed ||
                currentGeneration != generation ||
                !isCmbRechargeSuccessUrl(webView.url)
            ) {
                return@evaluateJavascript
            }
            val bounds = parseCmbRechargeNormalizedBounds(rawResult)
            if (bounds != null) {
                consecutiveMisses = 0
                publish(bounds)
            } else {
                consecutiveMisses += 1
                publish(null)
            }
            scheduleNextPoll(
                currentGeneration = currentGeneration,
                delayMillis = when {
                    bounds != null -> 250L
                    consecutiveMisses <= 30 -> 100L
                    else -> 1_000L
                }
            )
        }
    }

    private fun scheduleNextPoll(currentGeneration: Int, delayMillis: Long) {
        val poll = Runnable {
            pendingPoll = null
            locate(currentGeneration)
        }
        pendingPoll = poll
        if (!webView.postDelayed(poll, delayMillis)) pendingPoll = null
    }

    private fun cancelPendingPoll() {
        pendingPoll?.let(webView::removeCallbacks)
        pendingPoll = null
    }

    private fun publish(bounds: CmbRechargeNormalizedBounds?) {
        if (lastBounds == bounds) return
        lastBounds = bounds
        onBoundsChanged(bounds)
    }
}

internal fun parseCmbRechargeNormalizedBounds(rawResult: String?): CmbRechargeNormalizedBounds? {
    val value = rawResult?.trim().orEmpty()
    if (!value.startsWith('[') || !value.endsWith(']')) return null
    val parts = value.substring(1, value.length - 1).split(',')
    if (parts.size != 4) return null
    val numbers = parts.map { it.trim().toDoubleOrNull() ?: return null }
    return validateCmbRechargeNormalizedBounds(
        left = numbers[0],
        top = numbers[1],
        width = numbers[2],
        height = numbers[3]
    )
}

internal fun validateCmbRechargeNormalizedBounds(
    left: Double,
    top: Double,
    width: Double,
    height: Double
): CmbRechargeNormalizedBounds? {
    val values = listOf(left, top, width, height)
    if (values.any { !it.isFinite() }) return null
    if (left !in 0.0..1.0 || top !in 0.0..1.0) return null
    if (width !in 0.05..1.0 || height !in 0.01..0.35) return null
    if (left + width > 1.001 || top + height > 1.001) return null
    return CmbRechargeNormalizedBounds(
        left = left.toFloat(),
        top = top.toFloat(),
        width = width.toFloat(),
        height = height.toFloat()
    )
}

private fun applyCmbRechargePageStyle(webView: WebView, url: String?, script: String) {
    if (!isCmbRechargeStyleTarget(url)) return
    webView.evaluateJavascript(script, null)
}

internal fun isCmbRechargeSuccessUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val scheme = uri.scheme.orEmpty().lowercase()
    val host = uri.host.orEmpty().lowercase()
    val path = uri.path.orEmpty().trimEnd('/').lowercase()
    return scheme == "https" &&
        uri.port in setOf(-1, 443) &&
        host == "epay92.ahu.edu.cn" &&
        path == "/cashier-mobile/chargeresult"
}

internal fun shouldConfirmCmbRechargeSuccess(
    url: String?,
    verifiedReturnBounds: CmbRechargeNormalizedBounds?
): Boolean = verifiedReturnBounds != null && isCmbRechargeSuccessUrl(url)

internal fun isCmbRechargeStyleTarget(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    if (uri.scheme.orEmpty().lowercase() != "https" || uri.port !in setOf(-1, 443)) return false
    val host = uri.host.orEmpty().lowercase()
    val path = uri.path.orEmpty().lowercase()
    return when (host) {
        "epay92.ahu.edu.cn" -> path == "/cashier-mobile" || path.startsWith("/cashier-mobile/")
        "ycard.ahu.edu.cn" -> path.startsWith("/charge-app")
        else -> false
    }
}

internal fun isCmbRechargeNativeEntryUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val scheme = uri.scheme.orEmpty().lowercase()
    val host = uri.host.orEmpty().lowercase()
    val path = uri.path.orEmpty().trimEnd('/').lowercase()
    return scheme == "https" &&
        uri.port in setOf(-1, 443) &&
        host == "epay92.ahu.edu.cn" &&
        path == "/cashier-mobile/charge"
}

internal fun isCmbRechargeInsecureEntryUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    return uri.scheme.orEmpty().equals("http", ignoreCase = true) &&
        uri.port in setOf(-1, 80) &&
        uri.host.orEmpty().equals("epay92.ahu.edu.cn", ignoreCase = true) &&
        uri.path.orEmpty().trimEnd('/').equals(
            "/cashier-mobile/charge",
            ignoreCase = true
        )
}

internal fun isCmbRechargeHiddenFlowUrl(url: String?): Boolean {
    if (isCmbRechargeNativeEntryUrl(url)) return true
    if (url.isNullOrBlank()) return false
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val scheme = uri.scheme.orEmpty().lowercase()
    val host = uri.host.orEmpty().lowercase()
    val path = uri.path.orEmpty().trimEnd('/').lowercase()
    return scheme == "https" &&
        host == "ycard.ahu.edu.cn" &&
        uri.port in setOf(-1, 443) &&
        path == "/berserker-base/redirect"
}

internal fun shouldRevealCmbRechargeWebContent(
    url: String?,
    revealAllowed: Boolean
): Boolean = revealAllowed &&
    !url.isNullOrBlank() &&
    !isCmbRechargeHiddenFlowUrl(url) &&
    !isCmbRechargeSuccessUrl(url)

private fun WebView.updateCmbRechargeWebViewVisibility(isVisible: Boolean) {
    visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
    isEnabled = isVisible
    importantForAccessibility = if (isVisible) {
        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    } else {
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
}

internal fun normalizeCmbRechargeBalance(balanceInCents: Double): Double =
    if (balanceInCents.isFinite()) balanceInCents / 100.0 else 0.0

private fun buildCmbRechargeEntryUrl(token: String): String {
    return Uri.Builder()
        .scheme("https")
        .authority("ycard.ahu.edu.cn")
        .appendPath("berserker-base")
        .appendPath("redirect")
        .appendQueryParameter("appId", "253")
        .appendQueryParameter("loginFrom", "h5")
        .appendQueryParameter("synAccessSource", "h5")
        .appendQueryParameter("synjones-auth", token)
        .appendQueryParameter("type", "app")
        .build()
        .toString()
}

internal fun isCmbLoginRedirectUrl(url: String?): Boolean {
    val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        uri.host.equals("epay92.ahu.edu.cn", ignoreCase = true) &&
        uri.port in setOf(-1, 443) &&
        uri.path.orEmpty().trimEnd('/').equals(
            "/member/login/redirect",
            ignoreCase = true
        )
}

internal fun buildCmbHttpsCashierUrl(url: String?): String? {
    val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return null
    val scheme = uri.scheme.orEmpty().lowercase()
    val trustedPort = scheme == "http" && uri.port in setOf(-1, 80)
    if (
        !trustedPort ||
        !uri.host.equals("epay92.ahu.edu.cn", ignoreCase = true) ||
        !uri.path.orEmpty().trimEnd('/').equals(
            "/cashier-mobile/cashier",
            ignoreCase = true
        )
    ) {
        return null
    }

    return URI(
        "https",
        uri.userInfo,
        uri.host,
        -1,
        uri.path,
        uri.query,
        uri.fragment
    ).toString()
}

private fun safeCmbPageLocation(url: String?): String {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return "未知页面"
    val scheme = uri.scheme.orEmpty().lowercase()
    val host = uri.host.orEmpty().lowercase()
    val path = uri.encodedPath.orEmpty().ifBlank { "/" }
    return "$scheme://$host$path"
}

internal fun isCmbRechargeAllowedMainFrameUrl(url: String?): Boolean {
    val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return false
    if (uri.scheme.orEmpty().lowercase() != "https" || uri.port !in setOf(-1, 443)) return false
    val host = uri.host.orEmpty().lowercase()
    val path = uri.path.orEmpty()
    return when (host) {
        "ycard.ahu.edu.cn" ->
            path == "/berserker-base/redirect" ||
                path == "/charge-app" ||
                path.startsWith("/charge-app/")
        "epay92.ahu.edu.cn" ->
            path == "/member/login/redirect" ||
                path == "/cashier-mobile" ||
                path.startsWith("/cashier-mobile/")
        else -> false
    }
}

private fun isAuditedCmbSubmitPage(url: Uri): Boolean =
    isCmbRechargeAllowedMainFrameUrl(url.toString()) &&
        (url.path.orEmpty().contains("/cashier-mobile/charge") ||
            url.path.orEmpty().contains("/charge-app"))

private fun openExternalLink(context: android.content.Context, url: String) {
    val targetUri = runCatching { Uri.parse(url) }.getOrNull()
    if (targetUri == null) {
        Toast.makeText(context, "无法打开外部链接", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, targetUri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "无法打开外部链接", Toast.LENGTH_SHORT).show()
    }
}

private fun syncYcardCookiesToWebView(webView: WebView) {
    val webCookieManager = android.webkit.CookieManager.getInstance()
    YcardCookieManager.cookieJar.getAllCookies().forEach { cookie ->
        val targetUrl = buildCookieTargetUrl(cookie)
        val cookieValue = buildString {
            append(cookie.name)
            append("=")
            append(cookie.value)
            append("; Path=")
            append(cookie.path)
            append("; Domain=")
            append(cookie.domain)
            if (cookie.secure) append("; Secure")
            if (cookie.httpOnly) append("; HttpOnly")
        }
        webCookieManager.setCookie(targetUrl, cookieValue)
    }
    webCookieManager.flush()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        webCookieManager.setAcceptThirdPartyCookies(webView, false)
    }
}

private fun buildCookieTargetUrl(cookie: Cookie): String {
    val scheme = if (cookie.secure) "https" else "http"
    val domain = cookie.domain.trimStart('.')
    return "$scheme://$domain"
}

private fun Color.toCssColor(): String = "#%06X".format(toArgb() and 0xFFFFFF)

package com.ahu.ahutong.personalization.runtime

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.personalization.action.ActionFamily
import com.ahu.ahutong.personalization.action.ActionSource
import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.action.SideEffect
import com.ahu.ahutong.personalization.action.OrganicLabelPolicy
import com.ahu.ahutong.personalization.context.BalanceBucket
import com.ahu.ahutong.personalization.context.ContextSnapshot
import com.ahu.ahutong.personalization.context.DayType
import com.ahu.ahutong.personalization.context.ExamDistanceBucket
import com.ahu.ahutong.personalization.context.FeatureExtractor
import com.ahu.ahutong.personalization.context.PredictionInput
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import com.ahu.ahutong.personalization.evaluation.ShadowModelEvaluator
import com.ahu.ahutong.personalization.inference.DecayedFrequencyPredictor
import com.ahu.ahutong.personalization.inference.NextActionProbabilityVector
import com.ahu.ahutong.personalization.inference.RecentActionBaselinePredictor
import com.ahu.ahutong.personalization.inference.TimeBucketFrequencyBaselinePredictor
import com.ahu.ahutong.personalization.inference.TinyMlpPredictor
import com.ahu.ahutong.personalization.model.ModelStateStore
import com.ahu.ahutong.personalization.profile.ProfileKeyManager
import com.ahu.ahutong.personalization.prefetch.PaymentQrRepository
import com.ahu.ahutong.personalization.prefetch.PaymentQrOpenCommandStore
import com.ahu.ahutong.personalization.prefetch.PrefetchCoordinator
import com.ahu.ahutong.personalization.promotion.LocalPromotionManager
import com.ahu.ahutong.personalization.promotion.PromotionSnapshot
import com.ahu.ahutong.personalization.storage.BehaviorDao
import com.ahu.ahutong.personalization.storage.BehaviorDatabase
import com.ahu.ahutong.personalization.storage.BehaviorEventEntity
import com.ahu.ahutong.personalization.storage.BinaryCodec
import com.ahu.ahutong.personalization.storage.LearningStateEntity
import com.ahu.ahutong.personalization.storage.PendingPredictionEntity
import com.ahu.ahutong.personalization.storage.ProductExecutionLeaseEntity
import com.ahu.ahutong.personalization.storage.transaction
import com.ahu.ahutong.personalization.training.OnDeviceTrainer
import com.ahu.ahutong.personalization.training.OrganicTrainingSample
import com.ahu.ahutong.personalization.training.TrainingSliceResult
import com.ahu.ahutong.personalization.telemetry.ModelQualityTelemetryManager
import com.ahu.ahutong.personalization.telemetry.TelemetryAggregateStore
import com.ahu.ahutong.data.dao.PreferencesManager
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.inject.Inject
import javax.inject.Singleton
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class OpportunityTrigger(val labelWindowMillis: Long) {
    STABLE_FOREGROUND(120_000L),
    ACTION_INTENT_ACCEPTED(60_000L),
    BUSINESS_CONTEXT_CHANGED(60_000L)
}

enum class ConfidenceBucket { LOW, MEDIUM, HIGH }

sealed interface PredictionUiState {
    data object Hidden : PredictionUiState
    data class Suggestion(
        val executionId: String,
        val decisionId: String,
        val action: AppActionId,
        val title: String,
        val reason: String,
        val confidenceBucket: ConfidenceBucket
    ) : PredictionUiState
}

data class RuntimeDiagnosticsState(
    val profileActive: Boolean = false,
    val foreground: Boolean = false,
    val sessionId: String? = null,
    val decisionId: String? = null,
    val preparationState: String = "IDLE",
    val previousAction: String? = null,
    val deadlineElapsedMs: Long? = null,
    val stage: String = "SHADOW",
    val tier: String = "STAT_ONLY",
    val lambda: Float = 0f,
    val activeCheckpoint: String? = null,
    val statProbabilities: Map<String, Float> = emptyMap(),
    val tinyProbabilities: Map<String, Float> = emptyMap(),
    val effectiveProbabilities: Map<String, Float> = emptyMap(),
    val lastResolution: String? = null,
    val lastFailure: String? = null,
    val lastTraining: TrainingSliceResult? = null
)

data class SanitizedDiagnosticsSnapshot(
    val trainingSamples: Int = 0,
    val organicNonNoneSamples: Int = 0,
    val actionFamilies: Int = 0,
    val statLearningStartedDay: Long? = null,
    val tinyTrainingStartedDay: Long? = null,
    val trainingRevision: Long = 0,
    val candidateCheckpoint: String? = null,
    val activeChecksum: String? = null,
    val modelSizeBytes: Long = 0,
    val promotionWindows: List<String> = emptyList(),
    val recentTimeline: List<String> = emptyList(),
    val pendingTelemetryReports: Int = 0
)

@Singleton
class BehaviorPredictionRuntime @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: BehaviorDatabase,
    private val dao: BehaviorDao,
    private val profileKeyManager: ProfileKeyManager,
    private val statPredictor: DecayedFrequencyPredictor,
    private val tinyPredictor: TinyMlpPredictor,
    private val recentBaseline: RecentActionBaselinePredictor,
    private val timeBaseline: TimeBucketFrequencyBaselinePredictor,
    private val evaluator: ShadowModelEvaluator,
    private val trainer: OnDeviceTrainer,
    private val promotionManager: LocalPromotionManager,
    private val modelStateStore: ModelStateStore,
    private val preferencesManager: PreferencesManager,
    private val prefetchCoordinator: PrefetchCoordinator,
    private val paymentQrRepository: PaymentQrRepository,
    private val paymentQrCommands: PaymentQrOpenCommandStore,
    private val telemetryManager: ModelQualityTelemetryManager,
    private val telemetryAggregateStore: TelemetryAggregateStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val processInstanceId = UUID.randomUUID().toString()
    private val profileLocks = ConcurrentHashMap<String, Mutex>()
    private val deadlineJobs = ConcurrentHashMap<String, Job>()
    private val sequence = AtomicLong(0)
    private val executionEpoch = AtomicLong(0)
    private val profileGeneration = AtomicLong(0)
    private val loginGeneration = AtomicLong(0)
    private val _diagnostics = MutableStateFlow(RuntimeDiagnosticsState())
    private val _uiState = MutableStateFlow<PredictionUiState>(PredictionUiState.Hidden)
    private val _telemetryConsentEnabled = MutableStateFlow(false)
    private val _sensitiveUiVisible = MutableStateFlow(false)
    private val recentActions = ArrayDeque<AppActionId>()
    private val recentActionSources = ArrayDeque<ActionSource>()
    private val organicActionHistory = ArrayDeque<AppActionId>()
    private val gson = Gson()
    private val suggestionCooldownUntil = ConcurrentHashMap<String, Long>()
    @Volatile private var suggestionExpiryJob: Job? = null

    val diagnostics: StateFlow<RuntimeDiagnosticsState> = _diagnostics.asStateFlow()
    val uiState: StateFlow<PredictionUiState> = _uiState.asStateFlow()
    val telemetryConsentEnabled: StateFlow<Boolean> = _telemetryConsentEnabled.asStateFlow()
    val sensitiveUiVisible: StateFlow<Boolean> = _sensitiveUiVisible.asStateFlow()

    @Volatile private var profileKey: String? = null
    @Volatile private var activeAccountIdentifier: String? = null
    @Volatile private var sessionId: String? = null
    @Volatile private var foreground = false
    @Volatile private var interactive = false
    @Volatile private var lastRoute: String? = null
    @Volatile private var lastAction: AppActionId? = null
    @Volatile private var balanceBucket = BalanceBucket.UNKNOWN
    @Volatile private var balanceFresh = false
    @Volatile private var examBucket = ExamDistanceBucket.UNKNOWN
    @Volatile private var sessionStartedElapsedMs = 0L
    @Volatile private var lastBackgroundElapsedMs: Long? = null
    @Volatile private var foregroundGapBucket: Int? = null
    @Volatile private var routeChangedElapsedMs = 0L
    @Volatile private var lastContextOpportunityElapsedMs = 0L
    @Volatile private var taintedChain = false
    @Volatile private var lastSuggestionElapsedMs = 0L
    @Volatile private var sessionSuggestionCount = 0
    @Volatile private var suggestionEpochDay = Long.MIN_VALUE
    @Volatile private var dailySuggestionCount = 0
    @Volatile private var suppressedRoute: String? = null
    @Volatile private var nextNavigationSource: ActionSource? = null

    suspend fun startProfile(accountIdentifier: String) {
        val nextProfile = profileKeyManager.profileKey(accountIdentifier)
        if (profileKey == nextProfile && sessionId != null) {
            val refreshedLoginGeneration = loginGeneration.incrementAndGet()
            paymentQrRepository.activateProfile(nextProfile, profileGeneration.get(), refreshedLoginGeneration)
            paymentQrCommands.activate(profileGeneration.get(), refreshedLoginGeneration)
            return
        }
        stopSession(censorReason = "PROFILE_SWITCHED", clearProfile = false)
        profileKey = nextProfile
        activeAccountIdentifier = accountIdentifier
        sessionId = UUID.randomUUID().toString()
        sessionStartedElapsedMs = SystemClock.elapsedRealtime()
        val persistedMaxSequence = dao.maxEventSequence(nextProfile)
        sequence.updateAndGet { current -> maxOf(current, persistedMaxSequence) }
        val activeProfileGeneration = profileGeneration.incrementAndGet()
        val activeLoginGeneration = loginGeneration.incrementAndGet()
        recentActions.clear()
        recentActionSources.clear()
        organicActionHistory.clear()
        dao.recentEvents(nextProfile, 64).asReversed()
            .filter { it.eventType == "ACTION_INTENT_ACCEPTED" }
            .forEach { stored ->
                val action = AppActionId.fromStableId(stored.actionId) ?: return@forEach
                val source = runCatching { ActionSource.valueOf(stored.source) }.getOrDefault(ActionSource.SYSTEM)
                recentActions.addLast(action)
                recentActionSources.addLast(source)
                while (recentActions.size > 8) recentActions.removeFirst()
                while (recentActionSources.size > 8) recentActionSources.removeFirst()
                if (source == ActionSource.ORGANIC) organicActionHistory.addLast(action)
            }
        lastAction = null
        taintedChain = false
        sessionSuggestionCount = 0
        dao.censorStaleProcessPending(nextProfile, processInstanceId)
        modelStateStore.loadOrCreate(nextProfile)
        val promotion = promotionManager.snapshot(nextProfile)
        trainer.resumeProfile(nextProfile)
        val onboardingChoice = preferencesManager.modelQualityTelemetryOnboardingChoice.first()
        val profileTelemetryEnabled = telemetryManager.isConsentEnabled(nextProfile)
        when {
            onboardingChoice == true && !profileTelemetryEnabled ->
                telemetryManager.setConsent(
                    nextProfile,
                    enabled = true,
                    localModelGenerationVersion = promotion.modelGeneration
                )
            onboardingChoice != true && profileTelemetryEnabled ->
                telemetryManager.setConsent(nextProfile, enabled = false)
        }
        telemetryManager.reconcileProfile(nextProfile, promotion.modelGeneration)
        _telemetryConsentEnabled.value = onboardingChoice == true && telemetryManager.isConsentEnabled(nextProfile)
        paymentQrRepository.activateProfile(nextProfile, activeProfileGeneration, activeLoginGeneration)
        paymentQrCommands.activate(activeProfileGeneration, activeLoginGeneration)
        prefetchCoordinator.beginSession()
        insertLifecycleEvent("SESSION_STARTED", ActionSource.SYSTEM)
        _diagnostics.value = _diagnostics.value.copy(
            profileActive = true,
            sessionId = sessionId,
            foreground = foreground,
            lastFailure = null
        )
        if (foreground && interactive) createOpportunity(OpportunityTrigger.STABLE_FOREGROUND, null)
    }

    fun setForeground(value: Boolean, isInteractive: Boolean = value) {
        val elapsed = SystemClock.elapsedRealtime()
        if (value && !foreground) {
            foregroundGapBucket = lastBackgroundElapsedMs?.let { gapBucket(elapsed - it) }
        } else if (!value && foreground) {
            lastBackgroundElapsedMs = elapsed
        }
        foreground = value
        interactive = isInteractive
        _diagnostics.value = _diagnostics.value.copy(foreground = value)
        if (!value || !isInteractive) {
            scope.launch { censorActive("CENSORED_BACKGROUND") }
            scope.launch { prefetchCoordinator.cancelAll() }
            profileKey?.let { activeProfile -> scope.launch { trainer.cancelProfile(activeProfile) } }
            hideSuggestion()
        } else {
            scope.launch {
                val activeProfile = profileKey ?: return@launch
                trainer.resumeProfile(activeProfile)
                val pending = dao.latestPending(activeProfile)
                if (pending == null) createOpportunity(OpportunityTrigger.STABLE_FOREGROUND, lastAction)
                else registerDeadline(pending)
                delay(TRAINING_IDLE_GRACE_MS)
                runIdleTrainingSlice()
            }
        }
    }

    fun onRouteChanged(route: String?, source: ActionSource = ActionSource.ORGANIC) {
        if (route == null) return
        val externallyMarkedSource = nextNavigationSource.also { nextNavigationSource = null }
        if (route == lastRoute) return
        hideSuggestion()
        lastRoute = route
        routeChangedElapsedMs = SystemClock.elapsedRealtime()
        if (suppressedRoute == route) {
            suppressedRoute = null
            return
        }
        val action = AppActionCatalog.actionForRoute(route) ?: run {
            scope.launch { censorActive("CENSORED_UNTRACKED_OR_DEBUG_ROUTE") }
            return
        }
        if (externallyMarkedSource != null && !AppActionCatalog.spec(action).predictable) {
            nextNavigationSource = externallyMarkedSource
        }
        val actualSource = externallyMarkedSource ?: source
        scope.launch { recordActionIntent(action, actualSource, route) }
    }

    fun suppressNextRoute(route: String) {
        suppressedRoute = route.takeUnless { it == lastRoute }
    }

    fun markNextNavigationSource(source: ActionSource) { nextNavigationSource = source }

    fun recordActionIntentAsync(action: AppActionId, source: ActionSource = ActionSource.ORGANIC) {
        scope.launch { recordActionIntent(action, source, AppActionCatalog.spec(action).route) }
    }

    suspend fun recordActionIntent(
        action: AppActionId,
        source: ActionSource = ActionSource.ORGANIC,
        route: String? = AppActionCatalog.spec(action).route
    ) {
        val activeProfile = profileKey ?: return
        val activeSession = sessionId ?: return
        val spec = AppActionCatalog.spec(action)
        val lock = profileLocks.getOrPut(activeProfile) { Mutex() }
        lock.withLock {
            val elapsed = SystemClock.elapsedRealtime()
            val eventId = UUID.randomUUID().toString()
            val currentPending = dao.latestPending(activeProfile)
            val cleanOrganic = OrganicLabelPolicy.isEligible(action, source, taintedChain = taintedChain)
            val sequenceNo = sequence.incrementAndGet()
            dao.insertEvent(
                event(
                    eventId,
                    UUID.randomUUID().toString(),
                    activeProfile,
                    activeSession,
                    sequenceNo,
                    "ACTION_INTENT_ACCEPTED",
                    action,
                    source,
                    elapsed,
                    currentPending?.decisionId
                )
            )
            if (currentPending != null) {
                when {
                    currentPending.preparationState == "PREPARING" ->
                        dao.censorPendingCas(
                            currentPending.decisionId,
                            activeProfile,
                            "CENSORED_PREPARATION_SUPERSEDED",
                            eventId
                        )
                    cleanOrganic && elapsed <= currentPending.labelDeadlineElapsedMs ->
                        resolvePending(currentPending, action.stableId, eventId, "ORGANIC_ACTION", spec.family)
                    else -> {
                        dao.invalidatePendingForObservedSourceCas(
                            currentPending.decisionId,
                            activeProfile,
                            if (source == ActionSource.ORGANIC) currentPending.interventionState else "TAINTED_${source.name}",
                            if (source == ActionSource.ORGANIC) "CENSORED_TAINTED_CHAIN" else "INVALIDATED_PRODUCT_INTERVENTION",
                            eventId
                        )
                        deadlineJobs.remove(currentPending.decisionId)?.cancel()
                    }
                }
            }
            if (source == ActionSource.ORGANIC) {
                if (taintedChain) {
                    recentActions.clear()
                    recentActionSources.clear()
                }
                taintedChain = false
            } else {
                taintedChain = true
            }
            lastAction = action
            if (prefetchCoordinator.onActionOpened(action)) {
                dao.insertEvent(
                    event(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        activeProfile,
                        activeSession,
                        sequence.incrementAndGet(),
                        "PREFETCH_CONSUMED",
                        action,
                        ActionSource.SYSTEM,
                        elapsed,
                        null
                    )
                )
            }
            when (action) {
                AppActionId.MANUAL_REFRESH_SCHEDULE -> prefetchCoordinator.invalidate(AppActionId.VIEW_SCHEDULE)
                AppActionId.MANUAL_REFRESH_EXAM, AppActionId.RETRY_EXAM -> prefetchCoordinator.invalidate(AppActionId.VIEW_EXAM_ROOM)
                AppActionId.MANUAL_REFRESH_GRADE, AppActionId.RETRY_GRADE -> prefetchCoordinator.invalidate(AppActionId.VIEW_GRADES)
                AppActionId.REFRESH_PAYMENT_QR -> prefetchCoordinator.invalidate(AppActionId.OPEN_PAYMENT_QR)
                else -> Unit
            }
            recentActions.addLast(action)
            recentActionSources.addLast(source)
            while (recentActions.size > 8) recentActions.removeFirst()
            while (recentActionSources.size > 8) recentActionSources.removeFirst()
            if (source == ActionSource.ORGANIC) {
                organicActionHistory.addLast(action)
                while (organicActionHistory.size > 64) organicActionHistory.removeFirst()
            }
            // Product-directed/deep-link/restore actions may continue product prediction, but the
            // opportunity is TAINTED_CHAIN and can never train, evaluate, or promote a model.
            // The next independent organic action starts a clean opportunity after acting only as an anchor.
            if (spec.predictable && foreground && interactive) {
                createOpportunityLocked(OpportunityTrigger.ACTION_INTENT_ACCEPTED, action, eventId, sequenceNo)
            }
        }
    }

    fun onBusinessContextChanged(
        newBalanceBucket: BalanceBucket = balanceBucket,
        newBalanceFresh: Boolean = balanceFresh,
        newExamBucket: ExamDistanceBucket = examBucket
    ) {
        val changed = newBalanceBucket != balanceBucket || newBalanceFresh != balanceFresh || newExamBucket != examBucket
        balanceBucket = newBalanceBucket
        balanceFresh = newBalanceFresh
        examBucket = newExamBucket
        if (!changed || !foreground || !interactive || taintedChain) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastContextOpportunityElapsedMs < CONTEXT_DEBOUNCE_MS) return
        lastContextOpportunityElapsedMs = now
        scope.launch {
            censorActive("CENSORED_CONTEXT_CHANGED")
            createOpportunity(OpportunityTrigger.BUSINESS_CONTEXT_CHANGED, lastAction)
        }
    }

    suspend fun prepareVisibleIntervention(
        decisionId: String,
        action: AppActionId,
        type: String,
        source: ActionSource,
        route: String?,
        ttlMillis: Long = 10_000L,
        allowHoldoutInvalidation: Boolean = false
    ): ProductExecutionLeaseEntity? {
        val activeProfile = profileKey ?: return null
        val lock = profileLocks.getOrPut(activeProfile) { Mutex() }
        return lock.withLock {
            val pending = dao.pending(decisionId) ?: return@withLock null
            if (pending.profileKey != activeProfile || pending.resolutionStatus != "PENDING" ||
                (pending.isPromotionHoldout && !allowHoldoutInvalidation)
            ) return@withLock null
            val elapsed = SystemClock.elapsedRealtime()
            if (elapsed >= pending.labelDeadlineElapsedMs) return@withLock null
            val epoch = executionEpoch.incrementAndGet()
            val executionId = UUID.randomUUID().toString()
            val lease = ProductExecutionLeaseEntity(
                executionId,
                decisionId,
                activeProfile,
                requireNotNull(sessionId),
                processInstanceId,
                action.stableId,
                type,
                source.name,
                route,
                profileGeneration.get(),
                loginGeneration.get(),
                sequence.get(),
                epoch,
                elapsed,
                elapsed + ttlMillis,
                "PREPARED"
            )
            if (!dao.prepareProductExecution(decisionId, activeProfile, "PREPARED_$type", lease)) {
                return@withLock null
            }
            deadlineJobs.remove(decisionId)?.cancel()
            lease
        }
    }

    suspend fun consumeIntervention(executionId: String): ProductExecutionLeaseEntity? {
        val lease = dao.lease(executionId) ?: return null
        if (!foreground || !interactive || lease.profileKey != profileKey ||
            lease.processInstanceId != processInstanceId ||
            lease.profileGeneration != profileGeneration.get() ||
            lease.loginGeneration != loginGeneration.get()
        ) return null
        val now = SystemClock.elapsedRealtime()
        if (lease.executionEpoch != executionEpoch.get() || dao.consumeLease(executionId, now) != 1) return null
        return lease.copy(state = "CONSUMED")
    }

    suspend fun showSuggestion(decisionId: String, action: AppActionId, probability: Float): Boolean {
        if (!preferencesManager.personalizationEnabled.first()) return false
        val spec = AppActionCatalog.spec(action)
        if (!spec.suggestible || spec.sideEffect == SideEffect.TRANSACTION || !foreground || !interactive ||
            _sensitiveUiVisible.value || !isSuggestionSurfaceAllowed(lastRoute)
        ) return false
        val lease = prepareVisibleIntervention(decisionId, action, "SUGGESTION", ActionSource.SUGGESTION, spec.route) ?: return false
        val consumed = consumeIntervention(lease.executionId) ?: return false
        _uiState.value = PredictionUiState.Suggestion(
            consumed.executionId,
            decisionId,
            action,
            spec.title,
            spec.reasonLabel,
            when {
                probability >= 0.75f -> ConfidenceBucket.HIGH
                probability >= 0.50f -> ConfidenceBucket.MEDIUM
                else -> ConfidenceBucket.LOW
            }
        )
        suggestionExpiryJob?.cancel()
        suggestionExpiryJob = scope.launch {
            delay(SUGGESTION_VISIBLE_TTL_MS)
            val current = _uiState.value as? PredictionUiState.Suggestion
            if (current?.executionId == consumed.executionId) {
                _uiState.value = PredictionUiState.Hidden
            }
        }
        return true
    }

    fun hideSuggestion() {
        suggestionExpiryJob?.cancel()
        suggestionExpiryJob = null
        _uiState.value = PredictionUiState.Hidden
    }

    fun setSensitiveUiVisible(visible: Boolean) {
        _sensitiveUiVisible.value = visible
        if (visible) hideSuggestion()
    }

    fun dismissSuggestionByUser() {
        val current = _uiState.value as? PredictionUiState.Suggestion
        if (current != null) {
            suggestionCooldownUntil[current.action.stableId] =
                SystemClock.elapsedRealtime() + ACTION_DISMISS_COOLDOWN_MS
        }
        hideSuggestion()
    }

    fun suppressSuggestedActionByUser() {
        val current = _uiState.value as? PredictionUiState.Suggestion
        if (current != null) {
            suggestionCooldownUntil[current.action.stableId] =
                SystemClock.elapsedRealtime() + ACTION_SUPPRESS_COOLDOWN_MS
            profileKey?.let { activeProfile ->
                scope.launch {
                    preferencesManager.suppressSuggestionActionUntil(
                        activeProfile,
                        current.action.stableId,
                        System.currentTimeMillis() + ACTION_SUPPRESS_COOLDOWN_MS
                    )
                }
            }
        }
        hideSuggestion()
    }

    suspend fun acceptSuggestion(executionId: String): AppActionId? {
        val state = _uiState.value as? PredictionUiState.Suggestion ?: return null
        if (state.executionId != executionId) return null
        hideSuggestion()
        AppActionCatalog.spec(state.action).route?.let(::suppressNextRoute)
        recordActionIntent(state.action, ActionSource.SUGGESTION, AppActionCatalog.spec(state.action).route)
        return state.action
    }

    suspend fun authorizeUserPreferencePaymentQr(): Boolean {
        val activeProfile = profileKey ?: return false
        val pending = dao.latestPending(activeProfile)
        if (pending == null) {
            recordActionIntent(AppActionId.OPEN_PAYMENT_QR, ActionSource.USER_PREFERENCE, null)
            return true
        }
        val lease = prepareVisibleIntervention(
            pending.decisionId,
            AppActionId.OPEN_PAYMENT_QR,
            "PAYMENT_QR_USER_PREFERENCE",
            ActionSource.USER_PREFERENCE,
            null,
            allowHoldoutInvalidation = true
        ) ?: return false
        if (consumeIntervention(lease.executionId) == null) return false
        recordActionIntent(AppActionId.OPEN_PAYMENT_QR, ActionSource.USER_PREFERENCE, null)
        return true
    }

    suspend fun clearLearningRecord() {
        val activeProfile = profileKey ?: return
        val account = activeAccountIdentifier
        telemetryManager.revoke(activeProfile, deleteRemote = true)
        stopSession("CLEARED_BY_USER", clearProfile = true)
        profileKey = null
        if (account != null) startProfile(account)
    }

    suspend fun logoutAndClear() {
        val activeProfile = profileKey ?: return
        telemetryManager.setConsent(activeProfile, enabled = false)
        _telemetryConsentEnabled.value = false
        stopSession("LOGOUT", clearProfile = true)
        profileKey = null
        activeAccountIdentifier = null
    }

    suspend fun setTelemetryConsent(enabled: Boolean) {
        val activeProfile = profileKey ?: return
        val generation = promotionManager.snapshot(activeProfile).modelGeneration
        telemetryManager.setConsent(activeProfile, enabled, generation)
        _telemetryConsentEnabled.value = enabled
    }

    suspend fun cancelPredictivePrefetch() {
        prefetchCoordinator.cancelAll()
    }

    suspend fun stopSession(censorReason: String = "SESSION_ENDED", clearProfile: Boolean = false) {
        val activeProfile = profileKey ?: return
        censorActive(censorReason)
        deadlineJobs.values.forEach(Job::cancel)
        deadlineJobs.clear()
        trainer.cancelProfile(activeProfile)
        dao.cancelProfileLeases(activeProfile)
        paymentQrCommands.clear()
        insertLifecycleEvent("SESSION_ENDED", ActionSource.SYSTEM)
        if (clearProfile) {
            preferencesManager.clearSuggestionActionSuppressions(activeProfile)
            dao.deleteProfileLearningState(activeProfile)
            modelStateStore.reset(activeProfile)
        }
        profileGeneration.incrementAndGet()
        loginGeneration.incrementAndGet()
        sessionId = null
        recentActions.clear()
        recentActionSources.clear()
        organicActionHistory.clear()
        lastAction = null
        lastRoute = null
        routeChangedElapsedMs = 0L
        foregroundGapBucket = null
        hideSuggestion()
        _telemetryConsentEnabled.value = false
        _sensitiveUiVisible.value = false
        _diagnostics.value = RuntimeDiagnosticsState()
    }

    suspend fun runIdleTrainingSlice(): TrainingSliceResult {
        if (!foreground || !interactive) return TrainingSliceResult(false, profileKey, 0, 0, null, null, 0, "NOT_FOREGROUND_IDLE")
        val battery = context.getSystemService(BatteryManager::class.java)
        if (battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.let { it in 0..14 } == true) {
            return TrainingSliceResult(false, profileKey, 0, 0, null, null, 0, "LOW_BATTERY")
        }
        val power = context.getSystemService(PowerManager::class.java)
        if (power?.isPowerSaveMode == true) {
            return TrainingSliceResult(false, profileKey, 0, 0, null, null, 0, "POWER_SAVER")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            power?.currentThermalStatus?.let { it >= PowerManager.THERMAL_STATUS_SEVERE } == true
        ) {
            return TrainingSliceResult(false, profileKey, 0, 0, null, null, 0, "THERMAL_LIMIT")
        }
        val result = trainer.runIdleSlice(50)
        _diagnostics.value = _diagnostics.value.copy(lastTraining = result)
        return result
    }

    suspend fun sanitizedDiagnosticsSnapshot(): SanitizedDiagnosticsSnapshot {
        val activeProfile = profileKey ?: return SanitizedDiagnosticsSnapshot()
        val learning = dao.learningState(activeProfile)
        val model = modelStateStore.state(activeProfile)
        return SanitizedDiagnosticsSnapshot(
            trainingSamples = dao.trainingSampleCount(activeProfile),
            organicNonNoneSamples = dao.organicNonNoneTrainingSampleCount(activeProfile),
            actionFamilies = dao.trainingActionFamilyCount(activeProfile),
            statLearningStartedDay = learning?.statLearningStartedEpochDay,
            tinyTrainingStartedDay = learning?.tinyTrainingStartedEpochDay,
            trainingRevision = model.training.trainingRevision,
            candidateCheckpoint = model.candidate?.checkpointId?.take(8),
            activeChecksum = model.active.checksum.take(8),
            modelSizeBytes = modelStateStore.modelSizeBytes(activeProfile),
            promotionWindows = dao.promotionWindows(activeProfile, 5).map {
                "${it.stage} ${it.startEpochDay}..${it.endEpochDay} n=${it.pairedSampleCount} ECE=${"%.3f".format(it.ece)} ${if (it.qualified) "PASS" else "FAIL"}"
            },
            recentTimeline = dao.recentEvents(activeProfile, 20).map {
                "#${it.sequenceNo} ${it.eventType} ${it.actionId ?: "--"} ${it.source}"
            },
            pendingTelemetryReports = dao.pendingTelemetryReportCount(activeProfile)
        )
    }

    private suspend fun createOpportunity(trigger: OpportunityTrigger, previousAction: AppActionId?) {
        val activeProfile = profileKey ?: return
        val lock = profileLocks.getOrPut(activeProfile) { Mutex() }
        lock.withLock {
            val triggerEventId = UUID.randomUUID().toString()
            val seq = sequence.incrementAndGet()
            createOpportunityLocked(trigger, previousAction, triggerEventId, seq)
        }
    }

    private suspend fun createOpportunityLocked(
        trigger: OpportunityTrigger,
        previousAction: AppActionId?,
        triggerEventId: String,
        sequenceNo: Long
    ) {
        val activeProfile = profileKey ?: return
        val activeSession = sessionId ?: return
        if (!foreground || !interactive) return
        dao.latestPending(activeProfile)?.let { existing ->
            dao.censorPendingCas(existing.decisionId, activeProfile, "CENSORED_SUPERSEDED")
            deadlineJobs.remove(existing.decisionId)?.cancel()
        }
        val nowEpoch = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val decisionId = UUID.randomUUID().toString()
        val contextSnapshot = snapshot(nowEpoch, previousAction)
        val input = FeatureExtractor.build(
            activeProfile,
            decisionId,
            contextSnapshot,
            AppActionCatalog.businessAvailability(contextSnapshot.route)
        )
        val promotion = promotionManager.snapshot(activeProfile)
        val model = modelStateStore.state(activeProfile)
        val learningEligible = !taintedChain
        val holdout = learningEligible && bucket(promotion.holdoutSeed, decisionId, "promotion") < HOLDOUT_PERCENT
        val candidateHoldout = learningEligible && model.candidate != null &&
            bucket(promotion.holdoutSeed, decisionId, "candidate") < CANDIDATE_HOLDOUT_PERCENT
        val preparing = PendingPredictionEntity(
            decisionId,
            activeProfile,
            activeSession,
            processInstanceId,
            sequenceNo,
            triggerEventId,
            previousAction?.stableId,
            nowEpoch,
            elapsed,
            elapsed + trigger.labelWindowMillis,
            LABEL_WINDOW_POLICY_VERSION,
            input.featureSchemaVersion,
            input.outputSchemaVersion,
            input.actionCatalogVersion,
            input.features.toBytes(),
            input.businessAvailability.toBytes(),
            input.inputDigest,
            gson.toJson(input.snapshot),
            "PREPARING",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            model.active.checkpointId,
            model.active.checksum,
            model.candidate?.checkpointId,
            model.candidate?.checksum,
            null,
            null,
            null,
            null,
            promotion.stage.name,
            promotion.tier.name,
            promotion.tier.lambda,
            holdout || candidateHoldout,
            if (learningEligible) "NONE" else "TAINTED_CHAIN",
            "PENDING",
            null,
            null
        )
        dao.insertPending(preparing)
        _diagnostics.value = _diagnostics.value.copy(
            decisionId = decisionId,
            preparationState = "PREPARING",
            previousAction = previousAction?.stableId,
            deadlineElapsedMs = preparing.labelDeadlineElapsedMs,
            stage = promotion.stage.name,
            tier = promotion.tier.name,
            lambda = promotion.tier.lambda,
            activeCheckpoint = model.active.checkpointId
        )
        scope.launch {
            preparePrediction(preparing, input, promotion, candidateHoldout)
        }
    }

    private suspend fun preparePrediction(
        preparing: PendingPredictionEntity,
        input: PredictionInput,
        promotion: PromotionSnapshot,
        candidateHoldout: Boolean
    ) {
        try {
            val statDeferred = scope.async { statPredictor.predict(input) }
            val tinyDeferred = scope.async { tinyPredictor.predict(input) }
            val recentDeferred = scope.async { recentBaseline.predict(input) }
            val timeDeferred = scope.async { timeBaseline.predict(input) }
            val stat = statDeferred.await()
            val tiny = runCatching { tinyDeferred.await() }.getOrNull()
            val recent = recentDeferred.await()
            val time = timeDeferred.await()
            val candidate = if (candidateHoldout) runCatching { tinyPredictor.predictCandidate(input) }.getOrNull() else null
            val lock = profileLocks.getOrPut(preparing.profileKey) { Mutex() }
            lock.withLock {
                if (profileKey != preparing.profileKey || sessionId != preparing.sessionId) return@withLock
                val current = dao.pending(preparing.decisionId) ?: return@withLock
                val now = SystemClock.elapsedRealtime()
                val model = modelStateStore.state(preparing.profileKey)
                if (current.preparationState != "PREPARING" || current.resolutionStatus != "PENDING" ||
                    current.processInstanceId != processInstanceId || current.inputDigest != input.inputDigest ||
                    current.activeCheckpointId != model.active.checkpointId || now >= current.labelDeadlineElapsedMs
                ) {
                    if (current.resolutionStatus == "PENDING") {
                        dao.censorPendingCas(current.decisionId, current.profileKey, "CENSORED_PREPARATION_STALE")
                    }
                    return@withLock
                }
                val generationReset = promotionManager.recordInferenceAttempt(
                    preparing.profileKey,
                    preparing.activeCheckpointId,
                    tiny != null,
                    if (tiny == null) "TINY_FORWARD_FAILED" else null
                )
                if (generationReset) {
                    telemetryManager.reconcileProfile(
                        preparing.profileKey,
                        promotionManager.snapshot(preparing.profileKey).modelGeneration
                    )
                }
                val boundCandidate = candidate.takeIf {
                    current.candidateCheckpointId == model.candidate?.checkpointId &&
                        current.candidateCheckpointChecksum == model.candidate?.checksum
                }
                val activated = current.copy(
                    preparationState = "PENDING",
                    statProbabilities = BinaryCodec.floats(stat.probabilities),
                    tinyProbabilities = tiny?.let { BinaryCodec.floats(it.probabilities) },
                    recentBaselineProbabilities = BinaryCodec.floats(recent.probabilities),
                    timeBaselineProbabilities = BinaryCodec.floats(time.probabilities),
                    statModelVersion = stat.modelVersion,
                    tinyModelVersion = tiny?.modelVersion,
                    candidateProbabilities = boundCandidate?.let { BinaryCodec.floats(it.probabilities) },
                    candidateInferenceNanos = boundCandidate?.inferenceNanos,
                    statInferenceNanos = stat.inferenceNanos,
                    tinyInferenceNanos = tiny?.inferenceNanos,
                    preparationFailure = if (tiny == null) "TINY_FORWARD_FAILED" else null
                )
                dao.updatePending(activated)
                registerDeadline(activated)
                val effective = composeDecision(stat, tiny, input, preparing.profileKey, promotion)
                _diagnostics.value = _diagnostics.value.copy(
                    preparationState = "PENDING",
                    statProbabilities = stat.asMap(),
                    tinyProbabilities = tiny?.asMap().orEmpty(),
                    effectiveProbabilities = effective.asMap(),
                    lastFailure = activated.preparationFailure
                )
                scope.launch { prefetchCoordinator.consider(effective.asMap(), activated.isPromotionHoldout, foreground) }
                scope.launch { maybeOfferSuggestion(activated, effective) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val current = dao.pending(preparing.decisionId)
            if (current?.resolutionStatus == "PENDING") {
                dao.censorPendingCas(current.decisionId, current.profileKey, "CENSORED_PREPARATION_FAILED")
            }
            _diagnostics.value = _diagnostics.value.copy(lastFailure = error.javaClass.simpleName)
        }
    }

    private suspend fun resolvePending(
        pending: PendingPredictionEntity,
        targetOutputId: String,
        resolvedByEventId: String,
        labelSource: String,
        family: ActionFamily
    ) {
        if (pending.resolutionStatus != "PENDING" || pending.preparationState != "PENDING" || pending.interventionState != "NONE") return
        val targetIndex = AppActionCatalog.outputIndex[targetOutputId] ?: return
        val availability = BinaryCodec.booleans(pending.availabilityMask)
        if (!availability.getOrElse(targetIndex) { false }) {
            dao.resolvePendingCas(
                pending.decisionId,
                pending.profileKey,
                "INVALIDATED_AVAILABILITY_MISMATCH",
                null,
                resolvedByEventId
            )
            deadlineJobs.remove(pending.decisionId)?.cancel()
            _diagnostics.value = _diagnostics.value.copy(lastFailure = "AVAILABILITY_MISMATCH")
            return
        }
        val input = restoreInput(pending)
        database.transaction {
            check(
                dao.resolvePendingCas(
                    pending.decisionId,
                    pending.profileKey,
                    "RESOLVED",
                    targetOutputId,
                    resolvedByEventId
                ) == 1
            ) { "prediction opportunity was already resolved or intervened" }
            evaluator.resolve(pending, targetOutputId)?.let { evaluation ->
                telemetryAggregateStore.contribute(evaluation)
            }
            statPredictor.update(input, targetOutputId)
            trainer.enqueue(OrganicTrainingSample(input = input, targetOutputId = targetOutputId, actionFamily = family, labelSource = labelSource))
            val learning = dao.learningState(pending.profileKey)
            dao.upsertLearningState(
                LearningStateEntity(
                    pending.profileKey,
                    learning?.statLearningStartedEpochDay ?: LocalDate.now(ZoneOffset.UTC).toEpochDay(),
                    learning?.tinyTrainingStartedEpochDay,
                    learning?.lastCommittedBatchId,
                    learning?.lastTrainingNanos ?: 0L,
                    learning?.lastTrainingLoss,
                    learning?.lastGradientNorm
                )
            )
        }
        deadlineJobs.remove(pending.decisionId)?.cancel()
        _diagnostics.value = _diagnostics.value.copy(lastResolution = targetOutputId)
        promotionManager.evaluate(pending.profileKey)
        telemetryManager.reconcileProfile(
            pending.profileKey,
            promotionManager.snapshot(pending.profileKey).modelGeneration
        )
        telemetryManager.onNewEvaluation(pending.profileKey)
        val retentionDays = preferencesManager.behaviorRetentionDays.first().coerceIn(7, 30)
        dao.deleteExpiredEvents(
            pending.profileKey,
            System.currentTimeMillis() - retentionDays * 86_400_000L
        )
        dao.trimEvents(pending.profileKey, MAX_BEHAVIOR_EVENTS)
        dao.trimShadowEvaluations(pending.profileKey, MAX_SHADOW_EVALUATIONS)
        dao.trimPromotionWindows(pending.profileKey, MAX_PROMOTION_WINDOWS)
        dao.trimPromotionTransitionJournals(pending.profileKey, MAX_PROMOTION_JOURNALS)
        scope.launch {
            delay(TRAINING_IDLE_GRACE_MS)
            runIdleTrainingSlice()
        }
    }

    private fun registerDeadline(pending: PendingPredictionEntity) {
        deadlineJobs.remove(pending.decisionId)?.cancel()
        deadlineJobs[pending.decisionId] = scope.launch {
            val remaining = (pending.labelDeadlineElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            delay(remaining + DEADLINE_RACE_GRACE_MS)
            val lock = profileLocks.getOrPut(pending.profileKey) { Mutex() }
            lock.withLock {
                val current = dao.pending(pending.decisionId) ?: return@withLock
                if (current.processInstanceId != processInstanceId) return@withLock
                if (!OrganicLabelPolicy.isTimeoutEligible(
                        foreground && interactive,
                        current.interventionState,
                        current.preparationState,
                        current.resolutionStatus
                    )
                ) {
                    if (current.resolutionStatus == "PENDING" && current.interventionState == "TAINTED_CHAIN") {
                        dao.censorPendingCas(
                            current.decisionId,
                            current.profileKey,
                            "CENSORED_TAINTED_CHAIN_TIMEOUT"
                        )
                    }
                    return@withLock
                }
                resolvePending(
                    current,
                    AppActionCatalog.NONE_OUTPUT_ID,
                    UUID.randomUUID().toString(),
                    "INTERVENTION_FREE_TIMEOUT",
                    ActionFamily.TECHNICAL
                )
            }
        }
    }

    private suspend fun censorActive(reason: String) {
        val activeProfile = profileKey ?: return
        val lock = profileLocks.getOrPut(activeProfile) { Mutex() }
        lock.withLock {
            dao.latestPending(activeProfile)?.let { pending ->
                dao.censorPendingCas(pending.decisionId, activeProfile, reason)
                deadlineJobs.remove(pending.decisionId)?.cancel()
            }
            executionEpoch.incrementAndGet()
        dao.cancelProfileLeases(activeProfile)
        prefetchCoordinator.cancelAll()
        paymentQrRepository.clearSensitive()
        }
    }

    private suspend fun maybeOfferSuggestion(pending: PendingPredictionEntity, effective: NextActionProbabilityVector) {
        if (pending.isPromotionHoldout || _sensitiveUiVisible.value || !isSuggestionSurfaceAllowed(lastRoute) ||
            !preferencesManager.personalizationEnabled.first()
        ) return
        val now = SystemClock.elapsedRealtime()
        val today = LocalDate.now(ZoneOffset.UTC).toEpochDay()
        if (suggestionEpochDay != today) {
            suggestionEpochDay = today
            dailySuggestionCount = 0
        }
        if (now - lastSuggestionElapsedMs < SUGGESTION_MIN_INTERVAL_MS ||
            sessionSuggestionCount >= MAX_SESSION_SUGGESTIONS ||
            dailySuggestionCount >= MAX_DAILY_SUGGESTIONS
        ) return
        val ranked = effective.rankedIndices()
        val top = ranked.firstOrNull() ?: return
        val second = ranked.getOrNull(1)
        val outputId = effective.outputIds[top]
        val action = AppActionId.fromStableId(outputId) ?: return
        if ((suggestionCooldownUntil[outputId] ?: 0L) > now) return
        if ((preferencesManager.suggestionActionSuppressedUntil(pending.profileKey, outputId).first() ?: 0L) >
            System.currentTimeMillis()
        ) return
        val probability = effective.probabilities[top]
        val margin = probability - (second?.let { effective.probabilities[it] } ?: 0f)
        if (probability < SUGGESTION_THRESHOLD || margin < SUGGESTION_MARGIN ||
            dao.trainingActionCount(pending.profileKey, outputId) < MIN_ACTION_SUGGESTION_SAMPLES
        ) return
        if (showSuggestion(pending.decisionId, action, probability)) {
            lastSuggestionElapsedMs = now
            sessionSuggestionCount++
            dailySuggestionCount++
        }
    }

    private suspend fun composeDecision(
        stat: NextActionProbabilityVector,
        tiny: NextActionProbabilityVector?,
        input: PredictionInput,
        profileKey: String,
        promotion: PromotionSnapshot
    ): NextActionProbabilityVector {
        val lambdas = if (tiny == null) emptyMap() else promotionManager.actionLambdas(profileKey)
        val raw = FloatArray(stat.probabilities.size) { index ->
            val lambda = lambdas[stat.outputIds[index]] ?: 0f
            (1f - lambda) * stat.probabilities[index] + lambda * (tiny?.probabilities?.get(index) ?: stat.probabilities[index])
        }
        val masked = FloatArray(raw.size) { if (input.businessAvailability[it]) raw[it] else 0f }
        val sum = masked.sum()
        val probabilities = if (sum > 0f) FloatArray(masked.size) { masked[it] / sum } else stat.probabilities.copyOf()
        return NextActionProbabilityVector(stat.outputIds, probabilities, modelVersion = 1)
    }

    private fun restoreInput(pending: PendingPredictionEntity): PredictionInput {
        val snapshot = gson.fromJson(pending.contextSnapshotJson, ContextSnapshot::class.java)
        return PredictionInput(
            pending.profileKey,
            pending.decisionId,
            pending.featureSchemaVersion,
            pending.outputSchemaVersion,
            pending.actionCatalogVersion,
            com.ahu.ahutong.personalization.context.ImmutableFloatVector(BinaryCodec.floats(pending.features)),
            com.ahu.ahutong.personalization.context.ImmutableBooleanVector(BinaryCodec.booleans(pending.availabilityMask)),
            pending.inputDigest,
            snapshot
        )
    }

    private fun snapshot(epochMs: Long, previousAction: AppActionId?): ContextSnapshot {
        val time = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val personal = personalFamilySignals()
        val nowElapsed = SystemClock.elapsedRealtime()
        return ContextSnapshot(
            epochDay = Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay(),
            minuteOfDay = time.hour * 60 + time.minute,
            dayType = if (time.dayOfWeek.value >= 6) DayType.WEEKEND else DayType.WEEKDAY,
            route = lastRoute,
            previousAction = previousAction,
            recentActions = recentActions.toList(),
            balanceBucket = balanceBucket,
            balanceFresh = balanceFresh,
            examDistanceBucket = examBucket,
            sessionDurationBucket = ((SystemClock.elapsedRealtime() - sessionStartedElapsedMs) / 60_000L).toInt().coerceIn(0, 7),
            semesterWeek = CurrentWeekResolver.resolveLocalConfig(time.toLocalDate())?.config?.week?.coerceIn(1, 24),
            foregroundGapBucket = foregroundGapBucket,
            sessionDepth = recentActions.size,
            pageDwellBucket = routeChangedElapsedMs.takeIf { it > 0 }?.let { gapBucket(nowElapsed - it) },
            recentActionSources = recentActionSources.toList(),
            personalFamilyFrequencies = personal.first,
            personalFamilyRecencies = personal.second
        )
    }

    private fun personalFamilySignals(): Pair<List<Float>, List<Float>> {
        val frequencies = FloatArray(ActionFamily.entries.size)
        val recencies = FloatArray(ActionFamily.entries.size)
        var total = 0f
        organicActionHistory.reversed().forEachIndexed { index, action ->
            val familyIndex = AppActionCatalog.spec(action).family.ordinal
            val weight = 1f / (1f + index / 8f)
            frequencies[familyIndex] += weight
            total += weight
            if (recencies[familyIndex] == 0f) recencies[familyIndex] = 1f / (index + 1f)
        }
        if (total > 0f) frequencies.indices.forEach { frequencies[it] /= total }
        return frequencies.toList() to recencies.toList()
    }

    private fun gapBucket(durationMs: Long): Int = when {
        durationMs < 5_000L -> 0
        durationMs < 30_000L -> 1
        durationMs < 2 * 60_000L -> 2
        durationMs < 5 * 60_000L -> 3
        durationMs < 15 * 60_000L -> 4
        durationMs < 60 * 60_000L -> 5
        durationMs < 6 * 60 * 60_000L -> 6
        else -> 7
    }

    private fun isSuggestionSurfaceAllowed(route: String?): Boolean = route != null &&
        route != "login" && route != "setup" && route != "splash" && route != "debug" &&
        route != "electricity_pay" && !route.contains("deposit") && !route.contains("recharge")

    private fun event(
        eventId: String,
        actionInstanceId: String,
        profile: String,
        session: String,
        sequenceNo: Long,
        eventType: String,
        action: AppActionId?,
        source: ActionSource,
        elapsed: Long,
        resolvedDecisionId: String?
    ): BehaviorEventEntity {
        val now = System.currentTimeMillis()
        val snapshot = snapshot(now, lastAction)
        return BehaviorEventEntity(
            eventId,
            actionInstanceId,
            profile,
            session,
            processInstanceId,
            sequenceNo,
            eventType,
            action?.stableId,
            source.name,
            now,
            elapsed,
            elapsed - sessionStartedElapsedMs,
            null,
            resolvedDecisionId,
            snapshot.minuteOfDay / 60,
            snapshot.dayType.name,
            snapshot.balanceBucket.name,
            snapshot.examDistanceBucket.name,
            FeatureExtractor.FEATURE_SCHEMA_VERSION
        )
    }

    private suspend fun insertLifecycleEvent(type: String, source: ActionSource) {
        val activeProfile = profileKey ?: return
        val activeSession = sessionId ?: return
        dao.insertEvent(
            event(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                activeProfile,
                activeSession,
                sequence.incrementAndGet(),
                type,
                null,
                source,
                SystemClock.elapsedRealtime(),
                null
            )
        )
    }

    private fun bucket(holdoutSeed: String, decisionId: String, namespace: String): Int {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(holdoutSeed.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal("v1:$namespace:$decisionId".toByteArray(Charsets.UTF_8))
        return (digest[0].toInt() and 0xff) % 100
    }

    private fun NextActionProbabilityVector.asMap(): Map<String, Float> =
        outputIds.indices.associate { outputIds[it] to probabilities[it] }

    private companion object {
        const val LABEL_WINDOW_POLICY_VERSION = 1
        const val CONTEXT_DEBOUNCE_MS = 30_000L
        const val DEADLINE_RACE_GRACE_MS = 250L
        const val HOLDOUT_PERCENT = 15
        const val CANDIDATE_HOLDOUT_PERCENT = 20
        const val SUGGESTION_THRESHOLD = 0.42f
        const val SUGGESTION_MARGIN = 0.10f
        const val MIN_ACTION_SUGGESTION_SAMPLES = 20
        const val SUGGESTION_MIN_INTERVAL_MS = 60_000L
        const val ACTION_DISMISS_COOLDOWN_MS = 10 * 60_000L
        const val ACTION_SUPPRESS_COOLDOWN_MS = 30L * 24 * 60 * 60_000L
        const val MAX_SESSION_SUGGESTIONS = 3
        const val MAX_DAILY_SUGGESTIONS = 8
        const val SUGGESTION_VISIBLE_TTL_MS = 12_000L
        const val TRAINING_IDLE_GRACE_MS = 1_500L
        const val MAX_BEHAVIOR_EVENTS = 20_000
        const val MAX_SHADOW_EVALUATIONS = 20_000
        const val MAX_PROMOTION_WINDOWS = 256
        const val MAX_PROMOTION_JOURNALS = 256
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BehaviorRuntimeEntryPoint {
    fun behaviorPredictionRuntime(): BehaviorPredictionRuntime
}

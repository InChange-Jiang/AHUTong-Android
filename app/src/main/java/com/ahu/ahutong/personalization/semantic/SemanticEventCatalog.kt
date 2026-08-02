package com.ahu.ahutong.personalization.semantic

import com.ahu.ahutong.personalization.action.ActionSource
import com.ahu.ahutong.personalization.action.AppActionId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class SemanticEventFamily {
    SETTING_CHANGED,
    QUERY_FILTER_COMMITTED,
    CONTENT_STATE_CHANGED,
    BUSINESS_STATE_CHANGED,
    FLOW_STEP_COMPLETED,
    FLOW_ABANDONED,
    LOAD_RESULT_CHANGED,
    LOCAL_PRESET_APPLIED
}

enum class SemanticDomain {
    HOME,
    PAYMENT,
    SCHEDULE,
    WEATHER,
    FREE_CLASSROOM,
    GRADE,
    EXAM,
    LOST_FOUND,
    REPOSITORY,
    ELECTRICITY,
    EVALUATION,
    APPEARANCE,
    UNKNOWN
}

enum class SemanticChangeKind { ENABLED, DISABLED, INCREASED, DECREASED, REPLACED, ADDED, REMOVED, COMMITTED, UNKNOWN }

enum class ContentStateBucket { UNKNOWN, FRESH, STALE, EMPTY, LOADING, READY, ERROR }

enum class ResultCountBucket { UNKNOWN, ZERO, ONE_TO_FIVE, SIX_TO_TWENTY, TWENTY_ONE_PLUS }

enum class ErrorTypeBucket { NONE, UNKNOWN, NETWORK, AUTHENTICATION, SERVER, PARSE, LOCAL }

/** Stable, versioned mutation identifiers. Values are never derived from UI labels. */
enum class MutationId(
    val domain: SemanticDomain,
    val defaultFamily: SemanticEventFamily = SemanticEventFamily.SETTING_CHANGED
) {
    HOME_DEFAULT_QR_CHANGED(SemanticDomain.HOME),
    COURSE_REMINDER_CHANGED(SemanticDomain.SCHEDULE),
    COURSE_LIVE_COUNTDOWN_CHANGED(SemanticDomain.SCHEDULE),
    SCHEDULE_OVERVIEW_CHANGED(SemanticDomain.SCHEDULE),
    SCHEDULE_WEEK_CHANGED(SemanticDomain.SCHEDULE, SemanticEventFamily.BUSINESS_STATE_CHANGED),
    SCHEDULE_SEMESTER_PREVIEW_CHANGED(SemanticDomain.SCHEDULE, SemanticEventFamily.BUSINESS_STATE_CHANGED),
    THEME_CHANGED(SemanticDomain.APPEARANCE),
    LIQUID_GLASS_CHANGED(SemanticDomain.APPEARANCE),
    REPOSITORY_ACCELERATION_CHANGED(SemanticDomain.REPOSITORY),
    WEATHER_HOME_CONFIG_CHANGED(SemanticDomain.WEATHER),
    HOME_WIDGET_ADDED(SemanticDomain.HOME),
    HOME_WIDGET_REMOVED(SemanticDomain.HOME),
    HOME_WIDGET_MOVED(SemanticDomain.HOME),
    FREE_CLASSROOM_QUERY_COMMITTED(SemanticDomain.FREE_CLASSROOM, SemanticEventFamily.QUERY_FILTER_COMMITTED),
    GRADE_FILTER_COMMITTED(SemanticDomain.GRADE, SemanticEventFamily.QUERY_FILTER_COMMITTED),
    LOST_FOUND_FILTER_COMMITTED(SemanticDomain.LOST_FOUND, SemanticEventFamily.QUERY_FILTER_COMMITTED),
    ELECTRICITY_PRESET_COMMITTED(SemanticDomain.ELECTRICITY, SemanticEventFamily.QUERY_FILTER_COMMITTED),
    CONTENT_STATE_CHANGED(SemanticDomain.UNKNOWN, SemanticEventFamily.CONTENT_STATE_CHANGED),
    LOAD_RESULT_CHANGED(SemanticDomain.UNKNOWN, SemanticEventFamily.LOAD_RESULT_CHANGED),
    FLOW_STEP_COMPLETED(SemanticDomain.UNKNOWN, SemanticEventFamily.FLOW_STEP_COMPLETED),
    FLOW_ABANDONED(SemanticDomain.UNKNOWN, SemanticEventFamily.FLOW_ABANDONED),
    LOCAL_PRESET_APPLIED(SemanticDomain.UNKNOWN, SemanticEventFamily.LOCAL_PRESET_APPLIED)
}

data class SemanticContext(
    val eventFamily: SemanticEventFamily,
    val domain: SemanticDomain,
    val semanticId: String,
    val changeKind: SemanticChangeKind,
    val ageBucket: Int,
    val changeSetSize: Int,
    val stable: Boolean,
    val affectedCandidateSetVersion: Int = AffectedActionCatalog.VERSION,
    val coarseValueBucket: String = "UNKNOWN"
)

data class ContentContext(
    val domain: SemanticDomain,
    val state: ContentStateBucket,
    val freshnessBucket: Int,
    val resultCount: ResultCountBucket,
    val errorType: ErrorTypeBucket
)

/**
 * Product-facing candidate scope. This is deliberately separate from the full model output
 * catalog: evaluation and training always keep the complete probability vector.
 */
sealed interface ProductCandidateScope {
    data object Ordinary : ProductCandidateScope
    data class Targeted(val actions: Set<AppActionId>) : ProductCandidateScope {
        init {
            require(actions.isNotEmpty())
        }
    }
    data object Suppress : ProductCandidateScope
}

data class CommittedMutation(
    val mutationId: MutationId,
    val oldValue: Any?,
    val newValue: Any?,
    val source: ActionSource,
    val route: String?,
    val domainOverride: SemanticDomain? = null,
    val coarseValueBucket: String? = null,
    val familyOverride: SemanticEventFamily? = null,
    val committedAtEpochDay: Long,
    val occurredAtElapsedMs: Long,
    val tainted: Boolean = false,
    val mutationBatchId: String = UUID.randomUUID().toString()
)

data class NormalizedSemanticEvent(
    val eventId: String,
    val eventFamily: SemanticEventFamily,
    val domain: SemanticDomain,
    val semanticId: String,
    val changeKind: SemanticChangeKind,
    val coarseValueBucket: String,
    val route: String?,
    val affectedActionIds: Set<AppActionId>,
    val affectedCandidateSetVersion: Int,
    val source: ActionSource,
    val committedAtEpochDay: Long,
    val occurredAtElapsedMs: Long,
    val semanticSchemaVersion: Int,
    val tainted: Boolean,
    val mutationBatchId: String
)

object SemanticEventCatalog {
    const val SCHEMA_VERSION = 1

    fun isStableSemanticId(value: String): Boolean =
        value.matches(Regex("[A-Z][A-Z0-9_]{2,63}"))
}

object AffectedActionCatalog {
    const val VERSION = 2

    private val mappings: Map<MutationId, Set<AppActionId>> = mapOf(
        MutationId.HOME_DEFAULT_QR_CHANGED to setOf(AppActionId.OPEN_HOME),
        MutationId.COURSE_REMINDER_CHANGED to setOf(AppActionId.VIEW_SCHEDULE),
        MutationId.COURSE_LIVE_COUNTDOWN_CHANGED to setOf(AppActionId.VIEW_SCHEDULE),
        MutationId.SCHEDULE_OVERVIEW_CHANGED to setOf(AppActionId.VIEW_SCHEDULE),
        MutationId.SCHEDULE_WEEK_CHANGED to emptySet(),
        MutationId.SCHEDULE_SEMESTER_PREVIEW_CHANGED to emptySet(),
        MutationId.WEATHER_HOME_CONFIG_CHANGED to setOf(AppActionId.OPEN_HOME),
        MutationId.HOME_WIDGET_ADDED to emptySet(),
        MutationId.HOME_WIDGET_REMOVED to emptySet(),
        MutationId.HOME_WIDGET_MOVED to emptySet(),
        MutationId.REPOSITORY_ACCELERATION_CHANGED to setOf(AppActionId.OPEN_REPOSITORY),
        MutationId.FREE_CLASSROOM_QUERY_COMMITTED to setOf(AppActionId.FIND_FREE_CLASSROOM),
        MutationId.GRADE_FILTER_COMMITTED to setOf(AppActionId.VIEW_GRADES),
        MutationId.LOST_FOUND_FILTER_COMMITTED to setOf(AppActionId.OPEN_LOST_FOUND),
        MutationId.ELECTRICITY_PRESET_COMMITTED to setOf(AppActionId.OPEN_ELECTRICITY_PAYMENT)
    )

    private val homeWidgetActions = mapOf(
        "BATHROOM" to AppActionId.OPEN_BATHROOM_DEPOSIT,
        "ELECTRICITY" to AppActionId.OPEN_ELECTRICITY_PAYMENT,
        "GRADE" to AppActionId.VIEW_GRADES,
        "PHONE_BOOK" to AppActionId.OPEN_PHONE_BOOK,
        "EXAM" to AppActionId.VIEW_EXAM_ROOM,
        "EVALUATION" to AppActionId.OPEN_EVALUATION,
        "SCHOOL_CALENDAR" to AppActionId.VIEW_SCHOOL_CALENDAR,
        "FREE_CLASSROOM" to AppActionId.FIND_FREE_CLASSROOM,
        "LOST_FOUND" to AppActionId.OPEN_LOST_FOUND,
        "WEATHER" to AppActionId.VIEW_WEATHER,
        "REPOSITORY" to AppActionId.OPEN_REPOSITORY
    )

    fun affectedActions(mutationId: MutationId, coarseValueBucket: String? = null): Set<AppActionId> {
        val base = mappings[mutationId].orEmpty()
        if (mutationId != MutationId.HOME_WIDGET_ADDED) return base
        return homeWidgetActions[coarseValueBucket]?.let { base + it } ?: base
    }

    fun affectedActionsForSemanticId(semanticId: String): Set<AppActionId> {
        MutationId.entries.firstOrNull { it.name == semanticId }?.let { return affectedActions(it) }
        val widgetMutation = HOME_WIDGET_MUTATIONS.firstOrNull { semanticId.startsWith("${it.name}_") }
            ?: return emptySet()
        val bucket = semanticId.removePrefix("${widgetMutation.name}_")
        return affectedActions(widgetMutation, bucket)
    }

    private val HOME_WIDGET_MUTATIONS = setOf(
        MutationId.HOME_WIDGET_ADDED,
        MutationId.HOME_WIDGET_REMOVED,
        MutationId.HOME_WIDGET_MOVED
    )
}

/** Context-aware product policy. An observed semantic/content context is fail-closed. */
object ProductCandidateResolver {
    fun resolve(
        semantic: SemanticContext?,
        content: ContentContext?,
        route: String?
    ): ProductCandidateScope {
        contentErrorTarget(content)?.let { return ProductCandidateScope.Targeted(setOf(it)) }
        if (semantic == null) {
            return if (content == null) ProductCandidateScope.Ordinary else ProductCandidateScope.Suppress
        }
        return when {
            semantic.semanticId == MutationId.HOME_DEFAULT_QR_CHANGED.name -> targeted(AppActionId.OPEN_HOME)
            semantic.semanticId == MutationId.WEATHER_HOME_CONFIG_CHANGED.name -> targeted(AppActionId.OPEN_HOME)
            semantic.semanticId == MutationId.COURSE_REMINDER_CHANGED.name -> targeted(AppActionId.VIEW_SCHEDULE)
            semantic.semanticId == MutationId.COURSE_LIVE_COUNTDOWN_CHANGED.name -> targeted(AppActionId.VIEW_SCHEDULE)
            semantic.semanticId == MutationId.SCHEDULE_OVERVIEW_CHANGED.name -> {
                if (route == "schedule") ProductCandidateScope.Suppress else targeted(AppActionId.VIEW_SCHEDULE)
            }
            semantic.semanticId == MutationId.REPOSITORY_ACCELERATION_CHANGED.name -> targeted(AppActionId.OPEN_REPOSITORY)
            semantic.semanticId.startsWith("${MutationId.HOME_WIDGET_ADDED.name}_") -> {
                val bucket = semantic.semanticId.removePrefix("${MutationId.HOME_WIDGET_ADDED.name}_")
                AffectedActionCatalog.affectedActions(MutationId.HOME_WIDGET_ADDED, bucket)
                    .singleOrNull()
                    ?.let(::targeted)
                    ?: ProductCandidateScope.Suppress
            }
            else -> ProductCandidateScope.Suppress
        }
    }

    private fun contentErrorTarget(content: ContentContext?): AppActionId? {
        if (content?.state != ContentStateBucket.ERROR) return null
        return when (content.domain) {
            SemanticDomain.GRADE -> AppActionId.RETRY_GRADE
            SemanticDomain.EXAM -> AppActionId.RETRY_EXAM
            SemanticDomain.REPOSITORY -> AppActionId.RETRY_REPOSITORY
            else -> null
        }
    }

    private fun targeted(action: AppActionId): ProductCandidateScope =
        ProductCandidateScope.Targeted(setOf(action))
}

@Singleton
class SemanticEventRecorder @Inject constructor() {
    fun normalize(mutation: CommittedMutation): NormalizedSemanticEvent? {
        if (valuesEqual(mutation.oldValue, mutation.newValue)) return null
        val stableBucket = mutation.coarseValueBucket?.takeIf(::isStableBucket)
        val semanticId = if (mutation.mutationId in HOME_WIDGET_MUTATIONS && stableBucket != null) {
            "${mutation.mutationId.name}_$stableBucket"
        } else {
            mutation.mutationId.name
        }
        require(SemanticEventCatalog.isStableSemanticId(semanticId))
        return NormalizedSemanticEvent(
            eventId = UUID.randomUUID().toString(),
            eventFamily = mutation.familyOverride ?: mutation.mutationId.defaultFamily,
            domain = mutation.domainOverride ?: mutation.mutationId.domain,
            semanticId = semanticId,
            changeKind = changeKind(mutation.oldValue, mutation.newValue),
            coarseValueBucket = stableBucket ?: "CHANGED",
            route = mutation.route,
            affectedActionIds = AffectedActionCatalog.affectedActions(mutation.mutationId, stableBucket),
            affectedCandidateSetVersion = AffectedActionCatalog.VERSION,
            source = mutation.source,
            committedAtEpochDay = mutation.committedAtEpochDay,
            occurredAtElapsedMs = mutation.occurredAtElapsedMs,
            semanticSchemaVersion = SemanticEventCatalog.SCHEMA_VERSION,
            tainted = mutation.tainted || mutation.source != ActionSource.ORGANIC,
            mutationBatchId = mutation.mutationBatchId
        )
    }

    private fun valuesEqual(oldValue: Any?, newValue: Any?): Boolean = when {
        oldValue is Float && newValue is Float -> oldValue.compareTo(newValue) == 0
        oldValue is Double && newValue is Double -> oldValue.compareTo(newValue) == 0
        else -> oldValue == newValue
    }

    private fun changeKind(oldValue: Any?, newValue: Any?): SemanticChangeKind = when {
        oldValue is Boolean && newValue is Boolean -> if (newValue) SemanticChangeKind.ENABLED else SemanticChangeKind.DISABLED
        oldValue is Number && newValue is Number && newValue.toDouble() > oldValue.toDouble() -> SemanticChangeKind.INCREASED
        oldValue is Number && newValue is Number && newValue.toDouble() < oldValue.toDouble() -> SemanticChangeKind.DECREASED
        oldValue == null && newValue != null -> SemanticChangeKind.ADDED
        oldValue != null && newValue == null -> SemanticChangeKind.REMOVED
        else -> SemanticChangeKind.REPLACED
    }

    private fun isStableBucket(value: String): Boolean = value.matches(Regex("[A-Z][A-Z0-9_]{1,31}"))

    private companion object {
        val HOME_WIDGET_MUTATIONS = setOf(
            MutationId.HOME_WIDGET_ADDED,
            MutationId.HOME_WIDGET_REMOVED,
            MutationId.HOME_WIDGET_MOVED
        )
    }
}

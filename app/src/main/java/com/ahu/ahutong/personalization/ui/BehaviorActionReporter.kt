package com.ahu.ahutong.personalization.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ahu.ahutong.personalization.action.ActionSource
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.runtime.BehaviorRuntimeEntryPoint
import com.ahu.ahutong.personalization.context.ExamDistanceBucket
import com.ahu.ahutong.personalization.semantic.MutationId
import dagger.hilt.android.EntryPointAccessors

class BehaviorActionReporter internal constructor(
    private val runtime: com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
) {
    fun organic(action: AppActionId) = runtime.recordActionIntentAsync(action, ActionSource.ORGANIC)
    fun debug(action: AppActionId) = runtime.recordActionIntentAsync(action, ActionSource.DEBUG)
    fun examDistance(bucket: ExamDistanceBucket) = runtime.onBusinessContextChanged(newExamBucket = bucket)
    fun cmbRechargePreferenceChanged(oldValue: Boolean, newValue: Boolean) =
        runtime.recordCommittedMutationAsync(
            MutationId.CMB_RECHARGE_PREFERENCE_CHANGED,
            oldValue,
            newValue,
            coarseValueBucket = if (newValue) "ENABLED" else "DISABLED"
        )
}

@Composable
fun rememberBehaviorActionReporter(): BehaviorActionReporter {
    val context = LocalContext.current
    return remember(context.applicationContext) {
        val runtime = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BehaviorRuntimeEntryPoint::class.java
        ).behaviorPredictionRuntime()
        BehaviorActionReporter(runtime)
    }
}

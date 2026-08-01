package com.ahu.ahutong.personalization.telemetry

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TelemetryWorkerEntryPoint {
    fun telemetryUploader(): TelemetryUploader
}

class ModelQualityTelemetryWorker(
    appContext: Context,
    parameters: WorkerParameters
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val uploader = EntryPointAccessors.fromApplication(
            applicationContext,
            TelemetryWorkerEntryPoint::class.java
        ).telemetryUploader()
        return runCatching { uploader.uploadDue() }.fold(
            onSuccess = { pending -> if (pending) Result.retry() else Result.success() },
            onFailure = { Result.retry() }
        )
    }
}

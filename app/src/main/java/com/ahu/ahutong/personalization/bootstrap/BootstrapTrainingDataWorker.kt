package com.ahu.ahutong.personalization.bootstrap

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BootstrapTrainingWorkerEntryPoint {
    fun bootstrapTrainingUploader(): BootstrapTrainingUploader
}

class BootstrapTrainingDataWorker(
    appContext: Context,
    parameters: WorkerParameters
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val uploader = EntryPointAccessors.fromApplication(
            applicationContext,
            BootstrapTrainingWorkerEntryPoint::class.java
        ).bootstrapTrainingUploader()
        return runCatching { uploader.uploadDue() }.fold(
            onSuccess = { pending -> if (pending) Result.retry() else Result.success() },
            onFailure = { Result.retry() }
        )
    }
}

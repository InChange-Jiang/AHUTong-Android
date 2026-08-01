package com.ahu.ahutong.personalization.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        BehaviorEventEntity::class,
        PendingPredictionEntity::class,
        ProductExecutionLeaseEntity::class,
        ActionStatEntity::class,
        TrainingSampleEntity::class,
        TrainingBatchJournalEntity::class,
        ShadowEvaluationEntity::class,
        CandidateShadowEvaluationEntity::class,
        TinyPromotionStateEntity::class,
        TinyRuntimeHealthStateEntity::class,
        PromotionEvaluationWindowEntity::class,
        PromotionActionQualificationEntity::class,
        PromotionTransitionJournalEntity::class,
        LearningStateEntity::class,
        TelemetryReportEntity::class,
        TelemetryStateEntity::class,
        TelemetryAggregateWindowEntity::class,
        TelemetryDeletionTombstoneEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class BehaviorDatabase : RoomDatabase() {
    abstract fun behaviorDao(): BehaviorDao
}

@Module
@InstallIn(SingletonComponent::class)
object BehaviorStorageModule {
    @Provides
    @Singleton
    fun provideBehaviorDatabase(@ApplicationContext context: Context): BehaviorDatabase =
        Room.databaseBuilder(context, BehaviorDatabase::class.java, "behavior_prediction.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides
    fun provideBehaviorDao(database: BehaviorDatabase): BehaviorDao = database.behaviorDao()
}

suspend inline fun <T> BehaviorDatabase.transaction(crossinline block: suspend () -> T): T =
    withTransaction { block() }

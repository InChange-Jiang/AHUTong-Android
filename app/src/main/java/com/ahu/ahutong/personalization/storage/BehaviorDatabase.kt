package com.ahu.ahutong.personalization.storage

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

const val BEHAVIOR_DATABASE_VERSION = 5

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
        TelemetryV3AggregateWindowEntity::class,
        TelemetryDeletionTombstoneEntity::class,
        SemanticEventEntity::class,
        SemanticChangeSetEntity::class,
        PendingJourneyEntity::class,
        JourneyActionStatEntity::class,
        JourneyTrainingSampleEntity::class,
        JourneyShadowEvaluationEntity::class,
        LocalParameterPresetEntity::class,
        PresetUsageStatEntity::class,
        TargetedPredictionFeedbackEntity::class,
        PresetRecommendationInteractionEntity::class,
        TaskModelStateEntity::class,
        TaskTrainingBatchJournalEntity::class,
        PresetTrainingSampleEntity::class,
        PresetShadowEvaluationEntity::class,
        BootstrapTrainingConsentEntity::class,
        BootstrapTrainingExampleEntity::class,
        BootstrapTrainingBatchEntity::class,
        BootstrapTrainingDeletionTombstoneEntity::class
    ],
    version = BEHAVIOR_DATABASE_VERSION,
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
        BehaviorDatabaseFactory.open(context)

    @Provides
    fun provideBehaviorDao(database: BehaviorDatabase): BehaviorDao = database.behaviorDao()
}

suspend inline fun <T> BehaviorDatabase.transaction(crossinline block: suspend () -> T): T =
    withTransaction { block() }

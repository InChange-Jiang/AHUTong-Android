package com.ahu.ahutong.personalization

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahu.ahutong.personalization.action.ActionFamily
import com.ahu.ahutong.personalization.action.ActionSource
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.action.OrganicLabelPolicy
import com.ahu.ahutong.personalization.context.BalanceBucket
import com.ahu.ahutong.personalization.context.ContextSnapshot
import com.ahu.ahutong.personalization.context.DayType
import com.ahu.ahutong.personalization.context.ExamDistanceBucket
import com.ahu.ahutong.personalization.context.FeatureExtractor
import com.ahu.ahutong.personalization.context.PredictionInput
import com.ahu.ahutong.personalization.inference.DecayedFrequencyPredictor
import com.ahu.ahutong.personalization.model.ModelStateStore
import com.ahu.ahutong.personalization.promotion.LocalPromotionManager
import com.ahu.ahutong.personalization.storage.BehaviorDatabase
import com.ahu.ahutong.personalization.training.KotlinOnDeviceTrainer
import com.ahu.ahutong.personalization.training.OrganicTrainingSample
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PromotionSchemaRegressionTest {
    private lateinit var context: Context
    private lateinit var database: BehaviorDatabase
    private lateinit var modelStateStore: ModelStateStore
    private val profiles = mutableSetOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BehaviorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        modelStateStore = ModelStateStore(context)
    }

    @After
    fun tearDown() = runBlocking {
        profiles.forEach { profile -> modelStateStore.reset(profile) }
        database.close()
    }

    @Test
    fun newStateUsesCurrentSchemaAndRestartDoesNotResetIt() = runBlocking {
        val profile = profile()
        val firstManager = LocalPromotionManager(database.behaviorDao(), modelStateStore)
        firstManager.snapshot(profile)
        val first = requireNotNull(database.behaviorDao().promotionState(profile))

        assertEquals(FeatureExtractor.FEATURE_SCHEMA_VERSION, first.featureSchemaVersion)
        assertEquals("INITIALIZED_SHADOW", first.lastTransitionReason)
        recordEligibleOrganic(profile)

        val restartedManager = LocalPromotionManager(database.behaviorDao(), ModelStateStore(context))
        restartedManager.snapshot(profile)
        val afterRestart = requireNotNull(database.behaviorDao().promotionState(profile))

        assertEquals(first.transitionSequence, afterRestart.transitionSequence)
        assertEquals(first.stageGeneration, afterRestart.stageGeneration)
        assertEquals("INITIALIZED_SHADOW", afterRestart.lastTransitionReason)
        assertEquals(1, database.behaviorDao().trainingSampleCount(profile))
        assertTrue(database.behaviorDao().actionStats(profile).isNotEmpty())
    }

    @Test
    fun realSchemaUpgradeResetsExactlyOnceAndUsesCurrentSchema() = runBlocking {
        val profile = profile()
        val manager = LocalPromotionManager(database.behaviorDao(), modelStateStore)
        manager.snapshot(profile)
        recordEligibleOrganic(profile)
        val before = requireNotNull(database.behaviorDao().promotionState(profile))
        database.behaviorDao().upsertPromotionState(
            before.copy(featureSchemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION - 1)
        )

        manager.snapshot(profile)
        val reset = requireNotNull(database.behaviorDao().promotionState(profile))
        assertEquals(FeatureExtractor.FEATURE_SCHEMA_VERSION, reset.featureSchemaVersion)
        assertEquals("SCHEMA_INCOMPATIBLE_RESET", reset.lastTransitionReason)
        assertEquals(before.transitionSequence + 1, reset.transitionSequence)
        assertEquals(0, database.behaviorDao().trainingSampleCount(profile))
        assertTrue(database.behaviorDao().actionStats(profile).isEmpty())

        manager.snapshot(profile)
        val checkedAgain = requireNotNull(database.behaviorDao().promotionState(profile))
        assertEquals(reset.transitionSequence, checkedAgain.transitionSequence)
        assertEquals(reset.stageGeneration, checkedAgain.stageGeneration)
    }

    @Test
    fun eligibleOrganicUpdatesStatsAndTrainingWhileDirectedSourcesDoNot() = runBlocking {
        val organicProfile = profile()
        assertTrue(OrganicLabelPolicy.isEligible(AppActionId.VIEW_SCHEDULE, ActionSource.ORGANIC))
        recordEligibleOrganic(organicProfile)
        assertEquals(1, database.behaviorDao().trainingSampleCount(organicProfile))
        assertTrue(
            database.behaviorDao().actionStats(organicProfile).any {
                it.actionId == AppActionId.VIEW_SCHEDULE.stableId && it.positiveMass > 0.0
            }
        )

        val directedProfile = profile()
        listOf(
            ActionSource.SUGGESTION,
            ActionSource.DEEPLINK,
            ActionSource.RESTORE,
            ActionSource.SYSTEM,
            ActionSource.USER_PREFERENCE,
            ActionSource.DEBUG
        ).forEach { source ->
            assertFalse(OrganicLabelPolicy.isEligible(AppActionId.VIEW_SCHEDULE, source))
            if (OrganicLabelPolicy.isEligible(AppActionId.VIEW_SCHEDULE, source)) {
                recordEligibleOrganic(directedProfile)
            }
        }
        assertEquals(0, database.behaviorDao().trainingSampleCount(directedProfile))
        assertTrue(database.behaviorDao().actionStats(directedProfile).isEmpty())
    }

    private suspend fun recordEligibleOrganic(profile: String) {
        val input = input(profile)
        DecayedFrequencyPredictor(database.behaviorDao()).update(
            input,
            AppActionId.VIEW_SCHEDULE.stableId
        )
        KotlinOnDeviceTrainer(database.behaviorDao(), modelStateStore).enqueue(
            OrganicTrainingSample(
                input = input,
                targetOutputId = AppActionId.VIEW_SCHEDULE.stableId,
                actionFamily = ActionFamily.ACADEMIC,
                labelSource = "ORGANIC_ACTION"
            )
        )
    }

    private fun input(profile: String): PredictionInput = FeatureExtractor.build(
        profileKey = profile,
        decisionId = UUID.randomUUID().toString(),
        snapshot = ContextSnapshot(
            epochDay = LocalDate.now().toEpochDay(),
            minuteOfDay = 22 * 60,
            dayType = DayType.WEEKDAY,
            route = "home",
            previousAction = AppActionId.OPEN_HOME,
            recentActions = listOf(AppActionId.OPEN_HOME),
            balanceBucket = BalanceBucket.UNKNOWN,
            balanceFresh = false,
            examDistanceBucket = ExamDistanceBucket.UNKNOWN,
            sessionDurationBucket = 0,
            recentActionSources = listOf(ActionSource.ORGANIC)
        )
    )

    private fun profile(): String = UUID.randomUUID().toString().replace("-", "").also(profiles::add)
}

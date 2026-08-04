package com.ahu.ahutong.personalization

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.personalization.storage.BehaviorDatabase
import com.ahu.ahutong.personalization.storage.BehaviorDatabaseMigrations
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BehaviorDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BehaviorDatabase::class.java
    )

    @Test
    fun migrateOneToThreePreservesOldRowsAndCreatesTargetedAndTelemetryV3Tables() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO learning_state " +
                    "(profileKey, statLearningStartedEpochDay, tinyTrainingStartedEpochDay, " +
                    "lastCommittedBatchId, lastTrainingNanos, lastTrainingLoss, lastGradientNorm) " +
                    "VALUES (?, 2, 3, 'batch', 4, 0.5, 0.25)",
                arrayOf(PROFILE)
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.databaseBuilder(context, BehaviorDatabase::class.java, TEST_DB)
            .addMigrations(
                BehaviorDatabaseMigrations.MIGRATION_1_2,
                BehaviorDatabaseMigrations.MIGRATION_2_3
            )
            .build()
        try {
            val sqlite = database.openHelper.writableDatabase
                sqlite.query(
                    "SELECT statLearningStartedEpochDay, tinyTrainingStartedEpochDay " +
                        "FROM learning_state WHERE profileKey = ?",
                    arrayOf(PROFILE)
                ).use {
                    assertEquals(true, it.moveToFirst())
                    assertEquals(2, it.getInt(0))
                    assertEquals(3, it.getInt(1))
                }
                TARGETED_TABLES.forEach { table ->
                    sqlite.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                        assertEquals("missing table $table", true, cursor.moveToFirst())
                    }
                }
                sqlite.query("PRAGMA table_info(`pending_prediction`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    val names = buildSet {
                        while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                    }
                    assertEquals(true, "effectiveProbabilities" in names)
                }
        } finally {
            database.close()
        }
    }

    @Test
    fun migrateTwoToThreePreservesExistingLearningState() {
        helper.createDatabase(TEST_DB_V2, 2).apply {
            execSQL(
                "INSERT INTO learning_state " +
                    "(profileKey, statLearningStartedEpochDay, tinyTrainingStartedEpochDay, " +
                    "lastCommittedBatchId, lastTrainingNanos, lastTrainingLoss, lastGradientNorm) " +
                    "VALUES (?, 5, 6, 'v2-batch', 7, 0.4, 0.2)",
                arrayOf(PROFILE)
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.databaseBuilder(context, BehaviorDatabase::class.java, TEST_DB_V2)
            .addMigrations(BehaviorDatabaseMigrations.MIGRATION_2_3)
            .build()
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(3, sqlite.version)
            sqlite.query(
                "SELECT statLearningStartedEpochDay, tinyTrainingStartedEpochDay " +
                    "FROM learning_state WHERE profileKey = ?",
                arrayOf(PROFILE)
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(5, cursor.getInt(0))
                assertEquals(6, cursor.getInt(1))
            }
            sqlite.query("SELECT COUNT(*) FROM telemetry_v3_aggregate_window").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DB = "behavior-migration-test"
        const val TEST_DB_V2 = "behavior-migration-v2-test"
        const val PROFILE = "0123456789abcdef0123456789abcdef"
        val TARGETED_TABLES = listOf(
            "semantic_event",
            "semantic_change_set",
            "pending_journey",
            "journey_action_stat",
            "journey_training_sample",
            "journey_shadow_evaluation",
            "local_parameter_preset",
            "preset_usage_stat",
            "targeted_prediction_feedback",
            "preset_recommendation_interaction",
            "task_model_state",
            "task_training_batch_journal",
            "preset_training_sample",
            "preset_shadow_evaluation",
            "telemetry_v3_aggregate_window"
        )
    }
}

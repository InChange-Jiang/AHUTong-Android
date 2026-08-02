package com.ahu.ahutong.personalization

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.personalization.storage.BehaviorDatabase
import com.ahu.ahutong.personalization.storage.BehaviorDatabaseFactory
import com.ahu.ahutong.personalization.storage.BehaviorDatabaseFiles
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BehaviorDatabaseRollbackCompatibilityTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BehaviorDatabase::class.java
    )

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun cleanUp() {
        context.deleteDatabase(BehaviorDatabaseFiles.LEGACY_V1)
        context.deleteDatabase(BehaviorDatabaseFiles.CURRENT_V2)
    }

    @Test
    fun versionOneIsCopiedAndLeftReadableForAnOldBinary() {
        cleanUp()
        helper.createDatabase(BehaviorDatabaseFiles.LEGACY_V1, 1).apply {
            execSQL(
                "INSERT INTO learning_state " +
                    "(profileKey, statLearningStartedEpochDay, tinyTrainingStartedEpochDay, " +
                    "lastCommittedBatchId, lastTrainingNanos, lastTrainingLoss, lastGradientNorm) " +
                    "VALUES ('rollback-profile', 2, 3, 'batch', 4, 0.5, 0.25)"
            )
            close()
        }

        val database = BehaviorDatabaseFactory.open(context)
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(2, sqlite.version)
            sqlite.query(
                "SELECT COUNT(*) FROM learning_state WHERE profileKey = 'rollback-profile'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            database.close()
        }

        val legacy = SQLiteDatabase.openDatabase(
            context.getDatabasePath(BehaviorDatabaseFiles.LEGACY_V1).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        try {
            assertEquals(1, legacy.version)
        } finally {
            legacy.close()
        }
    }

    @Test
    fun unknownLegacyVersionIsPreservedAndPredictionStorageRecoversFresh() {
        cleanUp()
        SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(BehaviorDatabaseFiles.LEGACY_V1),
            null
        ).use { database ->
            database.version = 99
        }

        val database = BehaviorDatabaseFactory.open(context)
        try {
            assertEquals(2, database.openHelper.writableDatabase.version)
        } finally {
            database.close()
        }

        val legacy = SQLiteDatabase.openDatabase(
            context.getDatabasePath(BehaviorDatabaseFiles.LEGACY_V1).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        try {
            assertEquals(99, legacy.version)
        } finally {
            legacy.close()
        }
        assertTrue(context.getDatabasePath(BehaviorDatabaseFiles.CURRENT_V2).exists())
    }

    @Test
    fun futureCurrentVersionFallsBackToFreshVersionTwoStorage() {
        cleanUp()
        SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(BehaviorDatabaseFiles.CURRENT_V2),
            null
        ).use { database ->
            database.execSQL("CREATE TABLE future_only (id INTEGER PRIMARY KEY)")
            database.version = 99
        }

        val database = BehaviorDatabaseFactory.open(context)
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(2, sqlite.version)
            sqlite.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'behavior_event'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            database.close()
        }
    }
}

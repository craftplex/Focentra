package com.niloy.focentra

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PermissionInfo
import androidx.test.core.app.ApplicationProvider
import com.niloy.focentra.data.local.AppDatabase
import com.niloy.focentra.data.local.entity.SettingEntity
import com.niloy.focentra.data.local.entity.StudySessionEntity
import com.niloy.focentra.data.provider.DaynexaContract
import com.niloy.focentra.data.provider.DaynexaSessionsProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DaynexaSessionsProviderTest {

    private lateinit var context: Context
    private lateinit var provider: DaynexaSessionsProvider
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)
        
        provider = Robolectric.setupContentProvider(DaynexaSessionsProvider::class.java, DaynexaContract.AUTHORITY)
    }

    @Test
    fun testGetType_returnsCorrectMimeTypes() {
        val dirMime = provider.getType(DaynexaContract.SESSIONS_URI)
        assertEquals(DaynexaContract.CONTENT_TYPE_DIR, dirMime)

        val itemUri = ContentUris.withAppendedId(DaynexaContract.SESSIONS_URI, 42L)
        val itemMime = provider.getType(itemUri)
        assertEquals(DaynexaContract.CONTENT_TYPE_ITEM, itemMime)
    }

    @Test
    fun testReadOnlyProtection_mutationsThrowException() {
        try {
            provider.insert(DaynexaContract.SESSIONS_URI, ContentValues())
            fail("insert should have thrown UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("READ-ONLY") == true)
        }

        try {
            provider.update(DaynexaContract.SESSIONS_URI, ContentValues(), null, null)
            fail("update should have thrown UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("READ-ONLY") == true)
        }

        try {
            provider.delete(DaynexaContract.SESSIONS_URI, null, null)
            fail("delete should have thrown UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("READ-ONLY") == true)
        }
    }

    @Test
    fun sessionReadPermissionRequiresTheFocentraSigningCertificate() {
        val permission = context.packageManager.getPermissionInfo(
            DaynexaContract.PERMISSION_READ_SESSIONS,
            0
        )

        assertEquals(
            PermissionInfo.PROTECTION_SIGNATURE,
            permission.protection
        )
    }

    @Test
    fun testQuery_whenConsentDisabled_returnsEmptyCursor() {
        runBlocking {
            // Ensure consent is disabled
            database.settingDao().setSetting(
                SettingEntity(key = DaynexaContract.SETTING_DAYNEXA_CONSENT, value = "false")
            )

            // Insert a sample session
            database.sessionDao().insertSession(
                StudySessionEntity(
                    startTime = System.currentTimeMillis() - 3600000,
                    endTime = System.currentTimeMillis(),
                    durationSeconds = 3600,
                    actualFocusedSeconds = 3600,
                    pausedSeconds = 0,
                    sessionType = "COUNTDOWN",
                    subject = "Mathematics",
                    topic = "Calculus",
                    category = "STEM",
                    distractionCount = 0,
                    productivityRating = 5,
                    completionStatus = "COMPLETED"
                )
            )

            val cursor = provider.query(
                DaynexaContract.SESSIONS_URI,
                null,
                null,
                null,
                null
            )

            assertNotNull(cursor)
            assertEquals(0, cursor?.count)
            assertEquals(DaynexaContract.DEFAULT_PROJECTION.size, cursor?.columnNames?.size)
            cursor?.close()
        }
    }

    @Test
    fun testQuery_whenConsentEnabled_returnsExposedSessions() {
        runBlocking {
            // Enable user consent
            database.settingDao().setSetting(
                SettingEntity(key = DaynexaContract.SETTING_DAYNEXA_CONSENT, value = "true")
            )

            val startTime = 1700000000000L
            val endTime = 1700003600000L

            val insertedId = database.sessionDao().insertSession(
                StudySessionEntity(
                    startTime = startTime,
                    endTime = endTime,
                    durationSeconds = 3600,
                    actualFocusedSeconds = 3400,
                    pausedSeconds = 200,
                    sessionType = "COUNTDOWN",
                    subject = "Computer Science",
                    topic = "Algorithms",
                    category = "Engineering",
                    distractionCount = 1,
                    productivityRating = 5,
                    completionStatus = "COMPLETED"
                )
            )

            val cursor = provider.query(
                DaynexaContract.SESSIONS_URI,
                null,
                null,
                null,
                null
            )

            assertNotNull(cursor)
            assertTrue(cursor!!.count >= 1)
            
            var found = false
            while (cursor.moveToNext()) {
                val sessionId = cursor.getLong(cursor.getColumnIndexOrThrow(DaynexaContract.COLUMN_SESSION_ID))
                if (sessionId == insertedId) {
                    found = true
                    val subject = cursor.getString(cursor.getColumnIndexOrThrow(DaynexaContract.COLUMN_SUBJECT))
                    val topic = cursor.getString(cursor.getColumnIndexOrThrow(DaynexaContract.COLUMN_TOPIC))
                    val start = cursor.getLong(cursor.getColumnIndexOrThrow(DaynexaContract.COLUMN_START_TIME))
                    val end = cursor.getLong(cursor.getColumnIndexOrThrow(DaynexaContract.COLUMN_END_TIME))
                    val durationMin = cursor.getLong(cursor.getColumnIndexOrThrow(DaynexaContract.COLUMN_DURATION))
                    val status = cursor.getString(cursor.getColumnIndexOrThrow(DaynexaContract.COLUMN_COMPLETION_STATUS))
                    val focusScore = cursor.getInt(cursor.getColumnIndexOrThrow(DaynexaContract.COLUMN_FOCUS_SCORE))
                    val schemaVersion = cursor.getInt(cursor.getColumnIndexOrThrow(DaynexaContract.COLUMN_SCHEMA_VERSION))

                    assertEquals("Computer Science", subject)
                    assertEquals("Algorithms", topic)
                    assertEquals(startTime, start)
                    assertEquals(endTime, end)
                    assertEquals(56L, durationMin) // 3400s / 60 = 56m
                    assertEquals("COMPLETED", status)
                    assertTrue("Focus score should be between 0 and 100", focusScore in 0..100)
                    assertEquals(1, schemaVersion)
                }
            }
            assertTrue("Inserted session must be returned in query", found)
            cursor.close()
        }
    }

    @Test
    fun testQuery_singleSessionById() {
        runBlocking {
            // Enable user consent
            database.settingDao().setSetting(
                SettingEntity(key = DaynexaContract.SETTING_DAYNEXA_CONSENT, value = "true")
            )

            val insertedId = database.sessionDao().insertSession(
                StudySessionEntity(
                    startTime = 1700000000000L,
                    endTime = 1700001800000L,
                    durationSeconds = 1800,
                    actualFocusedSeconds = 1800,
                    pausedSeconds = 0,
                    sessionType = "STOPWATCH",
                    subject = "Physics",
                    topic = "Quantum Mechanics",
                    category = "Science",
                    distractionCount = 0,
                    productivityRating = 4,
                    completionStatus = "COMPLETED"
                )
            )

            val singleUri = ContentUris.withAppendedId(DaynexaContract.SESSIONS_URI, insertedId)
            val cursor = provider.query(singleUri, null, null, null, null)

            assertNotNull(cursor)
            assertEquals(1, cursor?.count)
            cursor?.moveToFirst()
            val subject = cursor?.getString(cursor.getColumnIndexOrThrow(DaynexaContract.COLUMN_SUBJECT))
            assertEquals("Physics", subject)
            cursor?.close()
        }
    }

    @Test
    fun testQuery_projectionFiltering() {
        runBlocking {
            database.settingDao().setSetting(
                SettingEntity(key = DaynexaContract.SETTING_DAYNEXA_CONSENT, value = "true")
            )

            val customProjection = arrayOf(
                DaynexaContract.COLUMN_SESSION_ID,
                DaynexaContract.COLUMN_SUBJECT,
                DaynexaContract.COLUMN_FOCUS_SCORE
            )

            val cursor = provider.query(
                DaynexaContract.SESSIONS_URI,
                customProjection,
                null,
                null,
                null
            )

            assertNotNull(cursor)
            assertEquals(customProjection.size, cursor?.columnNames?.size)
            assertEquals(DaynexaContract.COLUMN_SESSION_ID, cursor?.columnNames?.get(0))
            assertEquals(DaynexaContract.COLUMN_SUBJECT, cursor?.columnNames?.get(1))
            assertEquals(DaynexaContract.COLUMN_FOCUS_SCORE, cursor?.columnNames?.get(2))
            cursor?.close()
        }
    }
}

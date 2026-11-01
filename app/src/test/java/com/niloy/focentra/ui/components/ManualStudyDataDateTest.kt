package com.niloy.focentra.ui.components

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualStudyDataDateTest {

    @Test
    fun selectedDateKeepsLocalTimeOfDay() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val originalTimestamp = Calendar.getInstance().apply {
                clear()
                set(2026, Calendar.JANUARY, 31, 16, 43, 27)
                set(Calendar.MILLISECOND, 512)
            }.timeInMillis
            val datePickerDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = manualDatePickerMillisForTimestamp(originalTimestamp)
            }
            assertEquals(2026, datePickerDate.get(Calendar.YEAR))
            assertEquals(Calendar.JANUARY, datePickerDate.get(Calendar.MONTH))
            assertEquals(31, datePickerDate.get(Calendar.DAY_OF_MONTH))

            val selectedDateMillis = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(2026, Calendar.FEBRUARY, 28, 0, 0, 0)
            }.timeInMillis

            val adjustedTimestamp = manualSessionTimestampForDate(
                selectedDateMillis = selectedDateMillis,
                timestampWithTime = originalTimestamp
            )
            val result = Calendar.getInstance().apply { timeInMillis = adjustedTimestamp }

            assertEquals(2026, result.get(Calendar.YEAR))
            assertEquals(Calendar.FEBRUARY, result.get(Calendar.MONTH))
            assertEquals(28, result.get(Calendar.DAY_OF_MONTH))
            assertEquals(16, result.get(Calendar.HOUR_OF_DAY))
            assertEquals(43, result.get(Calendar.MINUTE))
            assertEquals(27, result.get(Calendar.SECOND))
            assertEquals(512, result.get(Calendar.MILLISECOND))
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }
}

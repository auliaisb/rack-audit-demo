package com.rackaudit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SubmissionValidatorTest {
    @Test fun validSubmissionHasNoErrors() {
        assertEquals(emptyList<SubmissionError>(), SubmissionValidator.validate("Rak penuh", 2))
    }

    @Test fun whitespaceOnlyNoteCountsAsEmpty() {
        assertEquals(listOf(SubmissionError.NOTE_REQUIRED), SubmissionValidator.validate("   \n ", 1))
    }

    @Test fun noteLengthBoundary() {
        assertEquals(emptyList<SubmissionError>(), SubmissionValidator.validate("x".repeat(Limits.NOTE_MAX_LENGTH), 1))
        assertEquals(listOf(SubmissionError.NOTE_TOO_LONG), SubmissionValidator.validate("x".repeat(Limits.NOTE_MAX_LENGTH + 1), 1))
    }

    @Test fun photoCountBoundaries() {
        assertEquals(listOf(SubmissionError.TOO_FEW_PHOTOS), SubmissionValidator.validate("ok", 0))
        assertEquals(emptyList<SubmissionError>(), SubmissionValidator.validate("ok", Limits.MIN_PHOTOS))
        assertEquals(emptyList<SubmissionError>(), SubmissionValidator.validate("ok", Limits.MAX_PHOTOS))
        assertEquals(listOf(SubmissionError.TOO_MANY_PHOTOS), SubmissionValidator.validate("ok", Limits.MAX_PHOTOS + 1))
    }

    @Test fun allProblemsAreReportedTogether() {
        assertEquals(
            listOf(SubmissionError.NOTE_REQUIRED, SubmissionError.TOO_FEW_PHOTOS),
            SubmissionValidator.validate("", 0),
        )
    }
}

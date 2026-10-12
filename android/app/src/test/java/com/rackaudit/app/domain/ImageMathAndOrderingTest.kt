package com.rackaudit.app.domain

import com.rackaudit.app.data.Assignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ImageMathAndOrderingTest {
    // ---- ImageMath ----
    @Test fun smallImagesAreNotDownsampledOrUpscaled() {
        assertEquals(1, ImageMath.inSampleSize(1200, 800, 1600))
        assertEquals(1200 to 800, ImageMath.scaledSize(1200, 800, 1600))
    }

    @Test fun typicalPhoneCameraPhotoIsHalvedTwice() {
        // 4000x3000: 4000/2=2000>=1600 -> 2; 2000/2=1000<1600 stop.
        assertEquals(2, ImageMath.inSampleSize(4000, 3000, 1600))
        assertEquals(1600 to 1200, ImageMath.scaledSize(4000, 3000, 1600))
    }

    @Test fun portraitUsesTheLongEdge() {
        assertEquals(1200 to 1600, ImageMath.scaledSize(3000, 4000, 1600))
    }

    @Test fun invalidDimensionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ImageMath.inSampleSize(0, 100, 1600) }
        assertThrows(IllegalArgumentException::class.java) { ImageMath.scaledSize(100, 100, 0) }
    }

    // ---- AssignmentOrdering ----
    private fun a(id: String, status: AssignmentStatus?, due: Long?) = Assignment(
        id, id, "", "Toko", "", due, status, 0, "", "",
    )

    @Test fun actionableFirstBySoonestDue_thenReview_thenFinishedNewestFirst() {
        val sorted = AssignmentOrdering.sort(
            listOf(
                a("done-old", AssignmentStatus.APPROVED, 100),
                a("review", AssignmentStatus.SUBMITTED, 50),
                a("todo-late", AssignmentStatus.ASSIGNED, 300),
                a("redo-soon", AssignmentStatus.REJECTED, 200),
                a("done-new", AssignmentStatus.CANCELLED, 400),
                a("unknown", null, 10),
            ),
        )
        assertEquals(
            listOf("redo-soon", "todo-late", "review", "done-new", "done-old", "unknown"),
            sorted.map { it.id },
        )
    }

    @Test fun missingDueDateSortsLastAmongActionable() {
        val sorted = AssignmentOrdering.sort(
            listOf(a("none", AssignmentStatus.ASSIGNED, null), a("dated", AssignmentStatus.ASSIGNED, 5)),
        )
        assertEquals(listOf("dated", "none"), sorted.map { it.id })
    }
}

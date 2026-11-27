package com.lumeryx.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferJobTest {
    private val source = MediaLocation("local", "content://example.test/source")
    private val destination = MediaLocation("local", "content://example.test/destination")

    @Test
    fun newJobStartsQueuedWithNoTransferredBytes() {
        val job = TransferJob("copy-1", source, destination)

        assertEquals(TransferStatus.QUEUED, job.status)
        assertEquals(0L, job.transferredBytes)
        assertNull(job.totalBytes)
    }

    @Test
    fun progressMayAdvanceWhenTheTotalSizeIsUnknown() {
        val job = TransferJob(
            "copy-1", source, destination,
            status = TransferStatus.RUNNING,
            transferredBytes = 12,
        )

        assertEquals(12L, job.transferredBytes)
        assertNull(job.totalBytes)
    }

    @Test
    fun progressCannotExceedTheKnownTotal() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferJob("copy-1", source, destination, transferredBytes = 12, totalBytes = 10)
        }
    }
}

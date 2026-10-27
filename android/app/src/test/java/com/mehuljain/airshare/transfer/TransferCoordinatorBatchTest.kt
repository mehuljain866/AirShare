package com.mehuljain.airshare.transfer

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransferCoordinatorBatchTest {

    @Before
    @After
    fun cleanUp() {
        TransferCoordinator.reset()
    }

    @Test
    fun batchAutoApprovalAcceptsSubsequentPhotos() {
        val thread = Thread {
            Thread.sleep(50)
            TransferCoordinator.accept()
        }
        thread.start()

        // First photo prompts user
        val firstApproved = TransferCoordinator.awaitApproval(
            jobId = 1,
            senderName = "mobile",
            jobName = "Photo 1",
            format = "application/pdf",
            totalBytes = 1000,
            userAgent = "AirPrint/1.0 (iPad; CPU OS 10_3_3 like Mac OS X)",
        )
        thread.join()
        assertTrue("First photo should be approved", firstApproved)

        // Second photo from same iPad arrives in batch: should be auto-approved immediately without waiting
        val secondApproved = TransferCoordinator.awaitApproval(
            jobId = 2,
            senderName = "mobile",
            jobName = "Photo 2",
            format = "application/pdf",
            totalBytes = 1200,
            userAgent = "AirPrint/1.0 (iPad; CPU OS 10_3_3 like Mac OS X)",
        )
        assertTrue("Second photo in batch should be auto-approved", secondApproved)

        // Third photo from same iPad: also auto-approved
        val thirdApproved = TransferCoordinator.awaitApproval(
            jobId = 3,
            senderName = "mobile",
            jobName = "Photo 3",
            format = "application/pdf",
            totalBytes = 1100,
            userAgent = "AirPrint/1.0 (iPad; CPU OS 10_3_3 like Mac OS X)",
        )
        assertTrue("Third photo in batch should be auto-approved", thirdApproved)
    }

    @Test
    fun batchDeclinedRejectsSubsequentPhotos() {
        val thread = Thread {
            Thread.sleep(50)
            TransferCoordinator.decline()
        }
        thread.start()

        // First photo is declined by user
        val firstApproved = TransferCoordinator.awaitApproval(
            jobId = 1,
            senderName = "mobile",
            jobName = "Photo 1",
            format = "application/pdf",
            totalBytes = 1000,
            userAgent = "AirPrint/1.0 (iPad; CPU OS 10_3_3 like Mac OS X)",
        )
        thread.join()
        assertFalse("First photo should be declined", firstApproved)

        // Second photo from same iPad in the rejected batch should be silently declined
        val secondApproved = TransferCoordinator.awaitApproval(
            jobId = 2,
            senderName = "mobile",
            jobName = "Photo 2",
            format = "application/pdf",
            totalBytes = 1200,
            userAgent = "AirPrint/1.0 (iPad; CPU OS 10_3_3 like Mac OS X)",
        )
        assertFalse("Second photo in declined batch should be automatically declined", secondApproved)
    }
}

package com.valhalla.bolt.model

import org.junit.Assert.*
import org.junit.Test

class FlashJourneyTest {
    private fun installed(boot: String? = "boot-one") = FlashJourney("job", "BakaSU.zip", "device", boot, 10, completedAt = 20)

    @Test fun sameBootDoesNotCountAsReboot() {
        val job = installed()
        assertEquals(job, job.observeBoot("device", "boot-one", 30))
        assertEquals(BootStatus.REBOOT_PENDING, job.bootStatus)
    }
    @Test fun changedBootOnSameDeviceReconcilesCompletedJob() {
        val job = installed().observeBoot("device", "boot-two", 30)
        assertEquals(BootStatus.REBOOT_OBSERVED, job.bootStatus)
        assertEquals(30L, job.bootObservedAt)
        assertNull(job.feedback)
        assertEquals(job, job.observeBoot("device", "boot-three", 40))
    }
    @Test fun missingEvidenceDifferentDeviceAndInterruptedInstallerStayUnobserved() {
        assertEquals(BootStatus.REBOOT_PENDING, installed(null).observeBoot("device", "boot-two", 30).bootStatus)
        assertEquals(installed(), installed().observeBoot("another-device", "boot-two", 30))
        assertEquals(installed(), installed().observeBoot("device", null, 30))
        val interrupted = installed().copy(completedAt = null)
        assertEquals(interrupted, interrupted.observeBoot("device", "boot-two", 30))
        assertEquals(installed(), installed().observeBoot("device", "boot-two", 15))
    }
    @Test fun userFeedbackRemainsSeparateFromBootObservation() {
        val legacy = installed(null).copy(feedback = "working")
        assertEquals(BootStatus.USER_CONFIRMED, legacy.bootStatus)
        assertNull(legacy.bootObservedAt)
        assertEquals(BootStatus.ISSUE_REPORTED, installed().copy(feedback = "issue").bootStatus)
    }
}

package com.mehuljain.airshare.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInfoParserTest {

    @Test
    fun parseIPhoneSinglePhoto() {
        val info = DeviceInfoParser.parse(
            userAgent = "MobileAirPrint/1.0 (iPhone; CPU OS 18_1 like Mac OS X)",
            userName = "mobile",
            jobName = "IMG_4821.JPG",
            format = "image/urf",
        )
        assertEquals("iPhone", info.deviceDisplayName)
        assertEquals("IMG_4821.JPG", info.contentDisplayName)
        assertTrue(info.isPhoto)
        assertEquals("Gallery", info.destinationStorage)
    }

    @Test
    fun parseIPhoneMultiplePhotos() {
        val info = DeviceInfoParser.parse(
            userAgent = "MobileAirPrint/1.0 (iPhone; CPU OS 18_1 like Mac OS X)",
            userName = "Mehul",
            jobName = "3 Photos",
            format = "image/urf",
        )
        assertEquals("Mehul's iPhone", info.deviceDisplayName)
        assertEquals("3 photos", info.contentDisplayName)
        assertTrue(info.isPhoto)
        assertEquals("Gallery", info.destinationStorage)
    }

    @Test
    fun parseMacPdfDocument() {
        val info = DeviceInfoParser.parse(
            userAgent = "AirPrint/1.0 (Macintosh; Intel Mac OS X 14_5)",
            userName = "danny",
            jobName = "Quarterly_Report.pdf",
            format = "application/pdf",
        )
        assertEquals("danny's Mac", info.deviceDisplayName)
        assertEquals("Quarterly_Report.pdf", info.contentDisplayName)
        assertFalse(info.isPhoto)
        assertEquals("Documents", info.destinationStorage)
    }

    @Test
    fun parseWindowsPC() {
        val info = DeviceInfoParser.parse(
            userAgent = "Microsoft-Windows/11.0",
            userName = "mobile",
            jobName = "Untitled",
            format = "image/jpeg",
        )
        assertEquals("Windows PC", info.deviceDisplayName)
        assertEquals("a photo", info.contentDisplayName)
        assertTrue(info.isPhoto)
    }

    @Test
    fun parseGenericFallback() {
        val info = DeviceInfoParser.parse(
            userAgent = null,
            userName = "anonymous",
            jobName = "print job",
            format = "image/jpeg",
        )
        assertEquals("Nearby device", info.deviceDisplayName)
        assertEquals("a photo", info.contentDisplayName)
    }

    @Test
    fun parseIPadScreenshot() {
        val info = DeviceInfoParser.parse(
            userAgent = "AirPrint/1.0 (iPad; CPU OS 10_3_3 like Mac OS X)",
            userName = "mobile",
            jobName = "Screenshot",
            format = "application/pdf",
        )
        assertEquals("iPad", info.deviceDisplayName)
        assertEquals("a screenshot", info.contentDisplayName)
        assertTrue(info.isPhoto)
        assertEquals("Gallery", info.destinationStorage)
    }

    @Test
    fun parseIPadNamedScreenshot() {
        val info = DeviceInfoParser.parse(
            userAgent = "AirPrint/1.0 (iPad; CPU OS 10_3_3 like Mac OS X)",
            userName = "Mehul",
            jobName = "Screenshot 2026-10-26 at 18.00.00",
            format = "application/pdf",
        )
        assertEquals("Mehul's iPad", info.deviceDisplayName)
        assertEquals("a screenshot", info.contentDisplayName)
        assertTrue(info.isPhoto)
        assertEquals("Gallery", info.destinationStorage)
    }

    @Test
    fun parseIPadPhoto() {
        val info = DeviceInfoParser.parse(
            userAgent = "AirPrint/1.0 (iPad; CPU OS 10_3_3 like Mac OS X)",
            userName = "Mehul",
            jobName = "Photo",
            format = "application/pdf",
        )
        assertEquals("Mehul's iPad", info.deviceDisplayName)
        assertEquals("a photo", info.contentDisplayName)
        assertTrue(info.isPhoto)
        assertEquals("Gallery", info.destinationStorage)
    }
}

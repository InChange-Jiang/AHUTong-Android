package com.ahu.ahutong.ui.screen.main.home

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentQrNfcGuardTest {

    @Test
    fun `guard requires the home QR resumed lifecycle and enabled NFC`() {
        assertTrue(shouldEnablePaymentQrNfcGuard(true, true, true, true))

        listOf(
            booleanArrayOf(false, true, true, true),
            booleanArrayOf(true, false, true, true),
            booleanArrayOf(true, true, false, true),
            booleanArrayOf(true, true, true, false)
        ).forEach { conditions ->
            assertFalse(
                shouldEnablePaymentQrNfcGuard(
                    isHomeActive = conditions[0],
                    isPaymentQrVisible = conditions[1],
                    isActivityResumed = conditions[2],
                    isNfcEnabled = conditions[3]
                )
            )
        }
    }

    @Test
    fun `reader mode covers every tag technology and suppresses platform handling`() {
        assertTrue(PAYMENT_QR_NFC_READER_FLAGS and 0x01 != 0) // NFC-A
        assertTrue(PAYMENT_QR_NFC_READER_FLAGS and 0x02 != 0) // NFC-B
        assertTrue(PAYMENT_QR_NFC_READER_FLAGS and 0x04 != 0) // NFC-F
        assertTrue(PAYMENT_QR_NFC_READER_FLAGS and 0x08 != 0) // NFC-V
        assertTrue(PAYMENT_QR_NFC_READER_FLAGS and 0x10 != 0) // NFC Barcode
        assertTrue(PAYMENT_QR_NFC_READER_FLAGS and 0x80 != 0) // Skip NDEF dispatch
        assertTrue(PAYMENT_QR_NFC_READER_FLAGS and 0x100 != 0) // No platform sounds
    }

    @Test
    fun `manifest keeps NFC hardware optional`() {
        val manifest = File(repositoryRoot(), "app/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.NFC"))
        assertTrue(
            Regex(
                """<uses-feature\s+android:name="android\.hardware\.nfc"\s+android:required="false"\s*/>"""
            ).containsMatchIn(manifest)
        )
    }

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main").isDirectory }
    }
}

package com.cellclaw

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElixirManifestTest {

    private fun loadManifest(): String {
        val manifestFile = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).firstOrNull { it.exists() } ?: error("AndroidManifest.xml not found")
        return manifestFile.readText()
    }

    @Test
    fun `manifest excludes SMS, call, contact, camera, location and audio permissions`() {
        val manifest = loadManifest().replace(Regex("<!--[\\s\\S]*?-->"), "")

        val forbiddenPermissions = listOf(
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.SEND_SMS",
            "android.permission.BROADCAST_SMS",
            "android.permission.READ_PHONE_STATE",
            "android.permission.CALL_PHONE",
            "android.permission.READ_CALL_LOG",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.CAMERA",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.RECORD_AUDIO"
        )

        for (perm in forbiddenPermissions) {
            assertFalse(
                "Manifest must not declare permission $perm",
                manifest.contains("<uses-permission android:name=\"$perm\"")
            )
        }
    }

    @Test
    fun `manifest does not register SmsReceiver or PhoneStateReceiver`() {
        val manifestWithoutComments = loadManifest().replace(Regex("<!--[\\s\\S]*?-->"), "")

        assertFalse("Manifest must not register SmsReceiver", manifestWithoutComments.contains("SmsReceiver"))
        assertFalse("Manifest must not register PhoneStateReceiver", manifestWithoutComments.contains("PhoneStateReceiver"))
    }

    @Test
    fun `manifest sets allowBackup to false to prevent ADB extraction`() {
        val manifest = loadManifest()
        assertTrue("allowBackup must be false (A2)", manifest.contains("android:allowBackup=\"false\""))
        assertFalse("allowBackup must not be true", manifest.contains("android:allowBackup=\"true\""))
    }

    @Test
    fun `phone state receiver does not log phone numbers`() {
        val receiverFile = sequenceOf(
            File("src/main/kotlin/com/cellclaw/service/receivers/PhoneStateReceiver.kt"),
            File("app/src/main/kotlin/com/cellclaw/service/receivers/PhoneStateReceiver.kt")
        ).firstOrNull { it.exists() } ?: error("PhoneStateReceiver.kt not found")

        val content = receiverFile.readText()
        assertFalse("PhoneStateReceiver must not log phone number", content.contains("number: \$number"))
        assertFalse("PhoneStateReceiver must not read EXTRA_INCOMING_NUMBER", content.contains("EXTRA_INCOMING_NUMBER)"))
    }

    @Test
    fun `manifest excludes QUERY_ALL_PACKAGES and declares scoped browser queries`() {
        val manifestWithoutComments = loadManifest().replace(Regex("<!--[\\s\\S]*?-->"), "")
        assertFalse(
            "Manifest must not declare QUERY_ALL_PACKAGES (R2)",
            manifestWithoutComments.contains("android.permission.QUERY_ALL_PACKAGES")
        )
        assertTrue(
            "Manifest must declare scoped browser queries (R2)",
            manifestWithoutComments.contains("<package android:name=\"com.android.chrome\" />")
        )
    }

    @Test
    fun `manifest does not register WakeWordService`() {
        val manifestWithoutComments = loadManifest().replace(Regex("<!--[\\s\\S]*?-->"), "")
        assertFalse(
            "Manifest must not register WakeWordService (R2)",
            manifestWithoutComments.contains("WakeWordService")
        )
    }
}

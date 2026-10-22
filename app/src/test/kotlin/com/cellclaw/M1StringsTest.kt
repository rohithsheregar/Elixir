package com.cellclaw

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M1StringsTest {

    @Test
    fun `display and notification channel labels use Elixir`() {
        val strings = File("src/main/res/values/strings.xml").readText()

        assertTrue(strings.contains("<string name=\"app_name\">Elixir</string>"))
        assertTrue(strings.contains("<string name=\"notification_channel_service\">Elixir Service</string>"))
        assertFalse(strings.contains("notification_channel_service\">CellClaw"))
    }
}

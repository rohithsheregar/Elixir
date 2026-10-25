package com.cellclaw.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SplashThemeTest {

    @Test
    fun testElixirLauncherForegroundVectorExistsAndValid() {
        val file = File("src/main/res/drawable/ic_elixir_launcher_foreground.xml")
        assertTrue("ic_elixir_launcher_foreground.xml should exist", file.exists())
        val content = file.readText()
        assertTrue("Must be vector XML", content.contains("<vector"))
        assertTrue("Must have 108dp width", content.contains("android:width=\"108dp\""))
        assertTrue("Must have 108dp height", content.contains("android:height=\"108dp\""))
        assertTrue("Must specify viewportWidth 108", content.contains("android:viewportWidth=\"108\""))
        assertTrue("Must specify viewportHeight 108", content.contains("android:viewportHeight=\"108\""))
    }

    @Test
    fun testAdaptiveLauncherIconConfigured() {
        val file = File("src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        assertTrue("mipmap-anydpi-v26/ic_launcher.xml should exist", file.exists())
        val content = file.readText()
        assertTrue("Must declare adaptive-icon", content.contains("<adaptive-icon"))
        assertTrue(
            "Must use ic_launcher_background",
            content.contains("@color/ic_launcher_background")
        )
        assertTrue(
            "Must use ic_elixir_launcher_foreground",
            content.contains("@drawable/ic_elixir_launcher_foreground")
        )
    }

    @Test
    fun testColorsXmlContainsElixirPalette() {
        val file = File("src/main/res/values/colors.xml")
        assertTrue("colors.xml must exist", file.exists())
        val content = file.readText()
        assertTrue("Must define elixir_splash_background", content.contains("name=\"elixir_splash_background\""))
        assertTrue("Must define ic_launcher_background", content.contains("name=\"ic_launcher_background\""))
        assertTrue("Must define elixir_primary", content.contains("name=\"elixir_primary\""))
    }

    @Test
    fun testThemesXmlDefinesSplashScreenTheme() {
        val file = File("src/main/res/values/themes.xml")
        assertTrue("themes.xml must exist", file.exists())
        val content = file.readText()
        assertTrue("Must declare Theme.Elixir", content.contains("name=\"Theme.Elixir\""))
        assertTrue("Must declare Theme.CellClaw alias", content.contains("name=\"Theme.CellClaw\""))
        assertTrue("Must declare Theme.Elixir.Starting with Theme.SplashScreen parent",
            content.contains("name=\"Theme.Elixir.Starting\" parent=\"Theme.SplashScreen\""))
        assertTrue("Must set windowSplashScreenBackground",
            content.contains("name=\"windowSplashScreenBackground\">@color/elixir_splash_background"))
        assertTrue("Must set windowSplashScreenAnimatedIcon",
            content.contains("name=\"windowSplashScreenAnimatedIcon\">@drawable/ic_elixir_launcher_foreground"))
        assertTrue("Must set postSplashScreenTheme",
            content.contains("name=\"postSplashScreenTheme\">@style/Theme.Elixir"))
    }

    @Test
    fun testManifestConfiguresStartingThemeAndLauncher() {
        val file = File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", file.exists())
        val content = file.readText()
        assertTrue("Application theme must be Theme.Elixir",
            content.contains("android:theme=\"@style/Theme.Elixir\""))
        assertTrue("MainActivity theme must be Theme.Elixir.Starting",
            content.contains("android:theme=\"@style/Theme.Elixir.Starting\""))
        assertTrue("Launcher icon must point to @mipmap/ic_launcher",
            content.contains("android:icon=\"@mipmap/ic_launcher\""))
    }

    @Test
    fun testMainActivityInitializesSplashScreen() {
        val file = File("src/main/kotlin/com/cellclaw/ui/MainActivity.kt")
        assertTrue("MainActivity.kt must exist", file.exists())
        val content = file.readText()
        assertTrue("Must import installSplashScreen",
            content.contains("androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen"))
        val splashIndex = content.indexOf("installSplashScreen()")
        val superCreate = content.indexOf("super.onCreate(savedInstanceState)")
        assertTrue("installSplashScreen() must be present", splashIndex != -1)
        assertTrue("super.onCreate must be present", superCreate != -1)
        assertTrue("installSplashScreen() must be called strictly before super.onCreate",
            splashIndex < superCreate)
    }

    @Test
    fun testServiceNotificationAndTileDisplayElixirBranding() {
        val serviceFile = File("src/main/kotlin/com/cellclaw/service/CellClawService.kt")
        assertTrue("CellClawService.kt must exist", serviceFile.exists())
        val serviceContent = serviceFile.readText()
        assertTrue("Service notification must display Elixir is running",
            serviceContent.contains("Elixir is running"))
        assertTrue("Service RemoteInput label must say Ask Elixir...",
            serviceContent.contains("Ask Elixir..."))

        val tileFile = File("src/main/kotlin/com/cellclaw/service/CellClawTileService.kt")
        assertTrue("CellClawTileService.kt must exist", tileFile.exists())
        val tileContent = tileFile.readText()
        assertTrue("QuickSettings tile label must be Elixir",
            tileContent.contains("tile.label = \"Elixir\""))
    }
}

package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.model.ConnectionStatus
import com.example.model.MissionTelemetry
import com.example.ui.components.TelemetryHeader
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        TelemetryHeader(
          telemetry = MissionTelemetry(
            nodeCallsign = "ISRO-SATCOM-ALPHA",
            peerCallsign = "ISRO-GROUND-BETA",
            frequencyGhz = "5.180 GHz (Ch 36)",
            signalDbm = -54,
            linkQualityPercent = 95,
            latencyMs = 16,
            batteryPercent = 89,
            powerDrawWatts = 0.38f,
            isModelLoaded = true
          ),
          connectionStatus = ConnectionStatus.CONNECTED,
          alertCount = 0
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

package io.github.yinlongdai.runwatch

import app.cash.paparazzi.Environment
import app.cash.paparazzi.detectEnvironment

/**
 * Paparazzi 1.3.5 renders with an Android 14 layoutlib but reports the app's
 * compileSdk (35) as the device SDK, which sends Compose down an Android 15
 * text path the renderer lacks. Report 34 so text lays out normally.
 */
fun testEnvironment(): Environment = detectEnvironment().copy(compileSdkVersion = 34)

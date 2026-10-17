package io.github.yinlongdai.runwatch

import io.github.yinlongdai.runwatch.update.ReleaseParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseParserTest {

    private fun release(tag: String, assets: String, extra: String = "") = JSONObject(
        """{"tag_name": "$tag", "body": "Fix alerts\nMore notes", "assets": [$assets] $extra}"""
    )

    private val apkAsset =
        """{"name": "runwatch-0.2.17.apk", "browser_download_url": "https://github.com/x/y/releases/download/v0.2.17/runwatch-0.2.17.apk"}"""

    @Test
    fun parsesTagAndApk() {
        val info = ReleaseParser.parse(release("v0.2.17", """{"name": "notes.txt", "browser_download_url": "https://x/n"}, $apkAsset"""))!!
        assertEquals("0.2.17", info.versionName)
        assertEquals(17, info.versionCode)
        assertEquals("https://github.com/x/y/releases/download/v0.2.17/runwatch-0.2.17.apk", info.apkUrl)
        assertEquals("Fix alerts\nMore notes", info.notes)
    }

    @Test
    fun rejectsReleasesItCantInstall() {
        assertNull(ReleaseParser.parse(release("v0.2.17", "")))                     // no APK
        assertNull(ReleaseParser.parse(release("nightly", apkAsset)))               // no build number
        assertNull(ReleaseParser.parse(release("v0.2.17", apkAsset, ""","prerelease": true""")))
        assertNull(
            ReleaseParser.parse(release("v0.2.17", """{"name": "a.apk", "browser_download_url": "http://insecure/a.apk"}"""))
        )
    }
}

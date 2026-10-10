package com.yike.ai

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit external-client check; run alone after installing AnkiDroid on test emulator. */
@RunWith(AndroidJUnit4::class)
class DroidCompatibilityLaunchTest {
    @Test
    fun launchActualPackageWithGrantedContentUri() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("droidCompatibility") == "true")
        val context = instrumentation.targetContext
        val source = File(context.getExternalFilesDir(null), "compatibility.apkg")
        assertTrue("Run AppFlowTest#exportRealPackageFixture first", source.exists())
        val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, "compatibility.apkg")
        source.copyTo(file, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.grantUriPermission("com.ichi2.anki", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(
            Intent(Intent.ACTION_SEND).apply {
                component = ComponentName("com.ichi2.anki", "com.ichi2.anki.IntentHandler")
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("APKG", uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
        // Keep the granting process alive while the receiving application copies the file.
        Thread.sleep(4000)
    }
}

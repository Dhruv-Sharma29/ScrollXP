package com.scrollxp.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scrollxp.app.ui.PrivacyPolicyDialog
import com.scrollxp.app.ui.theme.ScrollXPTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PrivacyAndBrandTest {
    @get:Rule val compose = createComposeRule()

    @Test fun offlinePolicyContainsIdentityDeletionAndSharingAtLargeTextSize() {
        var closed = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                ScrollXPTheme { PrivacyPolicyDialog { closed = true } }
            }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Developer: Dhruv Sharma"))
        compose.onNodeWithText("Developer: Dhruv Sharma").assertIsDisplayed()
        list.performScrollToNode(hasText("Privacy contact: dev.dhruvsharma29@gmail.com"))
        compose.onNodeWithText("Privacy contact: dev.dhruvsharma29@gmail.com").assertIsDisplayed()
        list.performScrollToNode(hasText("Your controls and deletion"))
        compose.onNodeWithText("Your controls and deletion").assertIsDisplayed()
        list.performScrollToNode(hasText("Optional postcards and sharing"))
        compose.onNodeWithText("Optional postcards and sharing").assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun packagedPolicyDisclosesOptionalOnlineFeaturesAndHasNoPendingFields() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val policy = context.assets.open("privacy-policy.md").bufferedReader().use { it.readText() }
        assertFalse(policy.contains("PENDING"))
        assertTrue(policy.contains("Optional accounts and cloud backup"))
        assertTrue(policy.contains("Included features"))
        assertTrue(policy.contains("Optional friend balance circles"))
        assertTrue(policy.contains("Optional Google Play subscriptions"))
        assertTrue(policy.contains("does not cancel a Google Play subscription"))
        assertTrue(policy.contains("deletion guard"))
        assertTrue(policy.contains("does not add a separate database encryption layer"))
        val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().contains("com.android.vending.BILLING"))
        assertTrue(info.requestedPermissions.orEmpty().contains("android.permission.INTERNET"))
        assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.QUERY_ALL_PACKAGES"))
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 33)
    @Test fun adaptiveAndMonochromeIconsRenderWithTransparentMaskCorners() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "brand-validation").apply { mkdirs() }
        val drawable = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val icon = Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        val mono = Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        try {
            drawable.setBounds(0,0,512,512); drawable.draw(Canvas(icon))
            assertEquals(0, android.graphics.Color.alpha(icon.getPixel(0,0)))
            assertTrue(android.graphics.Color.alpha(icon.getPixel(256,256)) > 0)
            val monochrome = requireNotNull(drawable.monochrome)
            monochrome.setBounds(0,0,512,512); monochrome.draw(Canvas(mono))
            assertTrue(android.graphics.Color.alpha(mono.getPixel(256,256)) > 0)
            assertFalse(icon.sameAs(mono))
            for ((name, bitmap) in listOf("adaptive" to icon,"monochrome" to mono)) {
                File(directory,"$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            }
        } finally { icon.recycle(); mono.recycle() }
    }
}

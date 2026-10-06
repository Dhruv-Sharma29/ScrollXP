package com.scrollxp.app

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scrollxp.app.ui.LoginEntry
import com.scrollxp.app.ui.theme.ScrollXPTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun launchShowsEntryAndDoesNotPretendToAuthenticate() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                ScrollXPTheme("Dusk") { LoginEntry { Text("Saved island") } }
            }
        }
        compose.onNodeWithText("Saved island").assertDoesNotExist()
        compose.onNodeWithText("Email address").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Password").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Sign in · Coming soon").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Continue on this device").assertIsDisplayed().performClick()
        compose.onNodeWithText("Saved island").assertIsDisplayed()
    }

    @Test fun continuingSurvivesActivityStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ScrollXPTheme { LoginEntry { Text("Saved island") } } }
        compose.onNodeWithText("Continue on this device").assertIsDisplayed().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Saved island").assertIsDisplayed()
        compose.onNodeWithText("Welcome to ScrollXP").assertDoesNotExist()
    }
}

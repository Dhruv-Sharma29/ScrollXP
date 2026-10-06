package com.scrollxp.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scrollxp.app.ui.OnlineFeaturesEntry
import com.scrollxp.app.ui.ScrollState
import com.scrollxp.app.ui.AccountForm
import com.scrollxp.app.ui.FriendCirclesPanel
import com.scrollxp.app.online.OnlineState
import com.scrollxp.app.ui.theme.ScrollXPTheme
import com.scrollxp.app.widget.IslandWidget
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectedFeaturesTest {
    @get:Rule val compose = createComposeRule()
    @Test fun unavailableAccountsCannotSaveButLocalExtrasAreFreeAtLargeTextSize() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {
                ScrollXPTheme("Dusk") { OnlineFeaturesEntry(ScrollState()) {} }
            }
        }
        compose.onNodeWithText("Explore connected features").performClick()
        compose.onNodeWithText("Back up now").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Create circle").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Join with code").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Subscribe with Google Play").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("View 30-day history").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Last 30 calendar days").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Weekly balance detail").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed().performClick()
        compose.onNodeWithText("Explore connected features").assertIsDisplayed()
    }
    @Test fun widgetProviderIsRegisteredWithARealLayoutAndPeriodicUpdate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = AppWidgetManager.getInstance(context).installedProviders.firstOrNull { it.provider == ComponentName(context,IslandWidget::class.java) }
        assertNotNull(provider); assertEquals(R.layout.island_widget,provider?.initialLayout)
        assertEquals(1_800_000,provider?.updatePeriodMillis)
    }
    @Test fun friendInvitationRequiresValidCodeAndOffersClearPrivacyAtLargeTextSize() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {
                ScrollXPTheme("Dusk") { Column(Modifier.verticalScroll(rememberScrollState())) {
                    FriendCirclesPanel(OnlineState(configured = true,uid = "test-member",verified = true),null)
                } }
            }
        }
        compose.onNodeWithText("Join with code").performScrollTo().performClick()
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithText("Name shown to friends").performTextInput("Test member")
        compose.onNodeWithText("Invite code").performTextInput("invalid-code")
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithText("Invite code").performTextClearance()
        compose.onNodeWithText("Invite code").performTextInput("a123456789abcdef0123")
        compose.onNodeWithText("Confirm").assertIsEnabled()
        compose.onNodeWithText("Members can see",substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Join a friend's circle").assertDoesNotExist()
    }
    @Test fun unverifiedAccountsCannotJoinCreateOrRefreshFriendCircles() {
        compose.setContent { ScrollXPTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            FriendCirclesPanel(OnlineState(configured = true,uid = "test-member",verified = false),null)
        } } }
        compose.onNodeWithText("Create circle").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Join with code").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Refresh circles").performScrollTo().assertIsNotEnabled()
    }
    @Test fun sparkAccountFormRequiresValidInputAndClearsPasswordWhenChangingMode() {
        compose.setContent { ScrollXPTheme { AccountForm(OnlineState(configured = true),null) {} } }
        compose.onNodeWithText("Sign in",useUnmergedTree = false).assertIsNotEnabled()
        compose.onNodeWithText("Email address").performTextInput("test@example.test")
        compose.onNodeWithText("Password",substring = false).performTextInput("TestPassword123")
        compose.onNodeWithText("Sign in",substring = false).assertIsEnabled()
        compose.onNodeWithText("New here? Create account").performClick()
        compose.onNodeWithText("Create account",substring = false).assertIsNotEnabled()
        compose.onNodeWithText("Password",substring = false).performTextInput("short")
        compose.onNodeWithText("Create account",substring = false).assertIsNotEnabled()
        compose.onNodeWithText("Password",substring = false).performTextClearance()
        compose.onNodeWithText("Password",substring = false).performTextInput("TestPassword123")
        compose.onNodeWithText("Create account",substring = false).assertIsEnabled()
    }
}

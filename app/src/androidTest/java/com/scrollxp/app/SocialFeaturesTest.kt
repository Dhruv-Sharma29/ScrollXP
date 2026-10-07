package com.scrollxp.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.scrollxp.app.ui.*
import com.scrollxp.app.ui.theme.ScrollXPTheme
import org.junit.Rule
import org.junit.Test

class SocialFeaturesTest {
    @get:Rule val compose = createComposeRule()
    @Test fun guestCannotClaimOrFindFriendsAndCanOpenRanking() {
        var opened = false
        compose.setContent { ScrollXPTheme { Column(Modifier.verticalScroll(rememberScrollState())) { FriendsPage { opened = true } } } }
        compose.onNodeWithText("Choose username").performTextInput("dhruv29")
        compose.onNodeWithText("Claim username").assertIsNotEnabled()
        compose.onNodeWithText("Friend's username").performScrollTo().performTextInput("rahul29")
        compose.onNodeWithText("Find friend").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Open Ranking").performScrollTo().performClick()
        org.junit.Assert.assertTrue(opened)
    }
    @Test fun rankingHasNoFakeResultsAndLocalParticipationIsOptional() {
        compose.setContent { ScrollXPTheme { Column(Modifier.verticalScroll(rememberScrollState())) { RankingPage() } } }
        compose.onNodeWithText("Start sharing score").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Local", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Join / update local board").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("No GPS", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Top 50 in Dehradun").performScrollTo().assertIsDisplayed()
    }
}

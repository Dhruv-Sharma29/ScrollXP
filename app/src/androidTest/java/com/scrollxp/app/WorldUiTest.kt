package com.scrollxp.app

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scrollxp.app.data.*
import com.scrollxp.app.domain.Worlds
import com.scrollxp.app.sharing.*
import com.scrollxp.app.ui.*
import com.scrollxp.app.ui.theme.ScrollXPTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WorldUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun lockedDestinationPreviewCannotVisitOrAlterProgress() {
        var visits = 0
        val state = ScrollState(profile = Profile(onboarded = true), xp = 2, loading = false)
        compose.setContent { ScrollXPTheme { WorldRegions(state) { visits++ } } }
        compose.onNodeWithText("Preview Seaside cove").performClick()
        compose.onNodeWithText("DESTINATION PREVIEW").assertIsDisplayed()
        compose.onNodeWithText("Unlocks at 5000 lifetime XP · 4998 XP to go").assertIsDisplayed()
        compose.onNodeWithText("Back to my world").performClick()
        compose.runOnIdle { assertEquals(0, visits); assertEquals(2, state.xp); assertEquals("meadow", state.profile.region) }
    }
    @Test fun guestCanPreviewDestinationsBeforeCreatingAnIsland() {
        var submissions = 0
        val state = ScrollState(loading = false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                ScrollXPTheme { Welcome(state) { _, _, _ -> submissions++ } }
            }
        }
        compose.onNodeWithText("Coast").performScrollTo().performClick()
        compose.onNodeWithText("Seaside cove").assertIsDisplayed()
        compose.onNodeWithText("Clouds").performClick()
        compose.onNodeWithText("Cloud sanctuary").assertIsDisplayed()
        compose.onNodeWithText("Create my island").performScrollTo().performClick()
        compose.onNodeWithText("Make it yours").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, submissions) }
    }
    @Test fun guideShowsProgressAndCanBeTuckedAwayWithoutAReward() {
        val state = mutableStateOf(ScrollState(profile = Profile(onboarded = true, hasPlacedTreasure = true),
            xp = 2, hasAccess = true, loading = false, chests = listOf(Chest("milestone:0", "Welcome", 1, "lantern", "COMMON", openedAt = 2))))
        var goals = 0
        compose.setContent { ScrollXPTheme { FirstChapterCard(state.value, {}, {}, { goals++ }, {},
            { state.value = state.value.copy(profile = state.value.profile.copy(guideDismissed = true)) }) } }
        compose.onNodeWithText("3 / 4").assertIsDisplayed()
        compose.onNodeWithText("See my budget goal").performClick()
        compose.onNodeWithText("Tuck away guide").performClick()
        compose.onNodeWithText("YOUR FIRST CHAPTER").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, goals); assertEquals(2, state.value.xp) }
    }
    @Test fun earnedDestinationCanBeVisitedAndRevisitedFromTheSelector() {
        val state = mutableStateOf(ScrollState(profile = Profile(onboarded = true), xp = 5000, loading = false))
        compose.setContent { ScrollXPTheme { WorldRegions(state.value) { id ->
            state.value = state.value.copy(profile = state.value.profile.copy(region = id))
        } } }
        compose.onNodeWithText("Visit Seaside cove").performClick()
        compose.onNodeWithText("Visit Meadow haven").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("meadow", state.value.world.id); assertEquals(5000, state.value.xp) }
    }
    @Test fun everyDestinationRendersInBothThemesAndExportsItsOwnScenery() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "world-validation").apply { mkdirs() }
        val all = mutableListOf<Bitmap>()
        try {
            for (world in Worlds.all) for (dusk in listOf(false, true)) {
                val snapshot = IslandSnapshot("Little haven", 10000, 0, emptySet(), setOf("lantern", "bench", "flowers"), dusk, region = world.id)
                val bitmap = IslandExport.render(snapshot); all += bitmap
                File(directory, "${world.id}-${if (dusk) "dusk" else "day"}.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                assertEquals(1080, bitmap.width); assertEquals(1350, bitmap.height)
            }
            for (i in all.indices) for (j in i + 1 until all.size) assertFalse(all[i].sameAs(all[j]))
        } finally { all.forEach { it.recycle() } }
    }
}

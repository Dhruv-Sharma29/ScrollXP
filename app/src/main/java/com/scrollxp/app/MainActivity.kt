package com.scrollxp.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import com.scrollxp.app.online.OnlineViewModel
import com.scrollxp.app.online.PlayBillingModel
import com.scrollxp.app.ui.LocalBillingModel
import com.scrollxp.app.ui.LocalOnlineModel
import com.scrollxp.app.ui.theme.LocalDusk
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.scrollxp.app.ui.ScrollApp
import com.scrollxp.app.ui.LoginEntry
import com.scrollxp.app.ui.ScrollViewModel
import com.scrollxp.app.ui.theme.ScrollXPTheme
import com.scrollxp.app.worker.UsageWorker
import com.scrollxp.app.worker.ReminderScheduler

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[ScrollViewModel::class.java] }
    private val online by lazy { ViewModelProvider(this)[OnlineViewModel::class.java] }
    private val billing by lazy { ViewModelProvider(this)[PlayBillingModel::class.java] }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UsageWorker.schedule(this)
        ReminderScheduler.channel(this)
        setContent {
            val state by model.state.collectAsState()
            val pro by billing.state.collectAsState()
            ScrollXPTheme(state.profile.appearance, state.profile.reducedMotion, proTheme = pro.theme.takeIf { pro.active }) {
                val dusk = LocalDusk.current
                DisposableEffect(dusk) {
                    val transparent = android.graphics.Color.TRANSPARENT
                    val style = if (dusk) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose { }
                }
                CompositionLocalProvider(LocalOnlineModel provides online, LocalBillingModel provides billing) {
                    LoginEntry { ScrollApp(state, model, ::openUsageSettings) }
                }
            }
        }
    }
    override fun onResume() {
        super.onResume(); model.refresh()
        billing.refresh()
        if (online.state.value.uid != null) online.refresh()
    }
    private fun openUsageSettings() {
        try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        catch (_: Exception) { Toast.makeText(this, "Open Android Settings and search for Usage Access.", Toast.LENGTH_LONG).show() }
    }
}

package com.scrollxp.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollxp.app.ui.theme.*
import com.scrollxp.app.R

/** Entry UI only. No credentials are collected until an account service is connected. */
@Composable
internal fun LoginEntry(content: @Composable () -> Unit) {
    // Keep the choice through rotation; show the entry page again on a fresh launch.
    var entered by rememberSaveable { mutableStateOf(false) }
    if (entered) content() else LoginPage { entered = true }
}

@Composable
internal fun LoginPage(continueOnDevice: () -> Unit) {
    val model = LocalOnlineModel.current
    val online = model?.state?.collectAsState()?.value ?: com.scrollxp.app.online.OnlineState()
    Scaffold(containerColor = Paper, contentColor = Ink, bottomBar = {
        Surface(color = Paper, contentColor = Ink) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = continueOnDevice, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                        Text("Continue on this device", fontWeight = FontWeight.Bold)
                    }
                    Text("Your saved island stays with you.", color = Muted, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp))
                    PrivacyPolicyLink()
                }
            }
        }
    }) { padding ->
    Column(Modifier.padding(padding).fillMaxSize().background(Paper).statusBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("scroll", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Surface(color = Lime, shape = RoundedCornerShape(9.dp), modifier = Modifier.padding(start = 4.dp)) {
                    Text("XP", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.weight(1f))
                Text("YOUR LITTLE ESCAPE", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Surface(color = Card, shape = RoundedCornerShape(28.dp)) {
                Image(painterResource(R.drawable.scrollxp_logo), contentDescription = "ScrollXP palm island logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(210.dp).padding(18.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A little progress.\nA place of your own.", fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 37.sp,
                    modifier = Modifier.semantics { heading() })
                Text("Turn small moments into a world that grows with you.", color = Muted, lineHeight = 23.sp)
            }
            Surface(color = Card, shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Welcome to ScrollXP", fontSize = 21.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() })
                    if (online.configured && online.uid == null) {
                        AccountForm(online,model,continueOnDevice)
                    } else if (online.uid != null) {
                        Text("Signed in as ${online.email.orEmpty()}. Your local island is ready.",color = Muted)
                    } else {
                    Text("Account sign-in is coming soon. For now, your island lives on this device.",
                        color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                    // Disabled fields advertise the planned entry flow without asking for secrets
                    // or pretending to authenticate a user in this offline build.
                    OutlinedTextField(value = "", onValueChange = {}, enabled = false,
                        label = { Text("Email address") }, singleLine = true,
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = "", onValueChange = {}, enabled = false,
                        label = { Text("Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    Button(onClick = {}, enabled = false, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Sign in · Coming soon")
                    }
                    Text("Existing island? You’ll pick up where you left off. New here? We’ll help you create one.",
                        color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
    }
    }
}

package com.scrollxp.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollxp.app.ui.theme.Muted

@Composable
internal fun PrivacyPolicyLink() {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("Privacy policy") }
    if (open) PrivacyPolicyDialog { open = false }
}

@Composable
internal fun PrivacyPolicyDialog(close: () -> Unit) {
    val context = LocalContext.current
    val paragraphs = remember(context) {
        context.assets.open("privacy-policy.md").bufferedReader().use { it.readText() }
            .split("\n\n").filter { it.isNotBlank() && !it.startsWith("# ") }
    }
    AlertDialog(onDismissRequest = close, title = { Text("Privacy policy") },
        text = {
            SelectionContainer {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(paragraphs) { paragraph ->
                        if (paragraph.startsWith("## ")) Text(paragraph.removePrefix("## "),
                            fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                        else Text(paragraph, fontSize = 14.sp, lineHeight = 22.sp, color = Muted)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = close) { Text("Close") } })
}

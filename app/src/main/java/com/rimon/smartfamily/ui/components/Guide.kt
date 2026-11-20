package com.rimon.smartfamily.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rimon.smartfamily.R

/**
 * 2.2 setup wizards: one numbered step with a short explanation and its actions (open a site, copy a value,
 * a field, a test). Steps that are done show a tick, so a returning user sees where they stopped.
 */
@Composable
fun GuideStep(n: Int, title: String, body: String? = null, done: Boolean = false, content: @Composable () -> Unit = {}) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(28.dp).background(if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(if (done) "✓" else n.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (!body.isNullOrBlank()) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                content()
            }
        }
    }
}

/** Opens [url] in the browser; the button always shows the site's name so it is clear where it leads. */
@Composable
fun OpenSiteButton(label: String, url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val host = runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
    OutlinedButton(onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }, modifier = modifier) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
        Text("  $label" + if (host.isNotBlank()) " · $host" else "")
    }
}

/** A value to type on another site (e.g. "Device Flow"), with a copy button. */
@Composable
fun CopyValue(label: String, value: String) {
    val clip = LocalClipboardManager.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", style = MaterialTheme.typography.bodyMedium)
        AssistChip(onClick = { clip.setText(AnnotatedString(value)) }, label = { Text(value) },
            trailingIcon = { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy), Modifier.size(16.dp)) })
    }
}

/** Result of a connection test: null = not tested yet. */
@Composable
fun TestResult(ok: Boolean?, text: String) {
    if (ok == null && text.isBlank()) return
    Text(
        (if (ok == true) "✓ " else if (ok == false) "✗ " else "") + text,
        style = MaterialTheme.typography.bodyMedium,
        color = when (ok) { true -> MaterialTheme.colorScheme.tertiary; false -> MaterialTheme.colorScheme.error; null -> MaterialTheme.colorScheme.onSurfaceVariant },
    )
}

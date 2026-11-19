package com.rimon.smartfamily.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import com.rimon.smartfamily.R
import java.io.File

/**
 * The quick-add bar used by the shopping list and the inventory: type (with suggestions from
 * history), speak, scan a barcode, or take/choose a photo for AI recognition.
 */
@Composable
fun AddBar(
    hint: String,
    suggestions: List<String>,
    onText: (String) -> Unit,
    onScan: () -> Unit,
    onPhoto: (Uri) -> Unit,
    photoLabel: String,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var photoMenu by remember { mutableStateOf(false) }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val lang = LocalConfiguration.current.locales[0].toLanguageTag()

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = pendingPhoto
        if (ok && p != null) onPhoto(Uri.parse(p))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhoto(uri)
    }

    fun submit() {
        if (text.isNotBlank()) { onText(text); text = "" }
    }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(hint) },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (text.isNotBlank()) {
                        IconButton(onClick = { submit() }) { Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.shop_add)) }
                    } else {
                        IconButton(enabled = enabled, onClick = {
                            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang.startsWith("he") || lang.startsWith("iw")) "he-IL" else "en-US")
                            runCatching { speech.launch(i) }
                        }) { Icon(Icons.Outlined.Mic, stringResource(R.string.shop_voice)) }
                        IconButton(enabled = enabled, onClick = onScan) { Icon(Icons.Outlined.QrCodeScanner, stringResource(R.string.shop_scan)) }
                        Box {
                            IconButton(enabled = enabled, onClick = { photoMenu = true }) { Icon(Icons.Outlined.CameraAlt, photoLabel) }
                            DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.shop_take_photo)) },
                                    leadingIcon = { Icon(Icons.Outlined.CameraAlt, null) },
                                    onClick = {
                                        photoMenu = false
                                        val uri = newPhotoUri(context)
                                        pendingPhoto = uri.toString()
                                        runCatching { camera.launch(uri) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.shop_pick_photo)) },
                                    leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                                    onClick = {
                                        photoMenu = false
                                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )
        val matches = remember(text, suggestions) {
            val t = text.trim()
            if (t.length < 2) emptyList()
            else suggestions.filter { it.contains(t, ignoreCase = true) && !it.equals(t, ignoreCase = true) }.take(6)
        }
        if (matches.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                matches.forEach { m ->
                    SuggestionChip(onClick = { onText(m); text = "" }, label = { Text(m) })
                }
            }
        }
    }
}

private fun newPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    // Old photos are not kept: they were only needed for recognition.
    dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 60 * 60 * 1000 }?.forEach { it.delete() }
    val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, context.packageName + ".files", file)
}

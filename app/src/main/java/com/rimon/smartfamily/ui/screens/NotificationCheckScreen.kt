package com.rimon.smartfamily.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.rimon.smartfamily.R
import com.rimon.smartfamily.smart.ChatNotifier
import com.rimon.smartfamily.sync.Push
import com.rimon.smartfamily.ui.AppViewModel
import com.rimon.smartfamily.ui.components.GuideStep
import com.rimon.smartfamily.ui.components.SfTopAppBar
import com.rimon.smartfamily.ui.components.TestResult
import kotlinx.coroutines.delay

/** 2.2 notification check: permission, channel, battery limits, background data, VPN, registration, then a real test push. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCheckScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val life = LocalLifecycleOwner.current
    LaunchedEffect(Unit) { life.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val test by vm.pushTest.collectAsState()
    var arrived by remember { mutableLongStateOf(Push.lastTestAt(context)) }
    var sentAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sentAt) { if (sentAt > 0) repeat(40) { delay(500); arrived = Push.lastTestAt(context) } }

    // re-read every time the screen comes back from the system settings
    val granted = tick >= 0 && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    val channelOk = tick >= 0 && !ChatNotifier.channelBlocked(context)
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val batteryOk = tick >= 0 && pm.isIgnoringBatteryOptimizations(context.packageName)
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val bgDataOk = tick >= 0 && cm.restrictBackgroundStatus != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    val vpn = tick >= 0 && cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    val firebase = remember { Push.available(context) }
    val registered = tick >= 0 && Push.registered(context)

    fun appSettings() = runCatching {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun appDetails() = runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Scaffold(topBar = {
        SfTopAppBar(title = { Text(stringResource(R.string.nc_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } })
    }) { inner ->
        Column(Modifier.padding(top = inner.calculateTopPadding()).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val allOk = granted && channelOk && batteryOk && bgDataOk && firebase && registered
            Text(stringResource(if (allOk) R.string.nc_all_ok else R.string.nc_some_bad), style = MaterialTheme.typography.titleMedium,
                color = if (allOk) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error, modifier = Modifier.testTag("nc_summary"))
            GuideStep(1, stringResource(R.string.nc_perm), null, done = granted && channelOk) {
                TestResult(granted && channelOk, stringResource(if (granted && channelOk) R.string.nc_ok else R.string.nc_perm_bad))
                if (!granted && Build.VERSION.SDK_INT >= 33) Button(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.nc_allow)) }
                else if (!channelOk) Button(onClick = { appSettings() }) { Text(stringResource(R.string.nc_open_settings)) }
            }
            GuideStep(2, stringResource(R.string.nc_battery), stringResource(R.string.nc_battery_body), done = batteryOk) {
                TestResult(batteryOk, stringResource(if (batteryOk) R.string.nc_ok else R.string.nc_battery_bad))
                if (!batteryOk) Button(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { appDetails() } }) {
                    Text(stringResource(R.string.nc_open_settings))
                }
            }
            GuideStep(3, stringResource(R.string.nc_data), null, done = bgDataOk && !vpn) {
                TestResult(bgDataOk, stringResource(if (bgDataOk) R.string.nc_ok else R.string.nc_data_bad))
                if (vpn) TestResult(null, stringResource(R.string.nc_vpn))
                if (!bgDataOk) Button(onClick = { appDetails() }) { Text(stringResource(R.string.nc_open_settings)) }
            }
            GuideStep(4, stringResource(R.string.nc_reg), null, done = firebase && registered) {
                TestResult(firebase && registered, stringResource(when { !firebase -> R.string.nc_no_firebase; !registered -> R.string.nc_not_registered; else -> R.string.nc_ok }))
            }
            GuideStep(5, stringResource(R.string.nc_test), stringResource(R.string.nc_test_hint), done = arrived > sentAt && sentAt > 0) {
                OutlinedButton(onClick = { sentAt = System.currentTimeMillis(); vm.sendTestPush() }, modifier = Modifier.testTag("nc_send")) { Text(stringResource(R.string.nc_send)) }
                when {
                    sentAt > 0 && arrived > sentAt -> TestResult(true, stringResource(R.string.nc_arrived))
                    test == "offline" -> TestResult(false, stringResource(R.string.ai_check_offline))
                    test == "error" -> TestResult(false, stringResource(R.string.nc_server_error))
                    test == "…" -> TestResult(null, stringResource(R.string.nc_sending))
                    test != null && test!!.startsWith("0/") -> TestResult(false, stringResource(R.string.nc_not_registered))
                    test != null -> TestResult(null, stringResource(R.string.nc_sent_waiting, test!!))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

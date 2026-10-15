package com.focus.intentlauncher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AppTier { GREEN, YELLOW, RED }

data class AppItem(
    val packageName: String, val label: String, var tier: AppTier = AppTier.GREEN,
    var dailyQuotaMinutes: Int = 0, var usedMinutesToday: Int = 0, var cooldownUntilMillis: Long = 0L
)

data class Strings(
    val searchPlaceholder: String, val thoughtPlaceholder: String, val settingsTitle: String,
    val intentionPrompt: String, val minCharsWarning: String, val preSessionDelayText: String,
    val selectDuration: String, val startSession: String, val cancel: String,
    val quotaReached: String, val inCooldown: String, val focusMode: String,
    val endOfDayReport: String, val gatePrompt: String, val unlockSettings: String,
    val redCooldownSetting: String, val yellowCooldownSetting: String, val delaySetting: String,
    val enableWidgets: String, val languageToggle: String, val beforeAfterComparison: String, val grantPermission: String
)

val EnglishStrings = Strings(
    "Search apps...", "Dump intrusive thought & press Enter...", "Settings & Regulation",
    "State your intention (min 6 chars):", "Enter at least 6 characters to proceed.", "Take a breath. Unlocking in",
    "Session duration (minutes):", "Begin Session", "Cancel",
    "Daily quota reached. Locked until tomorrow.", "Cooling down. Wait:", "Focus Mode",
    "End-of-Day Report", "Wait to unlock settings...", "Unlock Settings (5s delay)",
    "Red Cooldown Wait (min):", "Yellow Re-open Wait (min):", "Pre-session Delay (sec):",
    "Show Home Screen Widget", "العربية", "7-Day Avg vs Today:", "Grant Usage Permission (Required)"
)

val ArabicStrings = Strings(
    "ابحث عن التطبيقات...", "اكتب الفكرة الملحة واضغط إدخال...", "الإعدادات والتنظيم",
    "حدد نيتك (6 أحرف على الأقل):", "يرجى إدخال 6 أحرف على الأقل للمتابعة.", "خذ نفساً عميقاً. يفتح خلال",
    "مدة الجلسة (بالدقائق):", "ابدأ الجلسة", "إلغاء",
    "انتهت الحصة اليومية. مغلق حتى الغد.", "فترة انتظار قبل إعادة الفتح:", "وضع التركيز",
    "تقرير نهاية اليوم", "انتظر لفتح الإعدادات...", "فتح الإعدادات (تأخير 5 ثوانٍ)",
    "انتظار الفئة الحمراء (دقيقة):", "انتظار إعادة فتح الصفراء (دقيقة):", "تأخير ما قبل الجلسة (ثانية):",
    "إظهار الويدجت", "English", "المعدل السابق مقابل اليوم:", "منح إذن الاستخدام (مطلوب)"
)

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("regulated_launcher_prefs", Context.MODE_PRIVATE)
        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, 1024)
        appWidgetHost.startListening()
        setContent {
            val scheme = darkColorScheme(primary = Color.White, onPrimary = Color.Black, background = Color(0xFF0A0A0A), onBackground = Color(0xFFEAEAEA), surface = Color(0xFF121212), onSurface = Color(0xFFEAEAEA))
            val typo = Typography(bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = Color(0xFFEAEAEA)), titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light, fontSize = 22.sp, color = Color.White))
            MaterialTheme(colorScheme = scheme, typography = typo) { LauncherScreen(this, prefs, appWidgetManager, appWidgetHost) }
        }
    }

    override fun onDestroy() { super.onDestroy(); appWidgetHost.stopListening() }
}

@Composable
fun LauncherScreen(context: Context, prefs: SharedPreferences, appWidgetManager: AppWidgetManager, appWidgetHost: AppWidgetHost) {
    BackHandler(enabled = true) { }
    var isArabic by remember { mutableStateOf(prefs.getBoolean("is_arabic", false)) }
    val strings = if (isArabic) ArabicStrings else EnglishStrings
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    val focusManager = LocalFocusManager.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var preSessionDelaySec by remember { mutableStateOf(prefs.getInt("delay_sec", 5)) }
    var yellowCooldownMin by remember { mutableStateOf(prefs.getInt("yellow_cooldown_min", 2)) }
    var redCooldownMin by remember { mutableStateOf(prefs.getInt("cooldown_min", 15)) }
    var focusModeEnabled by remember { mutableStateOf(prefs.getBoolean("focus_mode", false)) }
    var widgetsEnabled by remember { mutableStateOf(prefs.getBoolean("widgets_enabled", false)) }
    val activeWidgetId by remember { mutableStateOf(prefs.getInt("active_widget_id", -1)) }

    var searchQuery by remember { mutableStateOf("") }
    var thoughtInput by remember { mutableStateOf("") }
    val thoughts = remember {
        val saved = prefs.getString("saved_thoughts", "") ?: ""
        val list = if (saved.isBlank()) emptyList() else saved.split("|||")
        mutableStateListOf<String>().apply { addAll(list) }
    }
    fun saveThoughts() { prefs.edit().putString("saved_thoughts", thoughts.joinToString("|||")).apply() }

    val apps = remember { mutableStateListOf<AppItem>() }
    var hasPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var selectedAppForPrompt by remember { mutableStateOf<AppItem?>(null) }
    var activeSessionPkg by remember { mutableStateOf<String?>(null) }
    var activeSessionLabel by remember { mutableStateOf("") }
    var activeSessionTier by remember { mutableStateOf(AppTier.GREEN) }
    var sessionEndTimeMillis by remember { mutableStateOf(0L) }
    var sessionRemainingSeconds by remember { mutableStateOf(0) }
    var userLeftLauncher by remember { mutableStateOf(false) }
    var currentTimeString by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }

    fun reloadApps() {
        hasPermission = hasUsageStatsPermission(context)
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val usageStats = getTodayUsageMinutes(context)
        apps.clear()
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName) {
                val label = info.loadLabel(pm).toString()
                val tierStr = prefs.getString("tier_$pkg", AppTier.GREEN.name) ?: AppTier.GREEN.name
                val savedTier = try { AppTier.valueOf(tierStr) } catch (e: Exception) { AppTier.GREEN }
                apps.add(AppItem(pkg, label, savedTier, prefs.getInt("quota_$pkg", 0), usageStats[pkg] ?: 0, prefs.getLong("cooldown_$pkg", 0L)))
            }
        }
        apps.sortBy { item: AppItem -> item.label.lowercase(Locale.getDefault()) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (activeSessionPkg != null) userLeftLauncher = true
            } else if (event == Lifecycle.Event.ON_RESUME) {
                reloadApps()
                searchQuery = ""
                if (activeSessionPkg != null && userLeftLauncher) {
                    activeSessionPkg = null; sessionEndTimeMillis = 0L; sessionRemainingSeconds = 0; userLeftLauncher = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            val now = System.currentTimeMillis()
            currentTimeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
            if (activeSessionPkg != null && sessionEndTimeMillis > 0L) {
                val rem = ((sessionEndTimeMillis - now) / 1000L).toInt()
                if (rem > 0) sessionRemainingSeconds = rem else {
                    val expiredPkg = activeSessionPkg!!
                    val waitMin = if (activeSessionTier == AppTier.RED) redCooldownMin else yellowCooldownMin
                    val cooldownUntil = now + (waitMin * 60 * 1000L)
                    prefs.edit().putLong("cooldown_$expiredPkg", cooldownUntil).apply()
                    for (app in apps) { if (app.packageName == expiredPkg) app.cooldownUntilMillis = cooldownUntil }
                    activeSessionPkg = null; sessionEndTimeMillis = 0L; sessionRemainingSeconds = 0; userLeftLauncher = false
                    bringLauncherToFront(context)
                }
            }
            if (hasPermission) {
                val fgPkg = getCurrentForegroundPackage(context)
                if (fgPkg != null && fgPkg != context.packageName) {
                    var matchedApp: AppItem? = null
                    for (app in apps) { if (app.packageName == fgPkg) { matchedApp = app; break } }
                    if (matchedApp != null && (matchedApp.tier == AppTier.YELLOW || matchedApp.tier == AppTier.RED)) {
                        if (activeSessionPkg != fgPkg) bringLauncherToFront(context)
                    }
                }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                SettingsDrawer(
                    strings, apps, prefs, hasPermission,
                    { isArabic = !isArabic; prefs.edit().putBoolean("is_arabic", isArabic).apply() },
                    preSessionDelaySec, { v: Int -> preSessionDelaySec = v; prefs.edit().putInt("delay_sec", v).apply() },
                    yellowCooldownMin, { v: Int -> yellowCooldownMin = v; prefs.edit().putInt("yellow_cooldown_min", v).apply() },
                    redCooldownMin, { v: Int -> redCooldownMin = v; prefs.edit().putInt("cooldown_min", v).apply() },
                    focusModeEnabled, { v: Boolean -> focusModeEnabled = v; prefs.edit().putBoolean("focus_mode", v).apply() },
                    widgetsEnabled, { v: Boolean -> widgetsEnabled = v; prefs.edit().putBoolean("widgets_enabled", v).apply() }, context
                )
            }
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { focusManager.clearFocus() }
                    .padding(horizontal = 24.dp, vertical = 36.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = currentTimeString, style = MaterialTheme.typography.titleLarge, fontSize = 34.sp)
                    if (activeSessionPkg != null && sessionRemainingSeconds > 0) {
                        Text(text = "$activeSessionLabel: ${sessionRemainingSeconds / 60}m ${sessionRemainingSeconds % 60}s", fontSize = 12.sp, color = Color(0xFFFF6B6B), modifier = Modifier.border(1.dp, Color(0xFFFF6B6B)).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                if (widgetsEnabled && activeWidgetId != -1) {
                    Box(modifier = Modifier.fillMaxWidth().height(110.dp).border(1.dp, Color(0xFF222222)).padding(8.dp)) {
                        AndroidView(factory = { ctx: Context -> appWidgetHost.createView(ctx, activeWidgetId, appWidgetManager.getAppWidgetInfo(activeWidgetId)) }, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                OutlinedTextField(
                    value = thoughtInput, onValueChange = { t: String -> thoughtInput = t },
                    placeholder = { Text(strings.thoughtPlaceholder, fontSize = 12.sp, color = Color(0xFF666666)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { val tr = thoughtInput.trim(); if (tr.isNotEmpty()) { thoughts.add(0, tr); saveThoughts(); thoughtInput = "" } }),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF555555), unfocusedBorderColor = Color(0xFF222222), cursorColor = Color.White)
                )
                if (thoughts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp).border(1.dp, Color(0xFF1F1F1F)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(thoughts) { item: String ->
                            Text(text = "• $item", fontSize = 13.sp, color = Color(0xFFAAAAAA), modifier = Modifier.fillMaxWidth().clickable { thoughts.remove(item); saveThoughts() }.padding(vertical = 2.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                val filteredApps = remember(searchQuery, apps.size) {
                    if (searchQuery.isBlank()) emptyList() else {
                        val q = searchQuery.trim()
                        val starts = mutableListOf<AppItem>()
                        val contains = mutableListOf<AppItem>()
                        for (app in apps) {
                            if (app.label.startsWith(q, ignoreCase = true)) starts.add(app)
                            else if (app.label.contains(q, ignoreCase = true)) contains.add(app)
                        }
                        starts + contains
                    }
                }
                fun triggerApp(target: AppItem) {
                    val now = System.currentTimeMillis()
                    val inCooldown = (target.tier == AppTier.RED || target.tier == AppTier.YELLOW) && now < target.cooldownUntilMillis
                    val quotaExceeded = target.tier == AppTier.RED && target.dailyQuotaMinutes > 0 && target.usedMinutesToday >= target.dailyQuotaMinutes
                    if (!inCooldown && !quotaExceeded) {
                        focusManager.clearFocus(); searchQuery = ""
                        if (target.tier == AppTier.GREEN) launchPackage(context, target.packageName) else selectedAppForPrompt = target
                    }
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { q: String ->
                        searchQuery = q; val clean = q.trim()
                        if (clean.isNotEmpty()) {
                            val matches = mutableListOf<AppItem>()
                            for (a in apps) { if (a.label.contains(clean, ignoreCase = true)) matches.add(a) }
                            if (matches.size == 1) triggerApp(matches[0])
                        }
                    },
                    placeholder = { Text(strings.searchPlaceholder, fontSize = 14.sp, color = Color.Gray) },
                    singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (filteredApps.isNotEmpty()) triggerApp(filteredApps[0]) }),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White, unfocusedBorderColor = Color(0xFF333333), cursorColor = Color.White)
                )
                Spacer(modifier = Modifier.height(18.dp))
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredApps, key = { item: AppItem -> item.packageName }) { appItem: AppItem ->
                        AppRow(app = appItem, strings = strings, onClick = { triggerApp(appItem) })
                    }
                }
            }
            if (selectedAppForPrompt != null) {
                IntentionAndTimerDialog(
                    app = selectedAppForPrompt!!, strings = strings, delaySeconds = preSessionDelaySec,
                    onDismiss = { selectedAppForPrompt = null },
                    onConfirm = { mins: Int, _: String ->
                        val targetApp = selectedAppForPrompt!!
                        selectedAppForPrompt = null; activeSessionPkg = targetApp.packageName; activeSessionLabel = targetApp.label; activeSessionTier = targetApp.tier
                        sessionEndTimeMillis = System.currentTimeMillis() + (mins * 60 * 1000L); sessionRemainingSeconds = mins * 60; userLeftLauncher = false
                        launchPackage(context, targetApp.packageName)
                    }
                )
            }
        }
    }
}

@Composable
fun AppRow(app: AppItem, strings: Strings, onClick: () -> Unit) {
    val now = System.currentTimeMillis()
    val inCooldown = (app.tier == AppTier.RED || app.tier == AppTier.YELLOW) && now < app.cooldownUntilMillis
    val quotaExceeded = app.tier == AppTier.RED && app.dailyQuotaMinutes > 0 && app.usedMinutesToday >= app.dailyQuotaMinutes
    val tierColor = when (app.tier) { AppTier.GREEN -> Color(0xFF4CAF50); AppTier.YELLOW -> Color(0xFFFFC107); AppTier.RED -> Color(0xFFF44336) }
    Column(modifier = Modifier.fillMaxWidth().clickable(enabled = !inCooldown && !quotaExceeded) { onClick() }.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier(modifier = Modifier.size(8.dp).background(tierColor))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = app.label, color = if (inCooldown || quotaExceeded) Color.DarkGray else Color.White, fontSize = 16.sp)
            }
            if (app.tier == AppTier.RED && app.dailyQuotaMinutes > 0) Text(text = "${app.usedMinutesToday}/${app.dailyQuotaMinutes}m", fontSize = 12.sp, color = Color.Gray)
        }
        if (inCooldown) {
            val remSec = ((app.cooldownUntilMillis - now) / 1000L).coerceAtLeast(1L)
            Text(text = "${strings.inCooldown} ${remSec / 60}m ${remSec % 60}s", fontSize = 11.sp, color = Color(0xFFE57373), modifier = Modifier.padding(start = 20.dp, top = 2.dp))
        } else if (quotaExceeded) {
            Text(text = strings.quotaReached, fontSize = 11.sp, color = Color(0xFFE57373), modifier = Modifier.padding(start = 20.dp, top = 2.dp))
        }
    }
}

@Composable
fun IntentionAndTimerDialog(app: AppItem, strings: Strings, delaySeconds: Int, onDismiss: () -> Unit, onConfirm: (Int, String) -> Unit) {
    var remainingDelay by remember { mutableStateOf(delaySeconds) }
    var intentionText by remember { mutableStateOf("") }
    var durationSlider by remember { mutableStateOf(if (app.tier == AppTier.RED) 10f else 15f) }
    val isValidIntention = remember(intentionText) { val c = intentionText.trim(); c.length >= 6 && c.toSet().size >= 3 }
    LaunchedEffect(Unit) { while (remainingDelay > 0) { delay(1000L); remainingDelay -= 1 } }
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF121212)).border(1.dp, Color(0xFF333333)).padding(24.dp)) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = app.label, style = MaterialTheme.typography.titleLarge)
                if (remainingDelay > 0) Text(text = "${strings.preSessionDelayText} ${remainingDelay}s...", fontSize = 14.sp, color = Color.Gray) else {
                    OutlinedTextField(value = intentionText, onValueChange = { v: String -> intentionText = v }, label = { Text(strings.intentionPrompt, fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (!isValidIntention) Text(text = strings.minCharsWarning, fontSize = 11.sp, color = Color(0xFFE57373))
                    Text(text = "${strings.selectDuration} ${durationSlider.toInt()}m", fontSize = 14.sp)
                    Slider(value = durationSlider, onValueChange = { v: Float -> durationSlider = v }, valueRange = 1f..(if (app.tier == AppTier.RED) 30f else 60f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(strings.cancel, color = Color.Gray) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(enabled = isValidIntention, onClick = { onConfirm(durationSlider.toInt(), intentionText.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text(strings.startSession) }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDrawer(
    strings: Strings, apps: List<AppItem>, prefs: SharedPreferences, hasPermission: Boolean,
    onToggleLanguage: () -> Unit, preSessionDelaySec: Int, onUpdateDelay: (Int) -> Unit,
    yellowCooldownMin: Int, onUpdateYellowCooldown: (Int) -> Unit, redCooldownMin: Int, onUpdateRedCooldown: (Int) -> Unit,
    focusModeEnabled: Boolean, onToggleFocus: (Boolean) -> Unit, widgetsEnabled: Boolean, onToggleWidgets: (Boolean) -> Unit, context: Context
) {
    var settingsUnlocked by remember { mutableStateOf(false) }
    var gateCountdown by remember { mutableStateOf(0) }
    LaunchedEffect(gateCountdown) { if (gateCountdown > 0) { delay(1000L); gateCountdown -= 1; if (gateCountdown == 0) settingsUnlocked = true } }
    val baselineDailyAvg = remember(hasPermission) { getPast7DayAverageMinutes(context) }
    var todayTotal = 0; for (app in apps) { todayTotal += app.usedMinutesToday }

    ModalDrawerSheet(drawerContainerColor = Color(0xFF0F0F0F), drawerContentColor = Color.White, modifier = Modifier.fillMaxWidth(0.88f)) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.settingsTitle, style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onToggleLanguage) { Text(strings.languageToggle, color = Color.White, fontSize = 12.sp) }
                }
                HorizontalDivider(color = Color(0xFF262626))
            }
            if (!hasPermission) {
                item {
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373), contentColor = Color.Black), modifier = Modifier.fillMaxWidth()) {
                        Text(strings.grantPermission, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF262626)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.endOfDayReport, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${strings.beforeAfterComparison} ${baselineDailyAvg}m -> ${todayTotal}m", fontSize = 13.sp, color = Color.LightGray)
                    val delta = baselineDailyAvg - todayTotal
                    Text(if (delta >= 0) "-${delta}m saved today" else "+${-delta}m over average", fontSize = 12.sp, color = if (delta >= 0) Color(0xFF81C784) else Color(0xFFE57373))
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.focusMode, fontSize = 13.sp); Switch(checked = focusModeEnabled, onCheckedChange = onToggleFocus)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.enableWidgets, fontSize = 13.sp); Switch(checked = widgetsEnabled, onCheckedChange = onToggleWidgets)
                }
            }
            item {
                if (!settingsUnlocked) {
                    Button(onClick = { if (gateCountdown == 0) gateCountdown = 5 }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222), contentColor = Color.White)) {
                        Text(if (gateCountdown > 0) "${strings.gatePrompt} (${gateCountdown}s)" else strings.unlockSettings)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${strings.delaySetting} ${preSessionDelaySec}s", fontSize = 13.sp)
                        Slider(value = preSessionDelaySec.toFloat(), onValueChange = { v: Float -> onUpdateDelay(v.toInt()) }, valueRange = 3f..15f)
                        Text("${strings.yellowCooldownSetting} ${yellowCooldownMin}m", fontSize = 13.sp)
                        Slider(value = yellowCooldownMin.toFloat(), onValueChange = { v: Float -> onUpdateYellowCooldown(v.toInt()) }, valueRange = 1f..30f)
                        Text("${strings.redCooldownSetting} ${redCooldownMin}m", fontSize = 13.sp)
                        Slider(value = redCooldownMin.toFloat(), onValueChange = { v: Float -> onUpdateRedCooldown(v.toInt()) }, valueRange = 5f..60f)
                    }
                }
            }
            if (settingsUnlocked) {
                items(apps, key = { item: AppItem -> "setting_${item.packageName}" }) { appItem: AppItem ->
                    var currentTier by remember { mutableStateOf(appItem.tier) }
                    var currentQuota by remember { mutableStateOf(appItem.dailyQuotaMinutes) }
                    Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF222222)).padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(appItem.label, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (tierOption in AppTier.values()) {
                                    val selected = currentTier == tierOption
                                    Text(
                                        text = tierOption.name.first().toString(), fontSize = 12.sp, color = if (selected) Color.Black else Color.Gray,
                                        modifier = Modifier.background(if (selected) Color.White else Color.Transparent).border(1.dp, Color.DarkGray)
                                            .clickable { currentTier = tierOption; appItem.tier = tierOption; prefs.edit().putString("tier_${appItem.packageName}", tierOption.name).apply() }.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(visible = currentTier == AppTier.RED) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text(text = "Daily Quota: ${if (currentQuota == 0) "Off" else "${currentQuota}m"}", fontSize = 11.sp, color = Color.Gray)
                                Slider(value = currentQuota.toFloat(), onValueChange = { newQ: Float -> currentQuota = newQ.toInt(); appItem.dailyQuotaMinutes = newQ.toInt(); prefs.edit().putInt("quota_${appItem.packageName}", newQ.toInt()).apply() }, valueRange = 0f..120f)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun bringLauncherToFront(context: Context) {
    val homeIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
    context.startActivity(homeIntent)
}

fun launchPackage(context: Context, packageName: String) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (launchIntent != null) { launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS); context.startActivity(launchIntent) }
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}

fun getCurrentForegroundPackage(context: Context): String? {
    if (!hasUsageStatsPermission(context)) return null
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val end = System.currentTimeMillis()
    val events = usm.queryEvents(end - 10000L, end)
    val event = UsageEvents.Event()
    var lastPkg: String? = null
    while (events.hasNextEvent()) { events.getNextEvent(event); if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) lastPkg = event.packageName }
    return lastPkg
}

fun getTodayUsageMinutes(context: Context): Map<String, Int> {
    if (!hasUsageStatsPermission(context)) return emptyMap()
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis()) ?: return emptyMap()
    val resultMap = mutableMapOf<String, Int>()
    for (stat in stats) { resultMap[stat.packageName] = (stat.totalTimeInForeground / 60000L).toInt() }
    return resultMap
}

fun getPast7DayAverageMinutes(context: Context): Int {
    if (!hasUsageStatsPermission(context)) return 0
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val end = System.currentTimeMillis()
    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - (7L * 24L * 60L * 60L * 1000L), end) ?: return 0
    var totalMinutes = 0
    for (stat in stats) { totalMinutes += (stat.totalTimeInForeground / 60000L).toInt() }
    return totalMinutes / 7
}

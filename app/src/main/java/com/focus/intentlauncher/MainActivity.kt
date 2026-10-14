package com.minimal.regulatedlauncher

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.util.*

enum class AppTier { GREEN, YELLOW, RED }

data class AppItem(
    val packageName: String,
    val label: String,
    var tier: AppTier = AppTier.GREEN,
    var dailyQuotaMinutes: Int = 0,
    var usedMinutesToday: Int = 0,
    var cooldownUntilMillis: Long = 0L
)

data class Strings(
    val searchPlaceholder: String,
    val settingsTitle: String,
    val intentionPrompt: String,
    val preSessionDelayText: String,
    val selectDuration: String,
    val startSession: String,
    val cancel: String,
    val quotaReached: String,
    val inCooldown: String,
    val focusMode: String,
    val usageHistory: String,
    val endOfDayReport: String,
    val gatePrompt: String,
    val unlockSettings: String,
    val cooldownSetting: String,
    val delaySetting: String,
    val enableWidgets: String,
    val languageToggle: String,
    val beforeAfterComparison: String
)

val EnglishStrings = Strings(
    searchPlaceholder = "Search apps...",
    settingsTitle = "Settings & Regulation",
    intentionPrompt = "State your intention for opening this app:",
    preSessionDelayText = "Take a breath. Starting in",
    selectDuration = "Session duration (minutes):",
    startSession = "Begin Session",
    cancel = "Cancel",
    quotaReached = "Daily quota reached. Locked until tomorrow.",
    inCooldown = "App suspended. Cooldown active:",
    focusMode = "Focus Mode (Silence Notifications)",
    usageHistory = "Before & After Usage Impact",
    endOfDayReport = "End-of-Day Report",
    gatePrompt = "Hold or wait to unlock critical settings...",
    unlockSettings = "Unlock Settings (5s delay)",
    cooldownSetting = "Red Tier Cooldown (min):",
    delaySetting = "Pre-session Delay (sec):",
    enableWidgets = "Show Home Screen Widget",
    languageToggle = "العربية (Arabic)",
    beforeAfterComparison = "Past Avg vs Today:"
)

val ArabicStrings = Strings(
    searchPlaceholder = "ابحث عن التطبيقات...",
    settingsTitle = "الإعدادات والتنظيم",
    intentionPrompt = "حدد نيتك لفتح هذا التطبيق:",
    preSessionDelayText = "خذ نفساً عميقاً. يبدأ خلال",
    selectDuration = "مدة الجلسة (بالدقائق):",
    startSession = "ابدأ الجلسة",
    cancel = "إلغاء",
    quotaReached = "تم استنفاد الحصة اليومية. مغلق حتى الغد.",
    inCooldown = "التطبيق معلق. فترة التهدئة نشطة:",
    focusMode = "وضع التركيز (كتم الإشعارات)",
    usageHistory = "مقارنة الاستخدام (قبل وبعد)",
    endOfDayReport = "تقرير نهاية اليوم",
    gatePrompt = "انتظر لفتح الإعدادات الحساسة...",
    unlockSettings = "فتح الإعدادات (تأخير 5 ثوانٍ)",
    cooldownSetting = "فترة التهدئة للفئة الحمراء (دقيقة):",
    delaySetting = "تأخير ما قبل الجلسة (ثانية):",
    enableWidgets = "إظهار الويدجت على الشاشة",
    languageToggle = "English (الإنجليزية)",
    beforeAfterComparison = "المعدل السابق مقابل اليوم:"
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
            MinimalLauncherTheme {
                LauncherScreen(
                    context = this,
                    prefs = prefs,
                    appWidgetManager = appWidgetManager,
                    appWidgetHost = appWidgetHost
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appWidgetHost.stopListening()
    }
}

@Composable
fun MinimalLauncherTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color.White,
        onPrimary = Color.Black,
        background = Color(0xFF0A0A0A),
        onBackground = Color(0xFFEAEAEA),
        surface = Color(0xFF141414),
        onSurface = Color(0xFFEAEAEA)
    )
    MaterialTheme(
        colorScheme = darkColorScheme,
        typography = Typography(
            bodyLarge = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = Color(0xFFEAEAEA)
            ),
            titleLarge = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light,
                fontSize = 22.sp,
                color = Color.White
            )
        ),
        content = content
    )
}

@Composable
fun LauncherScreen(
    context: Context,
    prefs: SharedPreferences,
    appWidgetManager: AppWidgetManager,
    appWidgetHost: AppWidgetHost
) {
    var isArabic by remember { mutableStateOf(prefs.getBoolean("is_arabic", false)) }
    val strings = if (isArabic) ArabicStrings else EnglishStrings
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    val focusManager = LocalFocusManager.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var preSessionDelaySec by remember { mutableStateOf(prefs.getInt("delay_sec", 4)) }
    var redCooldownMin by remember { mutableStateOf(prefs.getInt("cooldown_min", 15)) }
    var focusModeEnabled by remember { mutableStateOf(prefs.getBoolean("focus_mode", false)) }
    var widgetsEnabled by remember { mutableStateOf(prefs.getBoolean("widgets_enabled", false)) }
    var activeWidgetId by remember { mutableStateOf(prefs.getInt("active_widget_id", -1)) }

    var searchQuery by remember { mutableStateOf("") }

    val apps = remember { mutableStateListOf<AppItem>() }
    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val usageStats = getTodayUsageMinutes(context)

        apps.clear()
        resolveInfos.forEach { info ->
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName) {
                val label = info.loadLabel(pm).toString()
                val savedTier = AppTier.valueOf(prefs.getString("tier_$pkg", AppTier.GREEN.name)!!)
                val quota = prefs.getInt("quota_$pkg", 0)
                val cooldown = prefs.getLong("cooldown_$pkg", 0L)
                val usedToday = usageStats[pkg] ?: 0
                apps.add(AppItem(pkg, label, savedTier, quota, usedToday, cooldown))
            }
        }
        apps.sortBy { it.label.lowercase(Locale.getDefault()) }
    }

    var selectedAppForPrompt by remember { mutableStateOf<AppItem?>(null) }
    var activeSessionApp by remember { mutableStateOf<AppItem?>(null) }
    var sessionRemainingSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(activeSessionApp, sessionRemainingSeconds) {
        if (activeSessionApp != null && sessionRemainingSeconds > 0) {
            delay(1000L)
            sessionRemainingSeconds -= 1
            if (sessionRemainingSeconds == 0) {
                val expiredApp = activeSessionApp!!
                if (expiredApp.tier == AppTier.RED) {
                    val cooldownUntil = System.currentTimeMillis() + (redCooldownMin * 60 * 1000L)
                    expiredApp.cooldownUntilMillis = cooldownUntil
                    prefs.edit().putLong("cooldown_${expiredApp.packageName}", cooldownUntil).apply()
                }
                activeSessionApp = null
                val homeIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(homeIntent)
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                SettingsDrawer(
                    strings = strings,
                    apps = apps,
                    prefs = prefs,
                    isArabic = isArabic,
                    onToggleLanguage = {
                        isArabic = !isArabic
                        prefs.edit().putBoolean("is_arabic", isArabic).apply()
                    },
                    preSessionDelaySec = preSessionDelaySec,
                    onUpdateDelay = {
                        preSessionDelaySec = it
                        prefs.edit().putInt("delay_sec", it).apply()
                    },
                    redCooldownMin = redCooldownMin,
                    onUpdateCooldown = {
                        redCooldownMin = it
                        prefs.edit().putInt("cooldown_min", it).apply()
                    },
                    focusModeEnabled = focusModeEnabled,
                    onToggleFocus = {
                        focusModeEnabled = it
                        prefs.edit().putBoolean("focus_mode", it).apply()
                    },
                    widgetsEnabled = widgetsEnabled,
                    onToggleWidgets = {
                        widgetsEnabled = it
                        prefs.edit().putBoolean("widgets_enabled", it).apply()
                    },
                    context = context
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                        focusManager.clearFocus()
                    }
                    .padding(horizontal = 24.dp, vertical = 36.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 32.sp
                    )
                    if (activeSessionApp != null) {
                        Text(
                            text = "${activeSessionApp?.label}: ${sessionRemainingSeconds / 60}m ${sessionRemainingSeconds % 60}s",
                            fontSize = 12.sp,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (widgetsEnabled && activeWidgetId != -1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .border(1.dp, Color(0xFF2A2A2A))
                            .padding(8.dp)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val info = appWidgetManager.getAppWidgetInfo(activeWidgetId)
                                appWidgetHost.createView(ctx, activeWidgetId, info)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.searchPlaceholder, fontSize = 14.sp, color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0xFF333333),
                        cursorColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                val filteredApps = apps.filter {
                    it.label.contains(searchQuery, ignoreCase = true)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            strings = strings,
                            onClick = {
                                focusManager.clearFocus()
                                handleAppClick(
                                    app = app,
                                    context = context,
                                    onRequirePrompt = { selectedAppForPrompt = it }
                                )
                            }
                        )
                    }
                }
            }

            if (selectedAppForPrompt != null) {
                IntentionAndTimerDialog(
                    app = selectedAppForPrompt!!,
                    strings = strings,
                    delaySeconds = preSessionDelaySec,
                    onDismiss = { selectedAppForPrompt = null },
                    onConfirm = { durationMinutes, _ ->
                        val targetApp = selectedAppForPrompt!!
                        selectedAppForPrompt = null
                        activeSessionApp = targetApp
                        sessionRemainingSeconds = durationMinutes * 60
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
    val inCooldown = app.tier == AppTier.RED && now < app.cooldownUntilMillis
    val quotaExceeded = app.tier == AppTier.RED && app.dailyQuotaMinutes > 0 && app.usedMinutesToday >= app.dailyQuotaMinutes

    val tierIndicatorColor = when (app.tier) {
        AppTier.GREEN -> Color(0xFF4CAF50)
        AppTier.YELLOW -> Color(0xFFFFC107)
        AppTier.RED -> Color(0xFFF44336)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !inCooldown && !quotaExceeded) { onClick() }
            .padding(vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(tierIndicatorColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = app.label,
                    color = if (inCooldown || quotaExceeded) Color.DarkGray else Color.White,
                    fontSize = 16.sp
                )
            }

            if (app.tier == AppTier.RED && app.dailyQuotaMinutes > 0) {
                Text(
                    text = "${app.usedMinutesToday}/${app.dailyQuotaMinutes}m",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        if (inCooldown) {
            val remainingMin = ((app.cooldownUntilMillis - now) / 60000).coerceAtLeast(1)
            Text(
                text = "${strings.inCooldown} ${remainingMin}m",
                fontSize = 11.sp,
                color = Color(0xFFE57373),
                modifier = Modifier.padding(start = 20.dp, top = 2.dp)
            )
        } else if (quotaExceeded) {
            Text(
                text = strings.quotaReached,
                fontSize = 11.sp,
                color = Color(0xFFE57373),
                modifier = Modifier.padding(start = 20.dp, top = 2.dp)
            )
        }
    }
}

@Composable
fun IntentionAndTimerDialog(
    app: AppItem,
    strings: Strings,
    delaySeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var remainingDelay by remember { mutableStateOf(delaySeconds) }
    var intentionText by remember { mutableStateOf("") }
    var durationSlider by remember { mutableStateOf(if (app.tier == AppTier.RED) 10f else 15f) }

    LaunchedEffect(Unit) {
        while (remainingDelay > 0) {
            delay(1000L)
            remainingDelay -= 1
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414))
                .border(1.dp, Color(0xFF333333))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = app.label, style = MaterialTheme.typography.titleLarge)

            if (remainingDelay > 0) {
                Text(
                    text = "${strings.preSessionDelayText} ${remainingDelay}s...",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            } else {
                OutlinedTextField(
                    value = intentionText,
                    onValueChange = { intentionText = it },
             
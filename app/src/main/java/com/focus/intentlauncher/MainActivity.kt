package com.focus.intentlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var mainColumn: LinearLayout
    private lateinit var appsContainer: LinearLayout
    private lateinit var thoughtsContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var modeBtn: Button

    private var windowManager: WindowManager? = null
    private var pillView: TextView? = null
    private var blockerView: LinearLayout? = null
    private var activeTimer: CountDownTimer? = null

    data class AppEntry(val label: String, val pkg: String)
    private var installedApps = listOf<AppEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("intent_launcher_prefs", Context.MODE_PRIVATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        loadApps()
        buildHomeUi()
        checkOverlayPermission()
    }

    override fun onResume() {
        super.onResume()
        if (::searchInput.isInitialized) {
            searchInput.setText("")
            refreshAppList("")
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Enable 'Appear on top' for the sticky timer & blocker", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        installedApps = pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    private fun buildHomeUi() {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFillViewport = true
        }

        val outerWrapper = FrameLayout(this).apply {
            setPadding(dp(24), dp(48), dp(24), dp(48))
        }

        // Centered container (max 520dp so it looks clean on both phone and tablet)
        mainColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                dp(520).coerceAtMost(resources.displayMetrics.widthPixels - dp(48)),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        }

        // Header Prompt
        val title = TextView(this).apply {
            text = "What is your intention right now?"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(20))
        }
        mainColumn.addView(title)

        // 1. Brain Dump / Intrusive Thought Parking Lot
        val thoughtInput = EditText(this).apply {
            hint = "Park an intrusive thought here & press Enter..."
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                    val text = v.text.toString().trim()
                    if (text.isNotEmpty()) {
                        saveThought(text)
                        v.text = ""
                    }
                    true
                } else false
            }
        }
        mainColumn.addView(thoughtInput)

        thoughtsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(24))
        }
        mainColumn.addView(thoughtsContainer)
        renderThoughts()

        // 2. Mode Toggle Button (Strict Prompt Only vs. Prompt + Harmless Shortcuts)
        modeBtn = Button(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setOnClickListener {
                val nextMode = !isStrictMode()
                prefs.edit().putBoolean("strict_mode", nextMode).apply()
                updateModeButtonText()
                refreshAppList(searchInput.text.toString())
            }
        }
        updateModeButtonText()
        mainColumn.addView(modeBtn)

        // 3. Search / Command Bar
        searchInput = EditText(this).apply {
            hint = "Type the app you need (Long-press any app to set Green/Yellow)..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#181818"))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = refreshAppList(s.toString())
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        val searchParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12); bottomMargin = dp(16) }
        mainColumn.addView(searchInput, searchParams)

        // 4. App List Container
        appsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mainColumn.addView(appsContainer)

        outerWrapper.addView(mainColumn)
        rootScroll.addView(outerWrapper)
        setContentView(rootScroll)

        refreshAppList("")
    }

    private fun isStrictMode(): Boolean = prefs.getBoolean("strict_mode", false)

    private fun updateModeButtonText() {
        modeBtn.text = if (isStrictMode()) {
            "MODE: Strict Prompt Only (Tap to show Green shortcuts)"
        } else {
            "MODE: Prompt + Green Shortcuts (Tap for Strict Mode)"
        }
    }

    private fun isGreenApp(pkg: String): Boolean {
        val greens = prefs.getStringSet("green_apps", setOf()) ?: setOf()
        return greens.contains(pkg)
    }

    private fun toggleGreenApp(pkg: String, label: String) {
        val greens = (prefs.getStringSet("green_apps", setOf()) ?: setOf()).toMutableSet()
        if (greens.contains(pkg)) {
            greens.remove(pkg)
            Toast.makeText(this, "$label set to YELLOW (Requires Justification)", Toast.LENGTH_SHORT).show()
        } else {
            greens.add(pkg)
            Toast.makeText(this, "$label set to GREEN (Harmless / Instant)", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putStringSet("green_apps", greens).apply()
        refreshAppList(searchInput.text.toString())
    }

    private fun refreshAppList(query: String) {
        appsContainer.removeAllViews()
        val cleanQuery = query.trim().lowercase()

        val listToShow = when {
            cleanQuery.isNotEmpty() -> installedApps.filter { it.label.lowercase().contains(cleanQuery) }
            isStrictMode() -> emptyList() // Strict Mode shows NO apps until you type what you want
            else -> installedApps.filter { isGreenApp(it.pkg) } // Shortcut Mode shows only Green apps
        }

        if (cleanQuery.isEmpty() && !isStrictMode() && listToShow.isEmpty()) {
            val emptyHint = TextView(this).apply {
                text = "No Green apps saved yet. Search any harmless app above and long-press it to pin it here."
                setTextColor(Color.DKGRAY)
                textSize = 13f
            }
            appsContainer.addView(emptyHint)
            return
        }

        for (app in listToShow) {
            val green = isGreenApp(app.pkg)
            val row = TextView(this).apply {
                text = if (green) "● ${app.label}" else "○ ${app.label} [Intent Required]"
                textSize = 17f
                typeface = Typeface.MONOSPACE
                setTextColor(if (green) Color.parseColor("#81C784") else Color.parseColor("#FFB74D"))
                setPadding(dp(12), dp(14), dp(12), dp(14))
                setOnClickListener {
                    if (green) launchPackage(app.pkg) else showIntentGateDialog(app)
                }
                setOnLongClickListener {
                    toggleGreenApp(app.pkg, app.label)
                    true
                }
            }
            appsContainer.addView(row)
        }
    }

    private fun showIntentGateDialog(app: AppEntry) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        val reasonField = EditText(this).apply {
            hint = "Why do you need ${app.label} right now?"
        }
        box.addView(reasonField)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Unlocking ${app.label}")
            .setView(box)
            .setNegativeButton("Cancel (It's a trap)", null)
            .create()

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }

        listOf(2, 5, 15).forEach { mins ->
            val b = Button(this).apply {
                text = "${mins}m"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    val why = reasonField.text.toString().trim()
                    if (why.isEmpty()) {
                        Toast.makeText(context, "Type your reason first.", Toast.LENGTH_SHORT).show()
                    } else {
                        dialog.dismiss()
                        startMonitoredSession(app, why, mins)
                    }
                }
            }
            btnRow.addView(b)
        }
        box.addView(btnRow)
        dialog.show()
    }

    private fun startMonitoredSession(app: AppEntry, reason: String, minutes: Int) {
        showStickyTopPill(reason, minutes * 60 * 1000L, app)
        launchPackage(app.pkg)
    }

    private fun showStickyTopPill(reason: String, durationMs: Long, app: AppEntry) {
        clearOverlays()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        pillView = TextView(this).apply {
            setBackgroundColor(Color.parseColor("#CC111111"))
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }

        windowManager?.addView(pillView, params)

        activeTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(left: Long) {
                val m = (left / 1000) / 60
                val s = (left / 1000) % 60
                pillView?.text = "GOAL: $reason  |  ${m}:${if (s < 10) "0$s" else s}"
            }

            override fun onFinish() {
                showFullScreenBlocker(reason, app)
            }
        }.start()
    }

    private fun showFullScreenBlocker(reason: String, app: AppEntry) {
        clearOverlays()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        blockerView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F5000000"))
            setPadding(dp(32), dp(32), dp(32), dp(32))

            addView(TextView(context).apply {
                text = "TIME IS UP.\n\nYour stated goal was:\n\"$reason\"\n\nDid you finish what you came for?"
                textSize = 18f
                setTextColor(Color.WHITE)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(24))
            })

            addView(Button(context).apply {
                text = "Yes — Close App & Go Home"
                setBackgroundColor(Color.parseColor("#2E7D32"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    clearOverlays()
                    val home = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(home)
                }
            })

            addView(Button(context).apply {
                text = "Need 2 final minutes"
                setOnClickListener {
                    showStickyTopPill(reason, 2 * 60 * 1000L, app)
                }
            })
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager?.addView(blockerView, params)
    }

    private fun clearOverlays() {
        activeTimer?.cancel()
        pillView?.let { runCatching { windowManager?.removeView(it) } }
        blockerView?.let { runCatching { windowManager?.removeView(it) } }
        pillView = null
        blockerView = null
    }

    private fun launchPackage(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
    }

    private fun saveThought(thought: String) {
        val list = (prefs.getStringSet("thoughts", setOf()) ?: setOf()).toMutableSet()
        list.add("${System.currentTimeMillis()}|$thought")
        prefs.edit().putStringSet("thoughts", list).apply()
        renderThoughts()
    }

    private fun renderThoughts() {
        thoughtsContainer.removeAllViews()
        val raw = (prefs.getStringSet("thoughts", setOf()) ?: setOf()).sorted()
        raw.forEach { entry ->
            val label = entry.substringAfter("|")
            val item = TextView(this).apply {
                text = "• $label  [tap to clear]"
                setTextColor(Color.GRAY)
                textSize = 13f
                setPadding(0, dp(4), 0, dp(4))
                setOnClickListener {
                    val updated = (prefs.getStringSet("thoughts", setOf()) ?: setOf()).toMutableSet()
                    updated.remove(entry)
                    prefs.edit().putStringSet("thoughts", updated).apply()
                    renderThoughts()
                }
            }
            thoughtsContainer.addView(item)
        }
    }
}

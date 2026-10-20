package io.github.gdevenyi.claudeusage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Locale
import kotlin.random.Random

/**
 * Always-on screen for a spare phone: a large clock beside the widget's
 * detailed layout, landscape, screen kept on. Built from plain views since
 * Glance only renders into an app widget host.
 */
class ClockActivity : AppCompatActivity() {

    // Follow system, dark, light — cycled by the header button.
    private val nightModes = intArrayOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        AppCompatDelegate.MODE_NIGHT_YES,
        AppCompatDelegate.MODE_NIGHT_NO,
    )

    private val handler = Handler(Looper.getMainLooper())

    // Field: prefs hold listeners weakly, so an inline lambda would be collected.
    private val onPrefChange =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render() }

    // ACTION_BATTERY_CHANGED is sticky: registering hands back the current state.
    private val onBattery = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = showBattery(intent)
    }

    private val everyMinute = object : Runnable {
        override fun run() {
            render()
            nudge()
            // On the minute, so the countdowns move with the clock.
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = nightModes[Store(this).clockTheme]
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clock)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Paint behind the notch too, instead of a black letterbox strip, and
        // keep the content clear of it.
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        val content = findViewById<View>(R.id.content)
        val pad = content.paddingLeft
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.setPadding(pad + cut.left, pad + cut.top, pad + cut.right, pad + cut.bottom)
            insets
        }
        hideBars()

        val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMdEEEE")
        findViewById<TextClock>(R.id.date).apply {
            format12Hour = datePattern
            format24Hour = datePattern
        }

        findViewById<View>(R.id.refreshBtn).setOnClickListener { RefreshWorker.refreshNow(this) }
        findViewById<View>(R.id.themeBtn).setOnClickListener {
            val store = Store(this)
            store.clockTheme = (store.clockTheme + 1) % nightModes.size
            delegate.localNightMode = nightModes[store.clockTheme]
        }
        // Long press, so a stray touch on an always-on screen doesn't leave it.
        findViewById<View>(R.id.panel).setOnLongClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            true
        }
    }

    override fun onStart() {
        super.onStart()
        val store = Store(this)
        store.prefs.registerOnSharedPreferenceChangeListener(onPrefChange)
        val age = System.currentTimeMillis() - (Usage.cached(this)?.fetchedAt ?: 0)
        if (store.loggedIn && age > store.intervalMin * 60_000L) RefreshWorker.refreshNow(this)
        registerReceiver(onBattery, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::showBattery)
        handler.removeCallbacks(everyMinute)
        everyMinute.run()
    }

    override fun onStop() {
        super.onStop()
        Store(this).prefs.unregisterOnSharedPreferenceChangeListener(onPrefChange)
        unregisterReceiver(onBattery)
        handler.removeCallbacks(everyMinute)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    private fun hideBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showBattery(intent: Intent) {
        val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (raw < 0 || scale <= 0) return
        val pct = raw * 100 / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        // Plugged in counts as charging too: some chargers report
        // NOT_CHARGING near the top.
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val full = status == BatteryManager.BATTERY_STATUS_FULL
        val charging = !full && (status == BatteryManager.BATTERY_STATUS_CHARGING || plugged)
        findViewById<BatteryView>(R.id.battery).set(pct, charging)
        findViewById<TextView>(R.id.batteryText).text = when {
            full && plugged -> "$pct% · ${getString(R.string.battery_full)}"
            charging -> "$pct% · ${getString(R.string.battery_charging)}"
            else -> "$pct%"
        }
    }

    /** Shift everything a few dp each minute so nothing burns into the panel. */
    private fun nudge() {
        val px = 6 * resources.displayMetrics.density
        findViewById<View>(R.id.content).apply {
            translationX = Random.nextFloat() * 2 * px - px
            translationY = Random.nextFloat() * 2 * px - px
        }
    }

    private fun render() {
        val store = Store(this)
        val d = if (store.loggedIn) Usage.cached(this) else null
        findViewById<TextView>(R.id.plan).apply {
            text = store.plan
            visibility = if (store.plan.isEmpty()) View.GONE else View.VISIBLE
        }

        val hint = when {
            !store.loggedIn || store.authBroken -> "Open the app to log in\n(long-press here)"
            d == null -> "Waiting for the first refresh"
            else -> null
        }
        findViewById<TextView>(R.id.hint).apply {
            text = hint
            visibility = if (hint == null) View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.usageBody).visibility = if (hint == null) View.VISIBLE else View.GONE
        d ?: return

        val preds = History.predictions(this)
        bar(R.id.session, "Session (5h)", d.session, false, preds["session"])
        bar(R.id.weekly, "Weekly (7d)", d.weekly, true, preds["weekly"])

        val rows = findViewById<LinearLayout>(R.id.modelRows)
        rows.removeAllViews()
        d.scoped.take(2).forEach { s ->
            val row = layoutInflater.inflate(R.layout.clock_model_row, rows, false)
            val color = getColor(Severity.colorRes(s.window))
            // A model limit can reset apart from the weekly one; only then is
            // its own reset worth showing.
            val own = Fmt.ownReset(s.window.resetsAt, d.weekly?.resetsAt)
            row.findViewById<TextView>(R.id.name).text = Fmt.label(s.name, s.window) +
                (own?.let { "  resets ${Fmt.reset(it, true)}" } ?: "")
            row.findViewById<LinearProgressIndicator>(R.id.bar).apply {
                progress = s.window.pctInt.coerceAtMost(100)
                setIndicatorColor(color)
            }
            row.findViewById<TextView>(R.id.pct).apply {
                text = "${s.window.pctInt}%"
                setTextColor(color)
            }
            rows.addView(row)
        }
        val modelVis = if (d.scoped.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.modelTitle).visibility = modelVis
        rows.visibility = modelVis

        val risk = d.scoped.firstOrNull { preds[it.name]?.atRisk == true }
        findViewById<TextView>(R.id.risk).apply {
            text = risk?.let { it.name + Fmt.outMark(preds[it.name], true) }
            visibility = if (risk == null) View.GONE else View.VISIBLE
        }

        findViewById<TextView>(R.id.updated).text =
            if (Fmt.stale(d.fetchedAt)) "Stale — updated ${Fmt.age(d.fetchedAt)}"
            else "Updated ${Fmt.age(d.fetchedAt)}"
    }

    private fun bar(blockId: Int, label: String, w: Usage.Window?, withDay: Boolean, pred: History.Prediction?) {
        val block = findViewById<View>(blockId)
        val pct = w?.pctInt ?: 0
        val color = getColor(Severity.colorRes(w))
        block.findViewById<TextView>(R.id.label).text = Fmt.label(label, w)
        block.findViewById<TextView>(R.id.pct).apply {
            text = "$pct%"
            setTextColor(color)
        }
        block.findViewById<LinearProgressIndicator>(R.id.bar).apply {
            progress = pct.coerceAtMost(100)
            setIndicatorColor(color)
        }
        block.findViewById<TextView>(R.id.reset).text =
            Fmt.resetLine(w?.resetsAt, withDay, detailed = true, Fmt.outMark(pred, withDay))
    }
}

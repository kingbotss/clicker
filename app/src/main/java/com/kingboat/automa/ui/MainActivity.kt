package com.kingboat.automa.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.kingboat.automa.R
import com.kingboat.automa.data.ClickRepository
import com.kingboat.automa.data.ClipboardStore
import com.kingboat.automa.databinding.ActivityMainBinding
import com.kingboat.automa.model.ClickPoint
import com.kingboat.automa.model.Profile
import com.kingboat.automa.service.ClickerAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: ClickRepository
    private lateinit var clipStore: ClipboardStore
    private lateinit var clipAdapter: ClipboardAdapter
    private lateinit var profileAdapter: ArrayAdapter<Profile>
    private var suppressSpinner = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = ClickRepository(this)
        clipStore = ClipboardStore(this)
        repo.ensureDefaultProfile()

        setupTabs()
        setupClickerPage()
        setupClipboardPage()
        setupSetupPage()
        requestNotificationsIfNeeded()
        showPage(0)
    }

    override fun onResume() {
        super.onResume()
        ClickerAccessibilityService.instance?.stateListener = { runOnUiThread { refreshStartButton() } }
        registerClipWatcher()
        captureClipIntoHistory()
        reloadProfiles()
        renderPoints()
        refreshStartButton()
        clipAdapter.submit(clipStore.getAll())
        refreshClipEmpty()
        refreshSetupStatus()
    }

    override fun onPause() {
        if (ClickerAccessibilityService.instance?.stateListener != null) {
            ClickerAccessibilityService.instance?.stateListener = null
        }
        unregisterClipWatcher()
        super.onPause()
    }

    // --- tabs ---

    private fun setupTabs() {
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showPage(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun showPage(index: Int) {
        binding.pageClicker.root.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.pageClipboard.root.visibility = if (index == 1) View.VISIBLE else View.GONE
        binding.pageSetup.root.visibility = if (index == 2) View.VISIBLE else View.GONE
        when (index) {
            0 -> { reloadProfiles(); renderPoints(); refreshStartButton() }
            1 -> { clipAdapter.submit(clipStore.getAll()); refreshClipEmpty() }
            2 -> refreshSetupStatus()
        }
    }

    // --- profiles ---

    private fun setupClickerPage() = with(binding.pageClicker) {
        profileAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item)
        profileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        profileSpinner.adapter = profileAdapter
        profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (suppressSpinner) return
                repo.setActiveProfileId(profileAdapter.getItem(pos)!!.id)
                renderPoints()
                refreshStartButton()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        btnNewProfile.setOnClickListener { promptProfileName(null) }
        btnRenameProfile.setOnClickListener {
            repo.getActiveProfile()?.let { promptProfileName(it) }
        }
        btnDeleteProfile.setOnClickListener { confirmDeleteProfile() }

        btnAddPoint.setOnClickListener { addPointFromInputs() }
        btnPickScreen.setOnClickListener { pickOnScreen() }
        btnStartStop.setOnClickListener { toggleClicking() }

        modeOverlay.setOnCheckedChangeListener { _, checked ->
            if (checked) { repo.controlMode = ClickRepository.MODE_OVERLAY; refreshControlsIfShown() }
        }
        modeNotification.setOnCheckedChangeListener { _, checked ->
            if (checked) { repo.controlMode = ClickRepository.MODE_NOTIFICATION; refreshControlsIfShown() }
        }
        btnShowOverlay.setOnClickListener { showControls() }
        btnHideOverlay.setOnClickListener { ClickerAccessibilityService.instance?.hideControls() }
    }

    private fun reloadProfiles() {
        val profiles = repo.getProfiles()
        val activeId = repo.getActiveProfileId()
        suppressSpinner = true
        profileAdapter.clear()
        profileAdapter.addAll(profiles)
        val idx = profiles.indexOfFirst { it.id == activeId }
        if (idx >= 0) binding.pageClicker.profileSpinner.setSelection(idx)
        suppressSpinner = false
        // Reflect the stored control mode.
        if (repo.controlMode == ClickRepository.MODE_NOTIFICATION) {
            binding.pageClicker.modeNotification.isChecked = true
        } else {
            binding.pageClicker.modeOverlay.isChecked = true
        }
    }

    private fun promptProfileName(existing: Profile?) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.profile_name_hint)
            setText(existing?.name ?: "")
        }
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.new_profile else R.string.rename_profile)
            .setView(pad(input))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (existing == null) repo.createProfile(name) else repo.renameProfile(existing.id, name)
                reloadProfiles()
                renderPoints()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteProfile() {
        if (isClickerRunning()) { toast(getString(R.string.stop_to_edit)); return }
        val active = repo.getActiveProfile() ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_profile)
            .setMessage("Delete “${active.name}” and its points?")
            .setPositiveButton(R.string.delete) { _, _ ->
                repo.deleteProfile(active.id)
                reloadProfiles()
                renderPoints()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // --- points ---

    private fun addPointFromInputs() = with(binding.pageClicker) {
        val x = inputX.text.toString().toIntOrNull()
        val y = inputY.text.toString().toIntOrNull()
        if (x == null || y == null || x < 0 || y < 0) { toast("Enter valid X and Y (>= 0)"); return }
        val delay = inputDelay.text.toString().toLongOrNull() ?: 500L
        if (delay < MIN_POINT_DELAY_MS) { toast("Delay must be >= $MIN_POINT_DELAY_MS ms"); return }
        val label = inputLabel.text.toString().trim().ifBlank {
            "P${repo.getActivePoints().size + 1}"
        }
        repo.addPoint(repo.getActiveProfileId(), x, y, delay, label)
        inputLabel.text = null; inputX.text = null; inputY.text = null; inputDelay.text = null
        renderPoints()
    }

    private fun renderPoints() {
        val container = binding.pageClicker.pointsContainer
        container.removeAllViews()
        val points = repo.getActivePoints()
        binding.pageClicker.pointsEmpty.visibility =
            if (points.isEmpty()) View.VISIBLE else View.GONE
        points.forEach { p ->
            val row = LayoutInflater.from(this).inflate(R.layout.row_point, container, false)
            row.findViewById<TextView>(R.id.point_text).text =
                getString(R.string.point_line, p.label, p.x, p.y, p.delayMs)
            row.setOnClickListener {
                if (isClickerRunning()) { toast(getString(R.string.stop_to_edit)); return@setOnClickListener }
                editPointDialog(p)
            }
            row.findViewById<Button>(R.id.btn_delete_point).setOnClickListener {
                if (isClickerRunning()) { toast(getString(R.string.stop_to_edit)); return@setOnClickListener }
                repo.deletePoint(p.id)
                renderPoints()
            }
            container.addView(row)
        }
        ClickerAccessibilityService.instance?.refreshOverlayPoints()
    }

    private fun editPointDialog(point: ClickPoint) {
        val labelInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.hint_label)
            setText(point.label)
        }
        val xInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.hint_x)
            setText(point.x.toString())
        }
        val yInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.hint_y)
            setText(point.y.toString())
        }
        val delayInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.hint_delay)
            setText(point.delayMs.toString())
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(labelInput)
            addView(xInput)
            addView(yInput)
            addView(delayInput)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.edit_point)
            .setView(pad(box))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val x = xInput.text.toString().toIntOrNull()
                val y = yInput.text.toString().toIntOrNull()
                if (x == null || y == null || x < 0 || y < 0) {
                    toast("Enter valid X and Y (>= 0)")
                    return@setPositiveButton
                }
                val delay = delayInput.text.toString().toLongOrNull() ?: point.delayMs
                if (delay < MIN_POINT_DELAY_MS) {
                    toast("Delay must be >= $MIN_POINT_DELAY_MS ms")
                    return@setPositiveButton
                }
                val label = labelInput.text.toString().trim().ifBlank {
                    "P${repo.getActivePoints().size + 1}"
                }
                repo.updatePoint(point.id, label, x, y, delay)
                renderPoints()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickOnScreen() {
        val svc = ClickerAccessibilityService.instance
        if (svc == null) { toast(getString(R.string.need_accessibility)); openAccessibilitySettings(); return }
        svc.startPicker()
        toast(getString(R.string.tap_to_add))
        moveTaskToBack(true) // let the user see the target over other content
    }

    private fun toggleClicking() {
        if (!isAccessibilityEnabled()) {
            toast(getString(R.string.need_accessibility)); openAccessibilitySettings(); return
        }
        if (repo.getActivePoints().isEmpty()) { toast("Add at least one point"); return }
        ClickerAccessibilityService.instance?.toggle()
        refreshStartButton()
    }

    private fun refreshStartButton() {
        val running = ClickerAccessibilityService.instance?.isRunning == true
        binding.pageClicker.btnStartStop.text =
            getString(if (running) R.string.stop else R.string.start)
    }

    private fun showControls() {
        val svc = ClickerAccessibilityService.instance
        if (svc == null) { toast(getString(R.string.need_accessibility)); openAccessibilitySettings(); return }
        svc.showControls()
        toast("Controls shown")
    }

    private fun refreshControlsIfShown() {
        ClickerAccessibilityService.instance?.let { if (it.controlsShown) it.showControls() }
    }

    // --- clipboard ---

    private fun setupClipboardPage() {
        clipAdapter = ClipboardAdapter(
            clipStore.getAll(),
            onCopy = { copyToClipboard(it); toast(getString(R.string.copied)) },
            onDelete = { text ->
                clipStore.removeValue(text)
                clipAdapter.submit(clipStore.getAll())
                refreshClipEmpty()
            },
        )
        binding.pageClipboard.clipList.layoutManager = LinearLayoutManager(this)
        binding.pageClipboard.clipList.adapter = clipAdapter
        binding.pageClipboard.btnClear.setOnClickListener {
            clipStore.clear()
            clipAdapter.submit(emptyList())
            refreshClipEmpty()
        }
    }

    private fun refreshClipEmpty() {
        val empty = if (::clipAdapter.isInitialized) {
            clipAdapter.itemCount == 0
        } else {
            clipStore.getAll().isEmpty()
        }
        binding.pageClipboard.clipEmpty.visibility =
            if (empty) View.VISIBLE else View.GONE
    }

    private val clipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    private var clipWatcher: ClipboardManager.OnPrimaryClipChangedListener? = null

    /** Capture the current clipboard while we are foreground. Android 10+ blocks
     *  background reads, so the accessibility service is best-effort only. */
    private fun captureClipIntoHistory() {
        val text = runCatching {
            val clip = clipboardManager.primaryClip ?: return
            if (clip.itemCount == 0) return
            clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        }.getOrNull() ?: return
        if (clipStore.add(text) && binding.pageClipboard.root.visibility == View.VISIBLE) {
            clipAdapter.submit(clipStore.getAll())
            refreshClipEmpty()
        }
    }

    private fun registerClipWatcher() {
        if (clipWatcher != null) return
        val w = ClipboardManager.OnPrimaryClipChangedListener { captureClipIntoHistory() }
        clipboardManager.addPrimaryClipChangedListener(w)
        clipWatcher = w
    }

    private fun unregisterClipWatcher() {
        clipWatcher?.let { clipboardManager.removePrimaryClipChangedListener(it) }
        clipWatcher = null
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("copied", text))
    }

    // --- setup (permissions + test) ---

    private fun setupSetupPage() = with(binding.pageSetup) {
        btnOpenAccessibility.setOnClickListener { openAccessibilitySettings() }
        btnTest.setOnClickListener { runTestTap() }
    }

    private fun runTestTap() {
        val svc = ClickerAccessibilityService.instance
        if (svc == null) {
            binding.pageSetup.testResult.text = getString(R.string.test_fail)
            openAccessibilitySettings()
            return
        }
        val m = resources.displayMetrics
        svc.testTap(m.widthPixels / 2, m.heightPixels / 2) { ok ->
            runOnUiThread {
                binding.pageSetup.testResult.text =
                    getString(if (ok) R.string.test_ok else R.string.test_fail)
            }
        }
    }

    private fun refreshSetupStatus() = with(binding.pageSetup) {
        statusAccessibility.text =
            getString(if (isAccessibilityEnabled()) R.string.status_on else R.string.status_off)
    }

    // --- helpers ---

    private fun isClickerRunning() = ClickerAccessibilityService.instance?.isRunning == true

    private fun isAccessibilityEnabled(): Boolean {
        val target = android.content.ComponentName(this, ClickerAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any {
            android.content.ComponentName.unflattenFromString(it) == target
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    /** Wraps a dialog view with padding so inputs aren't flush to the edges. */
    private fun pad(view: View): View {
        val pad = (16 * resources.displayMetrics.density).toInt()
        return LinearLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addView(view)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val MIN_POINT_DELAY_MS = 20L
    }
}

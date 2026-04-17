package com.example.overlayscreen

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged

class MainActivity : AppCompatActivity() {
    private lateinit var store: OverlayConfigStore
    private lateinit var themeAdapter: ArrayAdapter<String>
    private lateinit var backgroundAdapter: ArrayAdapter<String>
    private lateinit var dismissAnimationAdapter: ArrayAdapter<String>
    private lateinit var colorPresetAdapter: ArrayAdapter<String>
    private var bindingUi = false
    private var suppressSpinnerCallbacks = false

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            refreshUi()
            return@registerForActivityResult
        }
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        store.update {
            it.copy(
                backgroundUri = uri.toString(),
                selectedThemeKey = OverlayThemePreset.CUSTOM.key,
            )
        }
        pushConfigUpdate()
        refreshUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = OverlayConfigStore(this)

        findViewById<Button>(R.id.btnGrantOverlayPermission).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        }

        findViewById<Button>(R.id.btnToggleOverlay).setOnClickListener {
            if (OverlayService.isRunning) {
                startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
            } else {
                if (!Settings.canDrawOverlays(this)) {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                    return@setOnClickListener
                }
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START),
                )
            }
            refreshUi()
        }

        setupAdapters()
        bindMenus()
        bindSectionToggles()
        configureSliders()
        configureTrueOpaqueToggle()
        configureTextInput()
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun setupAdapters() {
        themeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            OverlayThemePreset.valuesList.map { it.title },
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        backgroundAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf(getString(R.string.no_builtin_background), getString(R.string.choose_custom_image)) +
                BuiltinBackgrounds.presets.map { it.title },
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        dismissAnimationAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            OverlayDismissAnimationMode.valuesList.map { it.title },
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        colorPresetAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            OverlayColorPresets.presets.map { it.title },
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        findViewById<Spinner>(R.id.spinnerTheme).adapter = themeAdapter
        findViewById<Spinner>(R.id.spinnerBuiltinBackground).adapter = backgroundAdapter
        findViewById<Spinner>(R.id.spinnerDismissAnimation).adapter = dismissAnimationAdapter
        findViewById<Spinner>(R.id.spinnerColorPreset).adapter = colorPresetAdapter
    }

    private fun bindMenus() {
        findViewById<Spinner>(R.id.spinnerTheme).onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (bindingUi || suppressSpinnerCallbacks) return
                    val theme = OverlayThemePreset.valuesList[position]
                    store.update { it.withTheme(theme) }
                    pushConfigUpdate()
                    refreshUi()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

        findViewById<Spinner>(R.id.spinnerBuiltinBackground).onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (bindingUi || suppressSpinnerCallbacks) return
                    when (position) {
                        0 -> {
                            store.update {
                                it.copy(
                                    backgroundUri = null,
                                    selectedThemeKey = OverlayThemePreset.CUSTOM.key,
                                )
                            }
                            pushConfigUpdate()
                            refreshUi()
                        }

                        1 -> imagePicker.launch(arrayOf("image/*"))

                        else -> {
                            val preset = BuiltinBackgrounds.presets[position - 2]
                            store.update {
                                it.copy(
                                    backgroundUri = BuiltinBackgrounds.encode(preset.key),
                                    selectedThemeKey = OverlayThemePreset.CUSTOM.key,
                                )
                            }
                            pushConfigUpdate()
                            refreshUi()
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

        findViewById<Spinner>(R.id.spinnerDismissAnimation).onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (bindingUi || suppressSpinnerCallbacks) return
                    store.update {
                        it.copy(dismissAnimationModeKey = OverlayDismissAnimationMode.valuesList[position].key)
                    }
                    pushConfigUpdate()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

        findViewById<Spinner>(R.id.spinnerColorPreset).onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (bindingUi || suppressSpinnerCallbacks) return
                    val preset = OverlayColorPresets.presets[position]
                    store.update {
                        it.copy(
                            color = preset.color,
                            selectedThemeKey = OverlayThemePreset.CUSTOM.key,
                        )
                    }
                    pushConfigUpdate()
                    refreshUi()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
    }

    private fun bindSectionToggles() {
        bindSection(
            headerId = R.id.headerMaskSection,
            bodyId = R.id.bodyMaskSection,
            toggleId = R.id.textMaskSectionToggle,
            initiallyExpanded = true,
        )
        bindSection(
            headerId = R.id.headerAppearanceSection,
            bodyId = R.id.bodyAppearanceSection,
            toggleId = R.id.textAppearanceSectionToggle,
            initiallyExpanded = true,
        )
    }

    private fun bindSection(
        headerId: Int,
        bodyId: Int,
        toggleId: Int,
        initiallyExpanded: Boolean,
    ) {
        val header = findViewById<View>(headerId)
        val body = findViewById<View>(bodyId)
        val toggle = findViewById<TextView>(toggleId)
        body.tag = initiallyExpanded
        updateSection(body, toggle, initiallyExpanded, animate = false)
        header.setOnClickListener {
            val expanded = !(body.tag as? Boolean ?: true)
            body.tag = expanded
            updateSection(body, toggle, expanded, animate = true)
        }
    }

    private fun updateSection(body: View, toggle: TextView, expanded: Boolean, animate: Boolean) {
        toggle.text = if (expanded) getString(R.string.collapse_label) else getString(R.string.expand_label)
        if (animate) {
            if (expanded) {
                body.visibility = View.VISIBLE
                body.alpha = 0f
                body.animate().alpha(1f).setDuration(160L).start()
            } else {
                body.animate().alpha(0f).setDuration(140L).withEndAction {
                    body.visibility = View.GONE
                    body.alpha = 1f
                }.start()
            }
        } else {
            body.visibility = if (expanded) View.VISIBLE else View.GONE
            body.alpha = 1f
        }
    }

    private fun configureSliders() {
        val opacitySeek = findViewById<SeekBar>(R.id.seekOpacity)
        val widthSeek = findViewById<SeekBar>(R.id.seekOverlayWidth)
        val topSeek = findViewById<SeekBar>(R.id.seekOverlayTop)
        val bottomSeek = findViewById<SeekBar>(R.id.seekOverlayBottom)
        val peekCountSeek = findViewById<SeekBar>(R.id.seekPeekCount)
        val toolbarOpacitySeek = findViewById<SeekBar>(R.id.seekToolbarOpacity)

        opacitySeek.max = OverlayOpacityPolicy.DISPLAY_MAX_PERCENT
        widthSeek.max = 70
        topSeek.max = 70
        bottomSeek.max = 70
        peekCountSeek.max = 3
        toolbarOpacitySeek.max = 85

        opacitySeek.setOnSeekBarChangeListener(configSeekListener { progress ->
            store.update {
                it.copy(
                    opacityPercent = OverlayOpacityPolicy.displayPercentToActual(
                        progress,
                        it.fullOpaqueMaskEnabled,
                    ),
                )
            }
        })
        widthSeek.setOnSeekBarChangeListener(configSeekListener { progress ->
            store.update { it.copy(overlayWidthPercent = progress + 30) }
        })
        topSeek.setOnSeekBarChangeListener(configSeekListener { progress ->
            store.update { it.withTopEdge(progress) }
        })
        bottomSeek.setOnSeekBarChangeListener(configSeekListener { progress ->
            store.update { it.withBottomEdge(progress + OverlayConfig.MIN_OVERLAY_HEIGHT_PERCENT) }
        })
        peekCountSeek.setOnSeekBarChangeListener(configSeekListener { progress ->
            store.update { it.withPeekWindowCount(progress + 1) }
        })
        toolbarOpacitySeek.setOnSeekBarChangeListener(configSeekListener { progress ->
            store.update { it.copy(controlsOpacityPercent = progress + 15) }
        })
    }

    private fun configureTrueOpaqueToggle() {
        findViewById<CheckBox>(R.id.checkTrueOpaqueMask).setOnCheckedChangeListener { _, isChecked ->
            if (bindingUi) return@setOnCheckedChangeListener
            store.update {
                it.copy(
                    fullOpaqueMaskEnabled = isChecked,
                    opacityPercent = OverlayOpacityPolicy.normalizeActualPercent(
                        it.opacityPercent,
                        isChecked,
                    ),
                )
            }
            pushConfigUpdate()
            refreshUi()
        }
    }

    private fun configSeekListener(onPersist: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || bindingUi) return
                onPersist(progress)
                pushConfigUpdate()
                refreshUi()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
    }

    private fun configureTextInput() {
        findViewById<EditText>(R.id.editOverlayText).doAfterTextChanged { editable ->
            if (bindingUi) return@doAfterTextChanged
            store.update { it.copy(customText = editable?.toString().orEmpty()) }
            pushConfigUpdate()
        }
    }

    private fun refreshUi() {
        bindingUi = true
        suppressSpinnerCallbacks = true
        val config = store.load()

        findViewById<TextView>(R.id.textPermissionStatus).text =
            if (Settings.canDrawOverlays(this)) {
                getString(R.string.overlay_permission_granted)
            } else {
                getString(R.string.overlay_permission_missing)
            }

        findViewById<TextView>(R.id.textOverlayStatus).text =
            if (OverlayService.isRunning) getString(R.string.overlay_is_running) else getString(R.string.overlay_is_stopped)

        findViewById<Button>(R.id.btnToggleOverlay).text =
            if (OverlayService.isRunning) getString(R.string.stop_overlay) else getString(R.string.start_overlay)

        findViewById<TextView>(R.id.textOpacityValue).text =
            getString(
                R.string.percent_value,
                OverlayOpacityPolicy.actualPercentToDisplay(
                    config.opacityPercent,
                    config.fullOpaqueMaskEnabled,
                ),
            )
        findViewById<TextView>(R.id.textWidthValue).text =
            getString(R.string.percent_value, config.overlayWidthPercent)
        findViewById<TextView>(R.id.textTopValue).text =
            getString(R.string.percent_value, config.overlayTopPercent)
        findViewById<TextView>(R.id.textBottomValue).text =
            getString(R.string.percent_value, config.overlayBottomPercent)
        findViewById<TextView>(R.id.textPeekCountValue).text =
            getString(R.string.peek_count_value, config.peekWindowCount)
        findViewById<TextView>(R.id.textToolbarOpacityValue).text =
            getString(R.string.percent_value, config.controlsOpacityPercent)

        findViewById<SeekBar>(R.id.seekOpacity).progress =
            OverlayOpacityPolicy.actualPercentToDisplay(
                config.opacityPercent,
                config.fullOpaqueMaskEnabled,
            )
        findViewById<SeekBar>(R.id.seekOverlayWidth).progress = config.overlayWidthPercent - 30
        findViewById<SeekBar>(R.id.seekOverlayTop).progress = config.overlayTopPercent
        findViewById<SeekBar>(R.id.seekOverlayBottom).progress =
            config.overlayBottomPercent - OverlayConfig.MIN_OVERLAY_HEIGHT_PERCENT
        findViewById<SeekBar>(R.id.seekPeekCount).progress = config.peekWindowCount - 1
        findViewById<SeekBar>(R.id.seekToolbarOpacity).progress = config.controlsOpacityPercent - 15
        findViewById<CheckBox>(R.id.checkTrueOpaqueMask).isChecked = config.fullOpaqueMaskEnabled

        findViewById<TextView>(R.id.textColorValue).text = String.format("#%06X", 0xFFFFFF and config.color)
        findViewById<View>(R.id.viewColorPreview).setBackgroundColor(config.color)

        val editText = findViewById<EditText>(R.id.editOverlayText)
        if (editText.text?.toString() != config.customText) {
            editText.setText(config.customText)
            editText.setSelection(editText.text?.length ?: 0)
        }

        findViewById<TextView>(R.id.textBackgroundValue).text =
            config.backgroundUri?.let(::resolveDisplayName) ?: getString(R.string.no_background_selected)

        findViewById<Spinner>(R.id.spinnerTheme).setSelection(
            OverlayThemePreset.valuesList.indexOfFirst { it.key == config.selectedThemeKey }.coerceAtLeast(0),
            false,
        )
        findViewById<Spinner>(R.id.spinnerDismissAnimation).setSelection(
            OverlayDismissAnimationMode.valuesList.indexOfFirst { it.key == config.dismissAnimationModeKey }.coerceAtLeast(0),
            false,
        )
        findViewById<Spinner>(R.id.spinnerColorPreset).setSelection(
            OverlayColorPresets.indexOfColor(config.color),
            false,
        )
        findViewById<Spinner>(R.id.spinnerBuiltinBackground).setSelection(
            when {
                config.backgroundUri == null -> 0
                BuiltinBackgrounds.decode(config.backgroundUri) != null ->
                    BuiltinBackgrounds.presets.indexOfFirst {
                        BuiltinBackgrounds.encode(it.key) == config.backgroundUri
                    }.let { index -> if (index >= 0) index + 2 else 0 }
                else -> 1
            },
            false,
        )

        bindingUi = false
        findViewById<View>(android.R.id.content).post {
            suppressSpinnerCallbacks = false
        }
    }

    private fun resolveDisplayName(uriString: String): String {
        BuiltinBackgrounds.decode(uriString)?.let { return it.title }
        val uri = Uri.parse(uriString)
        val cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return uri.lastPathSegment ?: uriString
    }

    private fun pushConfigUpdate() {
        sendBroadcast(Intent(OverlayService.ACTION_CONFIG_CHANGED).setPackage(packageName))
    }
}

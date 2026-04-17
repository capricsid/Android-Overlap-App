package com.example.overlayscreen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs

class OverlayService : Service() {
    private data class SegmentWindow(
        val view: OverlayMaskSegmentView,
        val params: WindowManager.LayoutParams,
    )

    private lateinit var windowManager: WindowManager
    private lateinit var store: OverlayConfigStore
    private lateinit var config: OverlayConfig

    private val maskWindows = mutableListOf<SegmentWindow>()

    private var controlsView: View? = null
    private var settingsView: View? = null
    private var peekOneView: PeekHandleView? = null
    private var peekTwoView: PeekHandleView? = null
    private var peekThreeView: PeekHandleView? = null
    private var peekFourView: PeekHandleView? = null
    private var dismissAnimationView: OverlayDismissAnimationView? = null

    private var controlsWindowLayoutParams: WindowManager.LayoutParams? = null
    private var settingsWindowLayoutParams: WindowManager.LayoutParams? = null
    private var dismissWindowLayoutParams: WindowManager.LayoutParams? = null

    private var panelVisible = false
    private var bindingPanel = false
    private var overlayVisible = true
    private var animatingDismiss = false

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshFromStore()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        store = OverlayConfigStore(this)
        ContextCompat.registerReceiver(
            this,
            configReceiver,
            IntentFilter(ACTION_CONFIG_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                isRunning = false
                notifyStatusChanged()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START, ACTION_CONFIG_CHANGED, null -> {
                if (!Settings.canDrawOverlays(this)) {
                    isRunning = false
                    notifyStatusChanged()
                    stopSelf()
                    return START_NOT_STICKY
                }
                isRunning = true
                notifyStatusChanged()
                startOverlayForeground()
                showOverlayIfNeeded()
                refreshFromStore()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        notifyStatusChanged()
        runCatching { unregisterReceiver(configReceiver) }
        removeOverlayViews()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlayIfNeeded() {
        if (controlsView != null) return

        isRunning = true
        val bounds = windowManager.currentScreenBounds()
        config = store.load().seeded(bounds.width(), bounds.height())
        store.save(config)
        overlayVisible = true

        renderMaskSegments()

        val inflater = LayoutInflater.from(this)

        controlsView = inflater.inflate(R.layout.overlay_controls, null).also { view ->
            controlsWindowLayoutParams = controlsLayoutParams().also { params ->
                if (config.controlsX >= 0) params.x = config.controlsX
                if (config.controlsY >= 0) params.y = config.controlsY
                windowManager.addView(view, params)
            }
            bindControls(view)
            view.post {
                applyControlsCollapsedState(animated = false)
                applyControlsOpacity()
                updateSettingsPanelPosition()
            }
        }

        settingsView = inflater.inflate(R.layout.overlay_settings_panel, null).also { view ->
            view.visibility = View.GONE
            settingsWindowLayoutParams = settingsLayoutParams().also { params ->
                windowManager.addView(view, params)
            }
            bindSettingsPanel(view)
        }

        peekOneView = createPeekView(config.peekOne) { state ->
            config = config.copy(peekOne = state)
            persistAndRender()
        }
        peekTwoView = createPeekView(config.peekTwo) { state ->
            config = config.copy(peekTwo = state)
            persistAndRender()
        }
        peekThreeView = createPeekView(config.peekThree) { state ->
            config = config.copy(peekThree = state)
            persistAndRender()
        }
        peekFourView = createPeekView(config.peekFour) { state ->
            config = config.copy(peekFour = state)
            persistAndRender()
        }

        setPeekEditorsVisible(false)
        restackForegroundWindows()
    }

    private fun refreshFromStore() {
        if (controlsView == null) return
        val bounds = windowManager.currentScreenBounds()
        config = store.load().seeded(bounds.width(), bounds.height())
        store.save(config)
        renderMaskSegments()
        restackForegroundWindows()
        peekOneView?.applyState(config.peekOne)
        peekTwoView?.applyState(config.peekTwo)
        peekThreeView?.applyState(config.peekThree)
        peekFourView?.applyState(config.peekFour)
        applyControlsOpacity()
        applyControlsCollapsedState(animated = false)
        bindPanelValues(settingsView)
        updateOverlayVisibility()
    }

    private fun bindControls(view: View) {
        bindControlsDrag(view)

        view.findViewById<ImageButton>(R.id.btnTurnOffOverlay).setOnClickListener {
            toggleOverlayVisibility()
        }
        view.findViewById<ImageButton>(R.id.btnOpenOverlaySettings).setOnClickListener {
            panelVisible = !panelVisible
            updateOverlayVisibility()
        }
        view.findViewById<ImageButton>(R.id.btnGoHome).setOnClickListener {
            goHome()
        }
        view.findViewById<ImageButton>(R.id.btnCollapseOverlayToolbar).setOnClickListener {
            config = config.copy(controlsCollapsed = !config.controlsCollapsed)
            if (config.controlsCollapsed) {
                panelVisible = false
            }
            store.save(config)
            applyControlsCollapsedState(animated = true)
            updateOverlayVisibility()
        }

        updateControlsAppearance()
    }

    private fun bindControlsDrag(view: View) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        val touchListener = View.OnTouchListener { buttonView, event ->
            val params = controlsWindowLayoutParams ?: return@OnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startRawX).toInt()
                    val dy = (event.rawY - startRawY).toInt()
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        moveControlsTo(startX + dx, startY + dy, persist = false)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        snapControlsToNearestEdge()
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        buttonView.performClick()
                    }
                    dragging = false
                    true
                }

                else -> false
            }
        }

        listOf(
            R.id.btnCollapseOverlayToolbar,
            R.id.btnTurnOffOverlay,
            R.id.btnGoHome,
            R.id.btnOpenOverlaySettings,
        ).forEach { id ->
            view.findViewById<ImageButton>(id).setOnTouchListener(touchListener)
        }
    }

    private fun bindSettingsPanel(view: View) {
        val opacitySeek = view.findViewById<SeekBar>(R.id.seekOpacityQuick)
        val trueOpaqueCheck = view.findViewById<CheckBox>(R.id.checkTrueOpaqueMaskQuick)
        val controlsOpacitySeek = view.findViewById<SeekBar>(R.id.seekControlsOpacityQuick)
        val widthSeek = view.findViewById<SeekBar>(R.id.seekWidthQuick)
        val topSeek = view.findViewById<SeekBar>(R.id.seekTopQuick)
        val bottomSeek = view.findViewById<SeekBar>(R.id.seekBottomQuick)

        opacitySeek.max = OverlayOpacityPolicy.DISPLAY_MAX_PERCENT
        controlsOpacitySeek.max = 85
        widthSeek.max = 70
        topSeek.max = 70
        bottomSeek.max = 70

        opacitySeek.setOnSeekBarChangeListener(simpleSeekListener { value ->
            if (bindingPanel) return@simpleSeekListener
            config = config.copy(
                opacityPercent = OverlayOpacityPolicy.displayPercentToActual(
                    value,
                    config.fullOpaqueMaskEnabled,
                ),
            )
            persistAndRender(updatePanel = false)
            bindPanelValues(view)
        })
        trueOpaqueCheck.setOnCheckedChangeListener { _, isChecked ->
            if (bindingPanel) return@setOnCheckedChangeListener
            val displayPercent = OverlayOpacityPolicy.actualPercentToDisplay(
                config.opacityPercent,
                config.fullOpaqueMaskEnabled,
            )
            config = config.copy(
                fullOpaqueMaskEnabled = isChecked,
                opacityPercent = OverlayOpacityPolicy.displayPercentToActual(
                    displayPercent,
                    isChecked,
                ),
            )
            persistAndRender(updatePanel = false)
            bindPanelValues(view)
        }
        controlsOpacitySeek.setOnSeekBarChangeListener(simpleSeekListener { value ->
            if (bindingPanel) return@simpleSeekListener
            config = config.copy(controlsOpacityPercent = value + 15)
            persistAndRender(updatePanel = false)
            bindPanelValues(view)
        })
        widthSeek.setOnSeekBarChangeListener(simpleSeekListener { value ->
            if (bindingPanel) return@simpleSeekListener
            config = config.copy(overlayWidthPercent = value + 30)
            persistAndRender(updatePanel = false)
            bindPanelValues(view)
        })
        topSeek.setOnSeekBarChangeListener(simpleSeekListener { value ->
            if (bindingPanel) return@simpleSeekListener
            config = config.withTopEdge(value)
            persistAndRender(updatePanel = false)
            bindPanelValues(view)
        })
        bottomSeek.setOnSeekBarChangeListener(simpleSeekListener { value ->
            if (bindingPanel) return@simpleSeekListener
            config = config.withBottomEdge(value + OverlayConfig.MIN_OVERLAY_HEIGHT_PERCENT)
            persistAndRender(updatePanel = false)
            bindPanelValues(view)
        })

        bindPanelValues(view)
    }

    private fun bindPanelValues(view: View?) {
        if (view == null) return
        bindingPanel = true
        view.findViewById<TextView>(R.id.textOpacityQuickValue).text =
            getString(
                R.string.percent_value,
                OverlayOpacityPolicy.actualPercentToDisplay(
                    config.opacityPercent,
                    config.fullOpaqueMaskEnabled,
                ),
            )
        view.findViewById<CheckBox>(R.id.checkTrueOpaqueMaskQuick).isChecked = config.fullOpaqueMaskEnabled
        view.findViewById<TextView>(R.id.textControlsOpacityQuickValue).text =
            getString(R.string.percent_value, config.controlsOpacityPercent)
        view.findViewById<TextView>(R.id.textWidthQuickValue).text =
            getString(R.string.percent_value, config.overlayWidthPercent)
        view.findViewById<TextView>(R.id.textTopQuickValue).text =
            getString(R.string.percent_value, config.overlayTopPercent)
        view.findViewById<TextView>(R.id.textBottomQuickValue).text =
            getString(R.string.percent_value, config.overlayBottomPercent)

        view.findViewById<SeekBar>(R.id.seekOpacityQuick).progress =
            OverlayOpacityPolicy.actualPercentToDisplay(
                config.opacityPercent,
                config.fullOpaqueMaskEnabled,
            )
        view.findViewById<SeekBar>(R.id.seekControlsOpacityQuick).progress =
            config.controlsOpacityPercent - 15
        view.findViewById<SeekBar>(R.id.seekWidthQuick).progress = config.overlayWidthPercent - 30
        view.findViewById<SeekBar>(R.id.seekTopQuick).progress = config.overlayTopPercent
        view.findViewById<SeekBar>(R.id.seekBottomQuick).progress =
            config.overlayBottomPercent - OverlayConfig.MIN_OVERLAY_HEIGHT_PERCENT
        bindingPanel = false
    }

    private fun createPeekView(
        state: PeekRectState,
        onUpdate: (PeekRectState) -> Unit,
    ): PeekHandleView {
        val bounds = windowManager.currentScreenBounds()
        val params = WindowManager.LayoutParams(
            state.width,
            state.height,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = state.left
            y = state.top
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        return PeekHandleView(this).also { view ->
            view.bind(
                windowManager = windowManager,
                layoutParams = params,
                screenWidth = bounds.width(),
                screenHeight = bounds.height(),
                onUpdate = onUpdate,
            )
            windowManager.addView(view, params)
        }
    }

    private fun persistAndRender(updatePanel: Boolean = true) {
        store.save(config)
        renderMaskSegments()
        restackForegroundWindows()
        applyControlsOpacity()
        updateControlsAppearance()
        if (updatePanel) {
            bindPanelValues(settingsView)
        }
        updateOverlayVisibility()
    }

    private fun renderMaskSegments() {
        val bounds = windowManager.currentScreenBounds()
        val scene = OverlayMaskLayout.buildScene(config, bounds.width(), bounds.height())

        while (maskWindows.size > scene.segments.size) {
            val removed = maskWindows.removeLast()
            runCatching { windowManager.removeView(removed.view) }
        }

        while (maskWindows.size < scene.segments.size) {
            val rect = scene.segments[maskWindows.size]
            val view = OverlayMaskSegmentView(this)
            val params = maskSegmentLayoutParams(rect)
            windowManager.addView(view, params)
            maskWindows += SegmentWindow(view, params)
        }

        maskWindows.forEachIndexed { index, window ->
            val rect = scene.segments[index]
            window.view.updateSegment(config, scene.overlayRect, rect)
            window.params.width = rect.width()
            window.params.height = rect.height()
            window.params.x = rect.left
            window.params.y = rect.top
            window.params.format = maskPixelFormat()
            window.params.alpha = config.opacityPercent / 100f
            if (window.view.isAttachedToWindow) {
                windowManager.updateViewLayout(window.view, window.params)
            }
        }
    }

    private fun restackForegroundWindows() {
        restackWindow(controlsView, controlsWindowLayoutParams)
        restackWindow(settingsView, settingsWindowLayoutParams)
    }

    private fun restackWindow(
        view: View?,
        params: WindowManager.LayoutParams?,
    ) {
        if (view == null || params == null || !view.isAttachedToWindow) return
        runCatching {
            windowManager.removeView(view)
            windowManager.addView(view, params)
        }
    }

    private fun setPeekEditorsVisible(visible: Boolean) {
        val activeCount = config.peekWindowCount.coerceIn(1, 4)
        listOf(peekOneView, peekTwoView, peekThreeView, peekFourView).forEachIndexed { index, peekView ->
            peekView?.visibility = if (visible && index < activeCount) View.VISIBLE else View.GONE
        }
    }

    private fun removeOverlayViews() {
        removeDismissAnimationView()
        maskWindows.forEach { window ->
            runCatching { windowManager.removeView(window.view) }
        }
        maskWindows.clear()
        listOf(controlsView, settingsView, peekOneView, peekTwoView, peekThreeView, peekFourView).forEach { view ->
            if (view != null) {
                runCatching { windowManager.removeView(view) }
            }
        }
        controlsView = null
        settingsView = null
        peekOneView = null
        peekTwoView = null
        peekThreeView = null
        peekFourView = null
        controlsWindowLayoutParams = null
        settingsWindowLayoutParams = null
    }

    private fun toggleOverlayVisibility() {
        if (overlayVisible) {
            hideOverlayWithAnimation()
        } else {
            overlayVisible = true
            updateOverlayVisibility()
        }
    }

    private fun hideOverlayWithAnimation() {
        if (!overlayVisible || animatingDismiss) return
        val animationMode = OverlayDismissAnimationMode.fromKey(config.dismissAnimationModeKey)
        if (animationMode == OverlayDismissAnimationMode.OFF) {
            overlayVisible = false
            updateOverlayVisibility()
            return
        }

        val bounds = windowManager.currentScreenBounds()
        val snapshot = OverlayMaskPainter.captureSceneBitmap(this, config, bounds.width(), bounds.height())
        if (snapshot == null) {
            overlayVisible = false
            updateOverlayVisibility()
            return
        }

        animatingDismiss = true
        panelVisible = false
        settingsView?.visibility = View.GONE
        setPeekEditorsVisible(false)
        maskWindows.forEach { it.view.visibility = View.INVISIBLE }

        val animationView = OverlayDismissAnimationView(this)
        dismissAnimationView = animationView
        dismissWindowLayoutParams = fullScreenLayoutParams().also { params ->
            windowManager.addView(animationView, params)
        }
        animationView.play(snapshot, animationMode) {
            overlayVisible = false
            animatingDismiss = false
            removeDismissAnimationView()
            updateOverlayVisibility()
        }
    }

    private fun removeDismissAnimationView() {
        dismissAnimationView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        dismissAnimationView = null
        dismissWindowLayoutParams = null
    }

    private fun updateOverlayVisibility() {
        if (!overlayVisible) {
            maskWindows.forEach { it.view.visibility = View.GONE }
            settingsView?.visibility = View.GONE
            setPeekEditorsVisible(false)
        } else {
            if (!animatingDismiss) {
                maskWindows.forEach { it.view.visibility = View.VISIBLE }
            }
            settingsView?.visibility =
                if (panelVisible && !config.controlsCollapsed) View.VISIBLE else View.GONE
            setPeekEditorsVisible(panelVisible && overlayVisible && !config.controlsCollapsed)
        }
        updateControlsAppearance()
        updateSettingsPanelPosition()
    }

    private fun updateControlsAppearance() {
        val controls = controlsView ?: return
        val powerButton = controls.findViewById<ImageButton>(R.id.btnTurnOffOverlay)
        val gearButton = controls.findViewById<ImageButton>(R.id.btnOpenOverlaySettings)
        val homeButton = controls.findViewById<ImageButton>(R.id.btnGoHome)
        val collapseButton = controls.findViewById<ImageButton>(R.id.btnCollapseOverlayToolbar)

        powerButton.setBackgroundResource(
            if (overlayVisible) {
                R.drawable.floating_icon_button_background
            } else {
                R.drawable.floating_icon_button_background_inactive
            },
        )
        gearButton.setBackgroundResource(R.drawable.floating_icon_button_background)
        homeButton.setBackgroundResource(R.drawable.floating_special_button_background)
        collapseButton.setImageResource(
            if (config.controlsCollapsed) R.drawable.ic_plus_symbol else R.drawable.ic_minus_symbol,
        )
    }

    private fun applyControlsOpacity() {
        controlsView?.alpha = config.controlsOpacityPercent / 100f
    }

    private fun applyControlsCollapsedState(animated: Boolean) {
        val controls = controlsView ?: return
        val strip = controls.findViewById<View>(R.id.overlayButtonsStrip)
        updateControlsAppearance()
        if (config.controlsCollapsed) {
            if (animated && strip.visibility == View.VISIBLE) {
                strip.animate()
                    .alpha(0f)
                    .scaleX(0.2f)
                    .translationX(-dp(18).toFloat())
                    .setDuration(180L)
                    .withEndAction {
                        strip.visibility = View.GONE
                        strip.alpha = 1f
                        strip.scaleX = 1f
                        strip.translationX = 0f
                    }
                    .start()
            } else {
                strip.visibility = View.GONE
                strip.alpha = 1f
                strip.scaleX = 1f
                strip.translationX = 0f
            }
        } else {
            if (animated && strip.visibility != View.VISIBLE) {
                strip.visibility = View.VISIBLE
                strip.alpha = 0f
                strip.scaleX = 0.2f
                strip.translationX = -dp(18).toFloat()
                strip.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .translationX(0f)
                    .setDuration(180L)
                    .start()
            } else {
                strip.visibility = View.VISIBLE
                strip.alpha = 1f
                strip.scaleX = 1f
                strip.translationX = 0f
            }
        }
    }

    private fun moveControlsTo(
        targetX: Int,
        targetY: Int,
        persist: Boolean,
    ) {
        val controlsParams = controlsWindowLayoutParams ?: return
        val controls = controlsView ?: return
        val bounds = windowManager.currentScreenBounds()
        val controlWidth = controls.width.takeIf { it > 0 } ?: controls.measuredWidth
        val controlHeight = controls.height.takeIf { it > 0 } ?: controls.measuredHeight
        val maxX = (bounds.width() - controlWidth).coerceAtLeast(0)
        val maxY = (bounds.height() - controlHeight).coerceAtLeast(0)

        controlsParams.x = targetX.coerceIn(0, maxX)
        controlsParams.y = targetY.coerceIn(0, maxY)
        if (controls.isAttachedToWindow) {
            windowManager.updateViewLayout(controls, controlsParams)
        }

        if (persist) {
            config = config.copy(controlsX = controlsParams.x, controlsY = controlsParams.y)
            store.save(config)
        }
        updateSettingsPanelPosition()
    }

    private fun snapControlsToNearestEdge() {
        val controlsParams = controlsWindowLayoutParams ?: return
        val controls = controlsView ?: return
        val bounds = windowManager.currentScreenBounds()
        val controlWidth = controls.width.takeIf { it > 0 } ?: controls.measuredWidth
        val maxX = (bounds.width() - controlWidth).coerceAtLeast(0)
        val targetX = if (controlsParams.x + controlWidth / 2 < bounds.width() / 2) 0 else maxX
        moveControlsTo(targetX, controlsParams.y, persist = true)
    }

    private fun updateSettingsPanelPosition() {
        val settingsParams = settingsWindowLayoutParams ?: return
        val settings = settingsView ?: return
        val controlsParams = controlsWindowLayoutParams ?: return
        val controls = controlsView ?: return
        val bounds = windowManager.currentScreenBounds()
        val maxX = (bounds.width() - (settings.width.takeIf { it > 0 } ?: dp(172))).coerceAtLeast(0)
        val maxY = (bounds.height() - (settings.height.takeIf { it > 0 } ?: dp(240))).coerceAtLeast(0)
        val desiredX = controlsParams.x
        val desiredY = controlsParams.y + (controls.height.takeIf { it > 0 } ?: dp(40)) + dp(8)
        settingsParams.x = desiredX.coerceIn(0, maxX)
        settingsParams.y = desiredY.coerceIn(0, maxY)
        if (settings.isAttachedToWindow) {
            windowManager.updateViewLayout(settings, settingsParams)
        }
    }

    private fun goHome() {
        panelVisible = false
        updateOverlayVisibility()
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun buildNotification(): Notification {
        createChannel()
        val openAppIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(getString(R.string.overlay_running_title))
            .setContentText(getString(R.string.overlay_running_text))
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.turn_off), stopIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun startOverlayForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyStatusChanged() {
        sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(packageName))
    }

    private fun fullScreenLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        overlayWindowType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    private fun maskSegmentLayoutParams(rect: Rect): WindowManager.LayoutParams = WindowManager.LayoutParams(
        rect.width(),
        rect.height(),
        overlayWindowType(),
        maskWindowFlags(),
        maskPixelFormat(),
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = rect.left
        y = rect.top
        alpha = config.opacityPercent / 100f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    private fun controlsLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayWindowType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = dp(8)
        y = dp(8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    private fun settingsLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        dp(172),
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayWindowType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = dp(8)
        y = dp(44)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    private fun simpleSeekListener(onChanged: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChanged(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun maskPixelFormat(): Int =
        if (config.fullOpaqueMaskEnabled) {
            PixelFormat.OPAQUE
        } else {
            PixelFormat.TRANSLUCENT
        }

    private fun maskWindowFlags(): Int {
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        return if (config.fullOpaqueMaskEnabled) {
            baseFlags
        } else {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    companion object {
        const val ACTION_START = "com.example.overlayscreen.action.START"
        const val ACTION_STOP = "com.example.overlayscreen.action.STOP"
        const val ACTION_CONFIG_CHANGED = "com.example.overlayscreen.action.CONFIG_CHANGED"
        const val ACTION_STATUS_CHANGED = "com.example.overlayscreen.action.STATUS_CHANGED"

        private const val CHANNEL_ID = "overlay_controls"
        private const val NOTIFICATION_ID = 41

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

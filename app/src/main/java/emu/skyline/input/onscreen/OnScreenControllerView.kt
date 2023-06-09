/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright © 2020 Skyline Team and Contributors (https://github.com/skyline-emu/)
 */

package emu.skyline.input.onscreen

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.PointF
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import emu.skyline.input.ButtonId
import emu.skyline.input.ButtonState
import emu.skyline.input.ControllerType
import emu.skyline.input.StickId
import emu.skyline.utils.add
import emu.skyline.utils.multiply
import emu.skyline.utils.normalize
import kotlin.math.roundToLong

typealias OnButtonStateChangedListener = (buttonId : ButtonId, state : ButtonState) -> Unit
typealias OnStickStateChangedListener = (stickId : StickId, position : PointF) -> Unit

/**
 * Renders On-Screen Controls as a single view, handles touch inputs and button toggling
 */
class OnScreenControllerView @JvmOverloads constructor(context : Context, attrs : AttributeSet? = null, defStyleAttr : Int = 0, defStyleRes : Int = 0) : View(context, attrs, defStyleAttr, defStyleRes) {
    companion object {
        private val controllerTypeMappings = mapOf(*ControllerType.values().map {
            it to (setOf(*it.buttons) + setOf(*it.optionalButtons) to setOf(*it.sticks))
        }.toTypedArray())

        private const val SCALE_STEP = 0.05f
        private const val ALPHA_STEP = 25
        private val ALPHA_RANGE = 55..255
    }

    private var onButtonStateChangedListener : OnButtonStateChangedListener? = null
    private var onStickStateChangedListener : OnStickStateChangedListener? = null
    private val joystickAnimators = mutableMapOf<JoystickButton, Animator?>()
    var controllerType : ControllerType? = null
        set(value) {
            field = value
            invalidate()
        }
    var recenterSticks = false
        set(value) {
            field = value
            controls.joysticks.forEach { it.recenterSticks = recenterSticks }
        }
    var hapticFeedback = false
        set(value) {
            field = value
            (controls.circularButtons + controls.rectangularButtons + controls.triggerButtons).forEach { it.hapticFeedback = hapticFeedback }
        }

    val editInfo = OnScreenEditInfo()
    val isEditing get() = editInfo.isEditing

    // Populated externally by the activity, as retrieving the vibrator service inside the view crashes the layout editor
    lateinit var vibrator : Vibrator
    private val effectClick = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)

    // Ensure controls init happens after editInfo is initialized so that the buttons have a valid reference to it
    private val controls = Controls(this)

    override fun onDraw(canvas : Canvas) {
        super.onDraw(canvas)

        val allowedIds = controllerTypeMappings[controllerType]
        controls.allButtons.forEach { button ->
            if (button.config.enabled
                && allowedIds?.let { (buttonIds, stickIds) ->
                    if (button is JoystickButton) stickIds.contains(button.stickId) else buttonIds.contains(button.buttonId)
                } != false
            ) {
                button.width = width
                button.height = height
                button.render(canvas)
            }
        }
    }

    private val playingTouchHandler = OnTouchListener { _, event ->
        var handled = false
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)
        val x by lazy { event.getX(actionIndex) }
        val y by lazy { event.getY(actionIndex) }

        (controls.circularButtons + controls.rectangularButtons + controls.triggerButtons).forEach { button ->
            when (event.action and event.actionMasked) {
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> {
                    if (pointerId == button.touchPointerId) {
                        button.touchPointerId = -1
                        button.onFingerUp(x, y)
                        onButtonStateChangedListener?.invoke(button.buttonId, ButtonState.Released)
                        handled = true
                    } else if (pointerId == button.partnerPointerId) {
                        button.partnerPointerId = -1
                        button.onFingerUp(x, y)
                        onButtonStateChangedListener?.invoke(button.buttonId, ButtonState.Released)
                        handled = true
                    }
                }

                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (button.config.enabled && button.isTouched(x, y)) {
                        button.touchPointerId = pointerId
                        button.onFingerDown(x, y)
                        if (hapticFeedback) vibrator.vibrate(effectClick)
                        performClick()
                        onButtonStateChangedListener?.invoke(button.buttonId, ButtonState.Pressed)
                        handled = true
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    for (fingerId in 0 until event.pointerCount) {
                        if (fingerId == button.touchPointerId) {
                            for (buttonPair in controls.buttonPairs) {
                                if (buttonPair.contains(button)) {
                                    for (otherButton in buttonPair) {
                                        if (otherButton != button && otherButton.config.enabled && otherButton.isTouched(event.getX(fingerId), event.getY(fingerId))) {
                                            otherButton.partnerPointerId = fingerId
                                            otherButton.onFingerDown(x, y)
                                            performClick()
                                            onButtonStateChangedListener?.invoke(otherButton.buttonId, ButtonState.Pressed)
                                            handled = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        for (joystick in controls.joysticks) {
            when (event.actionMasked) {
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (pointerId == joystick.touchPointerId) {
                        joystick.touchPointerId = -1

                        val position = PointF(joystick.currentX, joystick.currentY)
                        val radius = joystick.radius
                        val outerToInner = joystick.outerToInner()
                        val outerToInnerLength = outerToInner.length()
                        val direction = outerToInner.normalize()
                        val duration = (50f * outerToInnerLength / radius).roundToLong()
                        joystickAnimators[joystick] = ValueAnimator.ofFloat(outerToInnerLength, 0f).apply {
                            addUpdateListener { animation ->
                                val value = animation.animatedValue as Float
                                val vector = direction.multiply(value)
                                val newPosition = position.add(vector)
                                joystick.onFingerMoved(newPosition.x, newPosition.y, false)
                                onStickStateChangedListener?.invoke(joystick.stickId, vector.multiply(1f / radius))
                                invalidate()
                            }
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationCancel(animation : Animator) {
                                    super.onAnimationCancel(animation)
                                    onAnimationEnd(animation)
                                    onStickStateChangedListener?.invoke(joystick.stickId, PointF(0f, 0f))
                                }

                                override fun onAnimationEnd(animation : Animator) {
                                    super.onAnimationEnd(animation)
                                    if (joystick.shortDoubleTapped)
                                        onButtonStateChangedListener?.invoke(joystick.buttonId, ButtonState.Released)
                                    joystick.onFingerUp(event.x, event.y)
                                    invalidate()
                                }
                            })
                            setDuration(duration)
                            start()
                        }
                        handled = true
                    }
                }

                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (joystick.config.enabled && joystick.isTouched(x, y)) {
                        joystickAnimators[joystick]?.cancel()
                        joystickAnimators[joystick] = null
                        joystick.touchPointerId = pointerId
                        joystick.onFingerDown(x, y)
                        if (joystick.shortDoubleTapped)
                            onButtonStateChangedListener?.invoke(joystick.buttonId, ButtonState.Pressed)
                        if (recenterSticks)
                            onStickStateChangedListener?.invoke(joystick.stickId, joystick.outerToInnerRelative())
                        performClick()
                        handled = true
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until event.pointerCount) {
                        if (event.getPointerId(i) == joystick.touchPointerId) {
                            val centerToPoint = joystick.onFingerMoved(event.getX(i), event.getY(i))
                            onStickStateChangedListener?.invoke(joystick.stickId, centerToPoint)
                            handled = true
                        }
                    }
                }
            }
        }
        handled.also { if (it) invalidate() }
    }

    private val editingTouchHandler = OnTouchListener { _, event ->
        var handled = false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Handle this event only if no other button is being edited
                if (editInfo.editButton == null) {
                    handled = controls.allButtons.any { button ->
                        if (button.config.enabled && button.isTouched(event.x, event.y)) {
                            editInfo.editButton = button
                            button.startEdit(event.x, event.y)
                            performClick()
                            true
                        } else false
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                editInfo.editButton?.edit(event.x, event.y)
                handled = true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                editInfo.editButton?.endEdit()
                editInfo.editButton = null
                handled = true
            }
        }

        handled.also { if (it) invalidate() }
    }

    init {
        setOnTouchListener(playingTouchHandler)
    }

    fun setEditMode(editMode : EditMode) {
        editInfo.editMode = editMode
        setOnTouchListener(if (isEditing) editingTouchHandler else playingTouchHandler )
    }

    fun resetControls() {
        controls.allButtons.forEach {
            it.resetConfig()
        }
        controls.globalScale = OnScreenConfiguration.DefaultGlobalScale
        controls.alpha = OnScreenConfiguration.DefaultAlpha
        invalidate()
    }

    fun increaseScale() {
        controls.globalScale += SCALE_STEP
        invalidate()
    }

    fun decreaseScale() {
        controls.globalScale -= SCALE_STEP
        invalidate()
    }

    fun setSnapToGrid(snap : Boolean) {
        editInfo.snapToGrid = snap
    }

    fun increaseOpacity() {
        controls.alpha = (controls.alpha + ALPHA_STEP).coerceIn(ALPHA_RANGE)
        invalidate()
    }

    fun decreaseOpacity() {
        controls.alpha = (controls.alpha - ALPHA_STEP).coerceIn(ALPHA_RANGE)
        invalidate()
    }

    fun getTextColor() : Int {
        return controls.globalTextColor
    }

    fun getBackGroundColor() : Int {
        return controls.globalBackgroundColor
    }

    fun setOnButtonStateChangedListener(listener : OnButtonStateChangedListener) {
        onButtonStateChangedListener = listener
    }

    fun setOnStickStateChangedListener(listener : OnStickStateChangedListener) {
        onStickStateChangedListener = listener
    }

    data class ButtonProp(val buttonId : ButtonId, val enabled : Boolean)

    fun getButtonProps() = controls.allButtons.map { ButtonProp(it.buttonId, it.config.enabled) }

    fun setButtonEnabled(buttonId : ButtonId, enabled : Boolean) {
        controls.allButtons.first { it.buttonId == buttonId }.config.enabled = enabled
        invalidate()
    }

    fun setTextColor(color : Int) {
        for (button in controls.allButtons) {
            button.config.textColor = color
        }
        invalidate()
    }

    fun setBackGroundColor(color : Int) {
        for (button in controls.allButtons) {
            button.config.backgroundColor = color
        }
        invalidate()
    }
}

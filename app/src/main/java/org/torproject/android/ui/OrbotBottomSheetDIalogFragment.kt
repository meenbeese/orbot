package org.torproject.android

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.EditText

import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
Class to setup default bottom sheet behavior for Config Connection, MOAT and any other
bottom sheets to come
 */
open class OrbotBottomSheetDialogFragment : BottomSheetDialogFragment() {
    private var backPressed = false
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val dialog = object : BottomSheetDialog(requireActivity(), theme) {
                override fun onBackPressed() {
                    super.onBackPressed()
                    backPressed = true
                }
            }
            dialog.setOnShowListener {setupRatio(dialog)}
            return dialog
    }

    override fun onCancel(dialog: DialogInterface) {
        if (!backPressed) {
            // todo this method only works for now because OrbotActivity is locked in portrait mode
           // closeAllSheetsInternal()
            dismiss()
        }
    }

    protected fun closeAllSheets() {
       // closeAllSheetsInternal()
        dismiss()
    }

    private fun closeAllSheetsInternal() {
        val fm = requireActivity().supportFragmentManager
        for (f in fm.fragments) {
            if (f == this) continue
            fm.beginTransaction().remove(f).commit()
        }
    }


    private fun setupRatio(bsd : BottomSheetDialog) {
        val bottomSheet = bsd.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            val layoutParams = it.layoutParams
            layoutParams.height = getHeight()
            bottomSheet.layoutParams = layoutParams
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun getHeight(): Int {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) return getHeightLegacy()

        val windowManager = requireActivity().getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val windowMetrics = windowManager.currentWindowMetrics
        val windowInsets = windowMetrics.windowInsets

        val insets = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
        val insetsWidth = insets.right + insets.left
        val insetsHeight = insets.top + insets.bottom

        val bounds = windowMetrics.bounds
        val legacySize = Point(bounds.width() - insetsWidth, bounds.height() - insetsHeight)
        val heightPercent = if (legacySize.y > 2000) 55 else 65

        return legacySize.y * heightPercent / 100
    }

    // Should be deleted once minSdkVersion is >= 30
    private fun getHeightLegacy(): Int {
        val windowManager = requireActivity().getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
        val display = windowManager.defaultDisplay
        display?.getRealMetrics(displayMetrics)
        val heightPercent = if (displayMetrics.heightPixels > 2000) 50 else 65
        return displayMetrics.heightPixels * heightPercent / 100
    }

    @SuppressLint("ClickableViewAccessibility")
    protected fun configureMultilineEditTextScrollEvent(editText: EditText) {
        // need this for scrolling an edittext in a BSDF
        editText.setOnTouchListener {v , event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }
}
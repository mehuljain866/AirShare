package com.mehuljain.airshare.transfer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.text.format.Formatter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.mehuljain.airshare.R

/**
 * Manages the floating, Dynamic Island-style overlay prompt on top of all apps
 * when [Settings.canDrawOverlays] is granted.
 */
class AirShareOverlayManager(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayRoot: View? = null

    // UI elements inside card
    private var titleText: TextView? = null
    private var subtitleText: TextView? = null
    private var thumbnailView: ImageView? = null
    private var buttonsRow: LinearLayout? = null
    private var progressSection: LinearLayout? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null
    private var progressSubtext: TextView? = null
    private var btnAccept: Button? = null
    private var btnDecline: Button? = null

    fun canDraw(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun show(request: TransferRequest, initialThumb: Bitmap? = null) {
        if (!canDraw()) return
        mainHandler.post {
            ensureOverlayCreated()
            bindRequest(request, initialThumb)
            showCard()
        }
    }

    fun updateProgress(bytesRead: Long, totalBytes: Long, progress: Float) {
        mainHandler.post {
            buttonsRow?.visibility = View.GONE
            progressSection?.visibility = View.VISIBLE

            if (totalBytes > 0) {
                progressBar?.isIndeterminate = false
                progressBar?.max = 1000
                progressBar?.progress = (progress * 1000).toInt()

                val percent = (progress * 100).toInt()
                progressText?.text = "Receiving $percent%"
                val readStr = Formatter.formatShortFileSize(context, bytesRead)
                val totalStr = Formatter.formatShortFileSize(context, totalBytes)
                progressSubtext?.text = "$readStr / $totalStr"
            } else {
                progressBar?.isIndeterminate = true
                progressText?.text = "Receiving…"
                progressSubtext?.text = Formatter.formatShortFileSize(context, bytesRead)
            }
        }
    }

    fun setThumbnail(bitmap: Bitmap) {
        mainHandler.post {
            thumbnailView?.setImageBitmap(bitmap)
            thumbnailView?.scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }

    fun showCompleted(success: Boolean, message: String) {
        mainHandler.post {
            buttonsRow?.visibility = View.GONE
            progressSection?.visibility = View.VISIBLE
            progressBar?.visibility = View.GONE

            if (success) {
                progressText?.text = "✓ $message"
                progressText?.setTextColor(Color.parseColor("#30D158"))
                progressSubtext?.text = "Saved to Gallery / Storage"
            } else {
                progressText?.text = "✕ Transfer $message"
                progressText?.setTextColor(Color.parseColor("#FF453A"))
                progressSubtext?.text = ""
            }

            // Auto dismiss after 1.6s
            mainHandler.postDelayed({
                dismiss()
            }, 1600)
        }
    }

    fun dismiss() {
        mainHandler.post {
            val root = overlayRoot ?: return@post
            root.animate()
                .alpha(0f)
                .translationY(-dp(60f))
                .setDuration(220)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        removeRoot()
                    }
                })
        }
    }

    private fun removeRoot() {
        try {
            overlayRoot?.let {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            }
        } catch (_: Exception) {}
        overlayRoot = null
    }

    private fun ensureOverlayCreated() {
        if (overlayRoot != null) return

        val root = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        // Floating Card
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padH = dp(16f).toInt()
            val padV = dp(16f).toInt()
            setPadding(padH, padV, padH, padV)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(24f))
                }
            }

            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#161618"))
                cornerRadius = dp(24f)
                setStroke(dp(1.2f).toInt(), Color.parseColor("#2E2E32"))
            }
            background = bg
            elevation = dp(16f)
        }

        val cardLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        ).apply {
            leftMargin = dp(16f).toInt()
            rightMargin = dp(16f).toInt()
        }
        root.addView(card, cardLp)

        // Top Row: [Icon + Texts] (weight 1) + [Thumbnail]
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        card.addView(topRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Wireless AirShare Icon
        val iconBadge = ImageView(context).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            val iconSize = dp(42f).toInt()
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                rightMargin = dp(12f).toInt()
            }
        }
        topRow.addView(iconBadge)

        // Texts column
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(12f).toInt()
            }
        }
        topRow.addView(textCol)

        titleText = TextView(context).apply {
            text = "AirShare"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        textCol.addView(titleText)

        subtitleText = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#A1A1A6"))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(2f).toInt(), 0, 0)
        }
        textCol.addView(subtitleText)

        // Thumbnail view
        val thumbContainer = FrameLayout(context).apply {
            val size = dp(62f).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(14f))
                }
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#26262A"))
                cornerRadius = dp(14f)
                setStroke(dp(1f).toInt(), Color.parseColor("#38383E"))
            }
        }
        thumbnailView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        thumbContainer.addView(thumbnailView)
        topRow.addView(thumbContainer)

        // Buttons Row
        buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44f).toInt()).apply {
                topMargin = dp(16f).toInt()
            }
            layoutParams = lp
        }
        card.addView(buttonsRow)

        // Decline Button
        btnDecline = Button(context).apply {
            text = "Decline"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#323236"))
                cornerRadius = dp(22f)
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                rightMargin = dp(8f).toInt()
            }
            setOnClickListener {
                TransferCoordinator.decline()
                dismiss()
            }
        }
        buttonsRow?.addView(btnDecline)

        // Accept Button
        btnAccept = Button(context).apply {
            text = "Accept"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A84FF"))
                cornerRadius = dp(22f)
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = dp(8f).toInt()
            }
            setOnClickListener {
                btnAccept?.isEnabled = false
                btnDecline?.isEnabled = false
                TransferCoordinator.accept()
            }
        }
        buttonsRow?.addView(btnAccept)

        // Progress Section (Hidden initially)
        progressSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14f).toInt()
            }
            layoutParams = lp
            visibility = View.GONE
        }
        card.addView(progressSection)

        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(7f).toInt()).apply {
                bottomMargin = dp(6f).toInt()
            }
            progressDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#0A84FF"))
                cornerRadius = dp(4f)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2C2C30"))
                cornerRadius = dp(4f)
            }
        }
        progressSection?.addView(progressBar)

        val progRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        progressSection?.addView(progRow)

        progressText = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = "Receiving…"
        }
        progRow.addView(progressText)

        progressSubtext = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#A1A1A6"))
            text = ""
        }
        progRow.addView(progressSubtext)

        // Attach to WindowManager
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = getStatusBarHeight() + dp(12f).toInt()
        }

        overlayRoot = root
        try {
            windowManager.addView(root, lp)
        } catch (_: Exception) {}
    }

    private fun bindRequest(request: TransferRequest, thumb: Bitmap?) {
        subtitleText?.text = "${request.senderName} would like to share ${request.jobName}"
        if (thumb != null) {
            thumbnailView?.setImageBitmap(thumb)
            thumbnailView?.scaleType = ImageView.ScaleType.CENTER_CROP
        } else {
            thumbnailView?.setImageResource(R.mipmap.ic_launcher)
            thumbnailView?.scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        buttonsRow?.visibility = View.VISIBLE
        progressSection?.visibility = View.GONE
        progressBar?.visibility = View.VISIBLE
        btnAccept?.isEnabled = true
        btnDecline?.isEnabled = true
    }

    private fun showCard() {
        val root = overlayRoot ?: return
        root.alpha = 0f
        root.translationY = -dp(60f)
        root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(280)
            .setListener(null)
    }

    private fun getStatusBarHeight(): Int {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) context.resources.getDimensionPixelSize(resId) else dp(28f).toInt()
    }

    private fun dp(v: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics,
    )
}

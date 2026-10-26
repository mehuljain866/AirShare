package com.mehuljain.airshare.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.mehuljain.airshare.R
import com.mehuljain.airshare.service.PrinterController
import com.mehuljain.airshare.service.ServiceState

class AirShareWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_AIRSHARE) {
            val currentState = PrinterController.status.value.state
            if (currentState == ServiceState.RUNNING || currentState == ServiceState.STARTING) {
                PrinterController.stop(context)
            } else {
                PrinterController.start(context)
            }
            updateAll(context)
        }
    }

    companion object {
        const val ACTION_TOGGLE_AIRSHARE = "com.mehuljain.airshare.action.TOGGLE_AIRSHARE"

        fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, AirShareWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                for (id in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, id)
                }
            }
        }

        private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_airshare)
            val status = PrinterController.status.value
            val isRunning = status.state == ServiceState.RUNNING || status.state == ServiceState.STARTING
            val autoOffEndTime = PrinterController.autoOffEndTime.value
            val now = SystemClock.elapsedRealtime()

            if (isRunning) {
                views.setImageViewResource(R.id.widget_toggle_btn, R.drawable.widget_power_on)
                if (autoOffEndTime != null && autoOffEndTime > now) {
                    // Use native Chronometer for battery-friendly, second-by-second countdown in Launcher
                    views.setViewVisibility(R.id.widget_status, View.GONE)
                    views.setViewVisibility(R.id.widget_timer, View.VISIBLE)
                    views.setTextColor(R.id.widget_timer, Color.parseColor("#0A84FF"))
                    views.setChronometer(R.id.widget_timer, autoOffEndTime, "Ready · Auto-off %s", true)
                    views.setChronometerCountDown(R.id.widget_timer, true)
                } else {
                    // Timer disabled ("Never") or expired
                    views.setChronometer(R.id.widget_timer, 0, null, false)
                    views.setViewVisibility(R.id.widget_timer, View.GONE)
                    views.setViewVisibility(R.id.widget_status, View.VISIBLE)
                    views.setTextViewText(R.id.widget_status, "Ready to receive")
                    views.setTextColor(R.id.widget_status, Color.parseColor("#0A84FF"))
                }
            } else {
                // Stopped / Off
                views.setChronometer(R.id.widget_timer, 0, null, false)
                views.setViewVisibility(R.id.widget_timer, View.GONE)
                views.setViewVisibility(R.id.widget_status, View.VISIBLE)
                views.setTextViewText(R.id.widget_status, "Off · Tap to start")
                views.setTextColor(R.id.widget_status, Color.parseColor("#8E8E93"))
                views.setImageViewResource(R.id.widget_toggle_btn, R.drawable.widget_power_off)
            }

            val toggleIntent = Intent(context, AirShareWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_AIRSHARE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_toggle_btn, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

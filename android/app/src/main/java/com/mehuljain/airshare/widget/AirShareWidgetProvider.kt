package com.mehuljain.airshare.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.mehuljain.airshare.R
import com.mehuljain.airshare.service.PrinterController
import com.mehuljain.airshare.service.ServiceState
import java.util.Locale

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
            for (id in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }

        private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_airshare)
            val status = PrinterController.status.value
            val isRunning = status.state == ServiceState.RUNNING || status.state == ServiceState.STARTING
            val remainingSecs = PrinterController.remainingSeconds.value

            if (isRunning) {
                val timerText = if (remainingSecs != null && remainingSecs > 0) {
                    val m = remainingSecs / 60
                    val s = remainingSecs % 60
                    String.format(Locale.getDefault(), "Ready · Auto-off %02d:%02d", m, s)
                } else {
                    "Ready to receive"
                }
                views.setTextViewText(R.id.widget_status, timerText)
                views.setTextColor(R.id.widget_status, Color.parseColor("#0A84FF"))
                views.setImageViewResource(R.id.widget_toggle_btn, R.drawable.widget_power_on)
            } else {
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

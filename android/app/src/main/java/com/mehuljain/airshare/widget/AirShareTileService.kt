package com.mehuljain.airshare.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.mehuljain.airshare.R
import com.mehuljain.airshare.service.PrinterController
import com.mehuljain.airshare.service.ServiceState
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.N)
class AirShareTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentState = PrinterController.status.value.state
        if (currentState == ServiceState.RUNNING || currentState == ServiceState.STARTING) {
            PrinterController.stop(this)
        } else {
            PrinterController.start(this)
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val status = PrinterController.status.value
        val isRunning = status.state == ServiceState.RUNNING || status.state == ServiceState.STARTING
        val remaining = PrinterController.remainingSeconds.value

        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_airshare)
        tile.label = "AirShare"

        if (isRunning) {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (remaining != null && remaining > 0) {
                    val m = remaining / 60
                    val s = remaining % 60
                    tile.subtitle = String.format(Locale.getDefault(), "Auto-off %02d:%02d", m, s)
                } else {
                    tile.subtitle = "Ready"
                }
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Off"
            }
        }
        tile.updateTile()
    }

    companion object {
        fun updateTileState(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                requestListeningState(context, ComponentName(context, AirShareTileService::class.java))
            }
        }
    }
}

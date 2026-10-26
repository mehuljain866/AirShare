package com.mehuljain.airshare.transfer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TransferReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_ACCEPT -> TransferCoordinator.accept()
            ACTION_DECLINE -> TransferCoordinator.decline()
        }
    }

    companion object {
        const val ACTION_ACCEPT = "com.mehuljain.airshare.action.ACCEPT_TRANSFER"
        const val ACTION_DECLINE = "com.mehuljain.airshare.action.DECLINE_TRANSFER"
    }
}

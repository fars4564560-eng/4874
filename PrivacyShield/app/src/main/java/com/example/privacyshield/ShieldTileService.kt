package com.example.privacyshield

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** مربع في الإعدادات السريعة لتفعيل/إيقاف الحماية يدوياً بلمسة واحدة. */
class ShieldTileService : TileService() {

    private fun isOn() = DisplayShield.active || DisplayShield.hasPendingRestore()

    override fun onStartListening() {
        render(isOn())
    }

    override fun onClick() {
        if (isOn()) {
            Prefs.setManual(this, false)
            DisplayShield.off()
            render(false)
        } else {
            Prefs.setManual(this, true)
            DisplayShield.on(Prefs.dim(this), Prefs.gray(this))
            render(true)
        }
    }

    private fun render(on: Boolean) {
        val tile = qsTile ?: return
        tile.label = "Privacy Shield"
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}

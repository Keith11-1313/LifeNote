package com.lifenote

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom

/**
 * Device identity + pairing registry. Backed by SharedPreferences.
 * The token is generated once per install and shared manually with peers.
 */
class Settings private constructor(private val prefs: SharedPreferences) {

    val token: String
        get() {
            var t = prefs.getString(KEY_TOKEN, null)
            if (t == null) {
                t = generateToken()
                prefs.edit().putString(KEY_TOKEN, t).apply()
            }
            return t
        }

    var deviceName: String
        get() = prefs.getString(KEY_DEVICE, null) ?: defaultDeviceName()
        set(value) = prefs.edit().putString(KEY_DEVICE, value).apply()

    /** Raw JSON array of {name, addr} — schema owned by the UI until sync phase. */
    var peersJson: String
        get() = prefs.getString(KEY_PEERS, null) ?: "[]"
        set(value) = prefs.edit().putString(KEY_PEERS, value).apply()

    private fun generateToken(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        val groups = List(3) { List(3) { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("") }
        return groups.joinToString("-")
    }

    private fun defaultDeviceName(): String {
        val model = android.os.Build.MODEL?.trim() ?: "android"
        return model.ifEmpty { "android" }.replace(Regex("\\s+"), "-").lowercase()
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE = "device_name"
        private const val KEY_PEERS = "peers_json"

        fun load(context: Context): Settings {
            val prefs = context.getSharedPreferences("lifenote", Context.MODE_PRIVATE)
            return Settings(prefs)
        }
    }
}

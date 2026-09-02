package com.lifenote

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom
import java.security.MessageDigest

/**
 * Local app preferences backed by SharedPreferences.
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

    val hasLockPin: Boolean get() = prefs.contains(KEY_LOCK_PIN)

    fun setLockPin(pin: String) {
        prefs.edit().putString(KEY_LOCK_PIN, hash(pin)).apply()
    }

    fun matchesLockPin(pin: String): Boolean =
        prefs.getString(KEY_LOCK_PIN, "") == hash(pin)

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

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE = "device_name"
        private const val KEY_LOCK_PIN = "lock_pin"

        fun load(context: Context): Settings {
            val prefs = context.getSharedPreferences("lifenote", Context.MODE_PRIVATE)
            return Settings(prefs)
        }
    }
}

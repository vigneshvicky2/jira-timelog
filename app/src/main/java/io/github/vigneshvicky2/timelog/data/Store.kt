package io.github.vigneshvicky2.timelog.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** App preferences. The Jira API key is encrypted with a hardware-backed Android Keystore key. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("timelog", Context.MODE_PRIVATE)

    var site: String
        get() = prefs.getString("site", null) ?: DEFAULT_SITE
        set(v) = prefs.edit().putString("site", v).apply()
    var email: String
        get() = prefs.getString("email", null) ?: DEFAULT_EMAIL
        set(v) = prefs.edit().putString("email", v).apply()
    var accountId: String
        get() = prefs.getString("accountId", "") ?: ""
        set(v) = prefs.edit().putString("accountId", v).apply()
    var displayName: String
        get() = prefs.getString("displayName", "") ?: ""
        set(v) = prefs.edit().putString("displayName", v).apply()

    var reminderEnabled: Boolean
        get() = prefs.getBoolean("remEnabled", false)
        set(v) = prefs.edit().putBoolean("remEnabled", v).apply()
    var reminderHour: Int
        get() = prefs.getInt("remHour", 18)
        set(v) = prefs.edit().putInt("remHour", v).apply()
    var reminderMinute: Int
        get() = prefs.getInt("remMinute", 0)
        set(v) = prefs.edit().putInt("remMinute", v).apply()
    var reminderWeekdaysOnly: Boolean
        get() = prefs.getBoolean("remWeekdays", true)
        set(v) = prefs.edit().putBoolean("remWeekdays", v).apply()

    var token: String?
        get() = prefs.getString("token", null)?.let { runCatching { decrypt(it) }.getOrNull() }
        set(v) {
            if (v == null) prefs.edit().remove("token").apply()
            else prefs.edit().putString("token", encrypt(v)).apply()
        }

    fun clearAccount() {
        prefs.edit().remove("token").remove("accountId").remove("displayName").apply()
    }

    // --- meeting title -> ticket memory ---

    private fun meetingMap(): JSONObject =
        runCatching { JSONObject(prefs.getString("meetings", "{}")!!) }.getOrDefault(JSONObject())

    fun meetingIssue(title: String): Issue? {
        val o = meetingMap().optJSONObject(normalize(title)) ?: return null
        return Issue(o.getString("key"), o.optString("summary"))
    }

    fun rememberMeeting(title: String, issue: Issue) {
        val map = meetingMap()
        map.put(normalize(title), JSONObject().put("key", issue.key).put("summary", issue.summary))
        prefs.edit().putString("meetings", map.toString()).apply()
    }

    private fun normalize(t: String) = t.lowercase().trim().replace(Regex("\\s+"), " ")

    // --- Keystore crypto ---

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = c.doFinal(plain.toByteArray())
        return b64(c.iv) + ":" + b64(ct)
    }

    private fun decrypt(stored: String): String {
        val (iv, ct) = stored.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        return String(c.doFinal(ct))
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    companion object {
        const val DEFAULT_SITE = ""
        const val DEFAULT_EMAIL = ""
        private const val ALIAS = "timelog-token"
    }
}

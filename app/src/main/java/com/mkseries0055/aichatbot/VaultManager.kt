package com.mkseries0055.aichatbot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class ApiEntry(
    val provider: String,
    val apiKey: String
)

class VaultManager(context: Context) {
    private val prefs = context.getSharedPreferences("aichatbot_vault", Context.MODE_PRIVATE)

    fun hasPassword(): Boolean {
        return prefs.getString("password_hash", null) != null
    }

    fun setMasterPassword(password: String): Pair<Boolean, String> {
        val clean = password.trim()
        if (clean.length < 4) {
            return Pair(false, "পাসওয়ার্ড অন্তত ৪ অক্ষরের হতে হবে।")
        }
        prefs.edit().putString("password_hash", hashText(clean)).apply()
        return Pair(true, "Master Password সেট হয়েছে।")
    }

    fun verifyPassword(password: String): Boolean {
        val savedHash = prefs.getString("password_hash", null) ?: return false
        return hashText(password.trim()) == savedHash
    }

    fun addApiKey(password: String, provider: String, apiKey: String): Pair<Boolean, String> {
        if (!hasPassword()) {
            return Pair(false, "আগে Settings-এ ঢুকে একটি Master Password সেট করুন।")
        }
        if (!verifyPassword(password)) {
            return Pair(false, "ভুল পাসওয়ার্ড! পাসওয়ার্ড না দিলে API ADD হবে না।")
        }
        val cleanProvider = provider.trim().ifEmpty { "OpenAI-Compatible" }
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return Pair(false, "API Key খালি রাখা যাবে না।")
        }

        val current = getApiKeys().toMutableList()
        current.add(ApiEntry(cleanProvider, cleanKey))
        saveApiKeys(current)
        return Pair(true, "$cleanProvider API Key সফলভাবে যুক্ত হয়েছে।")
    }

    fun getApiKeys(): List<ApiEntry> {
        val raw = prefs.getString("api_keys_json", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<ApiEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val provider = obj.optString("provider", "API")
            val key = obj.optString("key", "")
            if (key.isNotBlank()) {
                list.add(ApiEntry(provider, key))
            }
        }
        return list
    }

    fun removeApiKey(password: String, index: Int): Pair<Boolean, String> {
        if (!verifyPassword(password)) {
            return Pair(false, "ভুল পাসওয়ার্ড! ডিলিট করা যায়নি।")
        }
        val current = getApiKeys().toMutableList()
        if (index !in current.indices) {
            return Pair(false, "সঠিক API আইটেম পাওয়া যায়নি।")
        }
        current.removeAt(index)
        saveApiKeys(current)
        return Pair(true, "API Key মুছে ফেলা হয়েছে।")
    }

    private fun saveApiKeys(list: List<ApiEntry>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("provider", item.provider)
            obj.put("key", item.apiKey)
            arr.put(obj)
        }
        prefs.edit().putString("api_keys_json", arr.toString()).apply()
    }

    private fun hashText(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

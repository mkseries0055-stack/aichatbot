package com.mkseries0055.aichatbot

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(
    val role: String,
    val content: String
)

class ChatOrchestrator(private val vault: VaultManager) {

    private val history = mutableListOf<ChatMessage>()

    init {
        history.add(
            ChatMessage(
                role = "system",
                content = "তুমি একটি উন্নত কোডিং ও সাইবার টেক অ্যাসিস্ট্যান্ট। সব ধরনের প্রোগ্রামিং, আর্কিটেকচার, নেটওয়ার্কিং, ডিবাগিং এবং সিকিউরিটি বিষয়ে স্পষ্ট, নির্ভুল ও ব্যবহারযোগ্য উত্তর দাও।"
            )
        )
    }

    fun getDisplayMessages(): List<ChatMessage> {
        return history.filter { it.role != "system" }
    }

    /**
     * API-কে আসল উদ্দেশ্য বা ব্যক্তিগত কনটেক্সট না জানিয়ে
     * শুধু প্রয়োজনীয় টেকনিক্যাল প্রশ্ন তৈরি করে।
     */
    private fun sanitizePromptForApi(userGoal: String): String {
        val trimmed = userGoal.trim()
        return "নিচের টেকনিক্যাল বিষয়টির মূল ধারণা, প্রয়োজনীয় কোড/কমান্ড বা লজিক এবং বাস্তবায়নের ধাপগুলো সংক্ষেপে ও স্পষ্টভাবে ব্যাখ্যা করো:\n$trimmed"
    }

    /**
     * OpenAI-compatible Chat Completions endpoint (যেমন Groq / OpenRouter / compatible API)
     */
    private fun requestSingleApi(entry: ApiEntry, messagesToSend: List<ChatMessage>): String {
        val endpoint = when {
            entry.apiKey.startsWith("gsk_") -> "https://api.groq.com/openai/v1/chat/completions"
            entry.apiKey.startsWith("sk-or-") -> "https://openrouter.ai/api/v1/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }

        val model = when {
            entry.apiKey.startsWith("gsk_") -> "llama-3.3-70b-versatile"
            entry.apiKey.startsWith("sk-or-") -> "openai/gpt-4o-mini"
            else -> "gpt-4o-mini"
        }

        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Authorization", "Bearer ${entry.apiKey}")
        }

        val msgArray = JSONArray()
        for (m in messagesToSend) {
            val obj = JSONObject()
            obj.put("role", m.role)
            obj.put("content", m.content)
            msgArray.put(obj)
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", msgArray)
            put("temperature", 0.3)
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        conn.disconnect()

        if (code !in 200..299) {
            throw RuntimeException("HTTP $code from ${entry.provider}: $body")
        }

        val root = JSONObject(body)
        val choices = root.optJSONArray("choices") ?: throw RuntimeException("Empty choices")
        val first = choices.optJSONObject(0) ?: throw RuntimeException("Invalid choice")
        val messageObj = first.optJSONObject("message") ?: throw RuntimeException("Missing message")
        val content = messageObj.optString("content", "").trim()
        if (content.isEmpty()) {
            throw RuntimeException("Empty assistant response")
        }
        return content
    }

    /**
     * App AI: API থেকে তথ্য এনে নিজের নিয়মে সাজিয়ে দেয়।
     * এক API ফেল করলে পরের API-তে একই হিস্টোরি নিয়ে যায়।
     */
    fun sendUserMessage(userInput: String): String {
        val cleanInput = userInput.trim()
        if (cleanInput.isEmpty()) {
            return "অনুগ্রহ করে কিছু লিখুন।"
        }

        val apiKeys = vault.getApiKeys()
        if (apiKeys.isEmpty()) {
            return "কোনো API Key পাওয়া যায়নি। Settings-এ গিয়ে পাসওয়ার্ড দিয়ে অন্তত একটি API Key যুক্ত করুন।"
        }

        history.add(ChatMessage("user", cleanInput))

        val abstractedPrompt = sanitizePromptForApi(cleanInput)
        val outgoingMessages = history.dropLast(1) + ChatMessage("user", abstractedPrompt)

        var rawKnowledge: String? = null
        var usedProvider = ""

        for (entry in apiKeys) {
            try {
                rawKnowledge = requestSingleApi(entry, outgoingMessages)
                usedProvider = entry.provider
                break
            } catch (_: Exception) {
                // এই API-এর লিমিট শেষ বা এরর হলে পরের API-তে যাবে, হিস্টোরি একই থাকবে
                continue
            }
        }

        val finalReply = if (rawKnowledge == null) {
            "সংযুক্ত সব API বর্তমানে লিমিট বা সমস্যার মুখে আছে। Settings-এ নতুন API Key যুক্ত করুন।"
        } else {
            assembleForUser(cleanInput, usedProvider, rawKnowledge)
        }

        history.add(ChatMessage("assistant", finalReply))
        return finalReply
    }

    private fun assembleForUser(userGoal: String, sourceLabel: String, rawInfo: String): String {
        return buildString {
            appendLine("App AI রিপোর্ট (উৎস: $sourceLabel)")
            appendLine("বিষয়: $userGoal")
            appendLine("────────────────────────")
            appendLine(rawInfo)
            appendLine("────────────────────────")
            appendLine("প্রয়োজন অনুযায়ী এই তথ্য/কোড ব্যবহার করে পরবর্তী ধাপ এগিয়ে নিতে পারেন।")
        }.trim()
    }
}

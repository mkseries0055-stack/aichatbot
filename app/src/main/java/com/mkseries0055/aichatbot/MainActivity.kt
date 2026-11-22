package com.mkseries0055.aichatbot

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var vault: VaultManager
    private lateinit var orchestrator: ChatOrchestrator

    private lateinit var chatScroll: ScrollView
    private lateinit var chatContainer: LinearLayout
    private lateinit var inputBox: EditText
    private lateinit var sendBtn: Button
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vault = VaultManager(this)
        orchestrator = ChatOrchestrator(vault)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111318"))
            setPadding(24, 24, 24, 24)
        }

        // Top Bar with Title & Settings Button
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleView = TextView(this).apply {
            text = "AI Chat Bot"
            textSize = 20f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val settingsBtn = Button(this).apply {
            text = "Settings"
            setOnClickListener { openSettingsFlow() }
        }

        topBar.addView(titleView)
        topBar.addView(settingsBtn)
        root.addView(topBar)

        statusView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#9AA4B2"))
            setPadding(0, 8, 0, 12)
        }
        updateStatusText()
        root.addView(statusView)

        // Chat History Area
        chatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        chatScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(chatContainer)
        }
        root.addView(chatScroll)

        // Bottom Input Bar
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 0)
        }

        inputBox = EditText(this).apply {
            hint = "কোডিং বা সাইবার টেক প্রশ্ন লিখুন..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#7A8494"))
            setBackgroundColor(Color.parseColor("#1C2029"))
            setPadding(20, 16, 20, 16)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = 12
            }
        }

        sendBtn = Button(this).apply {
            text = "Send"
            setOnClickListener { handleSendMessage() }
        }

        bottomBar.addView(inputBox)
        bottomBar.addView(sendBtn)
        root.addView(bottomBar)

        setContentView(root)
        appendBubble("System", "শুরু করতে Settings-এ গিয়ে Password সেট করে API Key যুক্ত করুন।")
    }

    private fun updateStatusText() {
        val keyCount = vault.getApiKeys().size
        val lockState = if (vault.hasPassword()) "Password Protected" else "Password Not Set"
        statusView.text = "APIs: $keyCount টি  •  $lockState"
    }

    private fun appendBubble(sender: String, message: String) {
        val bubble = TextView(this).apply {
            text = "$sender:\n$message"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(20, 16, 20, 16)
            setBackgroundColor(
                if (sender == "You") Color.parseColor("#253654")
                else Color.parseColor("#1D222B")
            )
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 14
            layoutParams = params
        }
        chatContainer.addView(bubble)
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun handleSendMessage() {
        val text = inputBox.text.toString().trim()
        if (text.isEmpty()) return

        inputBox.setText("")
        appendBubble("You", text)
        sendBtn.isEnabled = false

        thread {
            val reply = orchestrator.sendUserMessage(text)
            runOnUiThread {
                appendBubble("App AI", reply)
                sendBtn.isEnabled = true
                updateStatusText()
            }
        }
    }

    private fun openSettingsFlow() {
        if (!vault.hasPassword()) {
            showSetPasswordDialog()
        } else {
            showVerifyPasswordDialog()
        }
    }

    private fun showSetPasswordDialog() {
        val passInput = EditText(this).apply {
            hint = "নতুন Password দিন (অন্তত ৪ অক্ষর)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Settings - Master Password সেট করুন")
            .setMessage("Settings-এ API Key যুক্ত করতে হলে আগে একটি Password সেট করতে হবে।")
            .setView(passInput)
            .setPositiveButton("Save Password") { _, _ ->
                val (ok, msg) = vault.setMasterPassword(passInput.text.toString())
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                updateStatusText()
                if (ok) {
                    showVerifyPasswordDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVerifyPasswordDialog() {
        val passInput = EditText(this).apply {
            hint = "Password লিখুন"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Settings Lock")
            .setMessage("সঠিক Password না দিলে API Key যুক্ত করা যাবে না।")
            .setView(passInput)
            .setPositiveButton("Unlock") { _, _ ->
                val enteredPass = passInput.text.toString()
                if (vault.verifyPassword(enteredPass)) {
                    showAddApiDialog(enteredPass)
                } else {
                    Toast.makeText(this, "ভুল Password! API যুক্ত করা যাবে না।", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddApiDialog(verifiedPassword: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }

        val providerInput = EditText(this).apply {
            hint = "API নাম (যেমন: Groq, OpenRouter, Backup1)"
        }
        val keyInput = EditText(this).apply {
            hint = "API Key পেস্ট করুন"
        }

        val existingInfo = TextView(this).apply {
            val keys = vault.getApiKeys()
            text = if (keys.isEmpty()) {
                "বর্তমানে কোনো API Key নেই।"
            } else {
                "যুক্ত থাকা API (${keys.size}টি): " + keys.joinToString(", ") { it.provider }
            }
            setPadding(0, 16, 0, 0)
        }

        layout.addView(providerInput)
        layout.addView(keyInput)
        layout.addView(existingInfo)

        AlertDialog.Builder(this)
            .setTitle("নতুন API Key যুক্ত করুন")
            .setView(layout)
            .setPositiveButton("Add API") { _, _ ->
                val (ok, msg) = vault.addApiKey(
                    password = verifiedPassword,
                    provider = providerInput.text.toString(),
                    apiKey = keyInput.text.toString()
                )
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                updateStatusText()
                if (!ok) {
                    Toast.makeText(this, "API যুক্ত হয়নি।", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }
}

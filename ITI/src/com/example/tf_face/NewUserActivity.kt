package com.example.tf_face

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class NewUserActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        setContentView(R.layout.activity_new_user)

        val nameInput = findViewById<EditText>(R.id.nameInput)
        val themeSpinner = findViewById<Spinner>(R.id.themeSpinner)
        val continueButton = findViewById<Button>(R.id.btnContinue)

        // Set up theme spinner
        val themes = arrayOf("Light", "Dark")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        themeSpinner?.adapter = adapter

        // Set current theme in spinner
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val currentTheme = sharedPreferences.getString("theme", "light")
        themeSpinner?.setSelection(if (currentTheme == "dark") 1 else 0)

        // NewUserActivity.kt
        continueButton?.setOnClickListener {
            val name = nameInput?.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Get selected theme
            val theme = if (themeSpinner?.selectedItem.toString() == "Dark") "dark" else "light"

            // Save theme preference
            sharedPreferences.edit().putString("theme", theme).apply()
            applyTheme()

            // Start frame capture activity with theme
            val intent = Intent(this, CaptureFramesActivity::class.java)
            intent.putExtra("user_name", name)
            intent.putExtra("user_theme", theme) // Pass theme to capture activity
            startActivity(intent)
            finish()
        }
    }

    private fun applyTheme() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val theme = sharedPreferences.getString("theme", "light")
        when (theme) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}

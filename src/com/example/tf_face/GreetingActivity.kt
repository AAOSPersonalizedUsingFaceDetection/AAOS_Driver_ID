package com.example.tf_face

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class GreetingActivity : AppCompatActivity() {
    private var showAllUsersBtn: Button? = null
    private var rootLayout: RelativeLayout? = null
    private var textGreeting: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_greeting)

        showAllUsersBtn = findViewById(R.id.showAllUsersBtn)
        rootLayout = findViewById(R.id.rootLayout)
        textGreeting = findViewById(R.id.greetingText)
        showAllUsersBtn?.setOnClickListener {
            startActivity(Intent(this, AllUsers::class.java))
        }
        val userName = intent.getStringExtra("user_name")
        if(userName!="Guest User") {
            showAllUsersBtn?.visibility = Button.VISIBLE
            textGreeting?.visibility = TextView.VISIBLE
            textGreeting?.text = "Welcome, $userName!"
        } else {
            showAllUsersBtn?.visibility = Button.GONE
            textGreeting?.visibility = TextView.GONE
        }

        if (userName.isNullOrEmpty() || userName == "Guest User") {
            rootLayout?.setBackgroundResource(R.drawable.guestwallpaper)
        } else {
            val db = DatabaseInitializer(this)
            CoroutineScope(Dispatchers.Main).launch {
                val theme = db.getThemeForUser(userName)
                when (theme) {
                    "dark" -> rootLayout?.setBackgroundResource(R.drawable.wallpaper)
                    "light" -> rootLayout?.setBackgroundResource(R.drawable.purple)
                    else -> rootLayout?.setBackgroundResource(R.drawable.wallpaper)
                }
            }
        }
    }
}

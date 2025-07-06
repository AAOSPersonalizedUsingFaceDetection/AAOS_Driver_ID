package com.example.tf_face

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class GreetingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_greeting)
        val textGreeting = findViewById<TextView>(R.id.greetingText)
        //get the user's name from the intent
        val userName = intent.getStringExtra("user_name")
	textGreeting?.text = userName


    }
}

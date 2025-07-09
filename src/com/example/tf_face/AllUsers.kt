package com.example.tf_face

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.pm.UserInfo
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.widget.ImageButton

class AllUsers : AppCompatActivity() {

    private lateinit var userRecyclerView: RecyclerView
    private lateinit var userAdapter: UserAdapter
    private val userList = mutableListOf<UserInfo>()
    private var userManagerHelper: UserManagerHelper? = null
    private var userInfo: UserInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userManagerHelper = UserManagerHelper(this)

        setContentView(R.layout.activity_all_users)
        userRecyclerView = findViewById<RecyclerView>(R.id.userRecyclerView)!!
        val layoutManager = GridLayoutManager(this, 7).apply {
            orientation = GridLayoutManager.VERTICAL
        }
        userRecyclerView.layoutManager = layoutManager
        userList.addAll(userManagerHelper?.listUsers() ?: emptyList())
        userList.removeIf { it.id == 0 }
        userList.removeIf { it.id == 10 }
        userList.removeIf { it.isGuest() }
        userAdapter = UserAdapter(userList)
        userRecyclerView.adapter = userAdapter
        val dbInitializer = DatabaseInitializer(this)
        val backButton = findViewById<ImageButton>(R.id.fabBack)
        backButton?.setOnClickListener {
            finish() 
        }
        userAdapter.setOnItemClickListener { position ->
            val selectedUser = userList[position]
            val userName = selectedUser.name!!
            
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete User")
                .setMessage("Are you sure you want to delete user \"$userName\"?")
                .setPositiveButton("Delete") { _, _ ->
                    val currentUserId = userManagerHelper?.getCurrentUserId()
                    if (currentUserId == selectedUser.id) {
                        // Run everything inside coroutine
                        lifecycleScope.launch {
                            // 1. Delete face data from Room
                            dbInitializer.deleteFacesByName(userName)
                
                            // 2. Delete system user
                            userManagerHelper?.deleteUser(selectedUser.id)
                
                            // 3. Update UI list
                            userList.removeAt(position)
                            userAdapter.notifyItemRemoved(position)
                        }
                    } else {
                        // Show error message outside coroutine
                        val errorDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Error")
                            .setMessage("You cannot delete another user.")
                            .setPositiveButton("OK", null)
                            .create()
                        errorDialog.show()
                    }
                }                
                .setNegativeButton("Cancel", null)
                .create()
            dialog.show()
        }
    }
}

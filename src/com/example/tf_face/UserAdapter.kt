package com.example.tf_face

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.content.pm.UserInfo

class UserAdapter(private val userList: MutableList<UserInfo>) :
    RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private var longClickListener: OnItemLongClickListener? = null
    private var clickListener: OnItemClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.user_item, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = userList[position]
        holder.userImage.setImageResource(R.drawable.user)
        holder.userName.text = user.name

        holder.itemView.setOnClickListener {
            clickListener?.onItemClick(position)
        }

        holder.itemView.setOnLongClickListener {
            longClickListener?.onItemLongClick(position) ?: false
        }
    }

    override fun getItemCount(): Int = userList.size

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.clickListener = listener
    }

    fun setOnItemLongClickListener(listener: OnItemLongClickListener) {
        this.longClickListener = listener
    }

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userImage: ImageView = itemView.findViewById<ImageView>(R.id.userImage)!!
        val userName: TextView = itemView.findViewById<TextView>(R.id.userName)!!
    }

    fun interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    fun interface OnItemLongClickListener {
        fun onItemLongClick(position: Int): Boolean
    }
}

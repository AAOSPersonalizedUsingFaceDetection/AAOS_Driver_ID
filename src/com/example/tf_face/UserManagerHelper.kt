package com.example.tf_face

import android.app.ActivityManager
import android.app.IActivityManager
import android.content.Context
import android.content.pm.UserInfo
import android.os.RemoteException
import android.os.ServiceManager
import android.os.UserHandle
import android.os.UserManager
import android.util.Log

class UserManagerHelper(context: Context) {

    private val userManager: UserManager = context.getSystemService(UserManager::class.java)!!
    private val activityManager: IActivityManager? =
        IActivityManager.Stub.asInterface(ServiceManager.getService(Context.ACTIVITY_SERVICE))

    fun listUsers(): List<UserInfo> {
        return userManager.users
    }

        fun getGuestUserId(): Int {
        try {
            val users = userManager.users
            for (user in users) {
                if (user.isGuest()) {
                    Log.d(TAG, "Found existing guest user with ID: ${user.id}")
                    return user.id
                }
            }
            Log.w(TAG, "No guest user found")
            return -1
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving guest user: ${e.message}")
            return -1
        }
    }

    fun createNewUser(name: String, isGuest: Boolean = false): UserInfo? {
        return try {
            if (isGuest && getGuestUserId() != -1) {
                Log.e(TAG, "Cannot create guest user: a guest user already exists")
                return null
            }
            val userType = if (isGuest) UserManager.USER_TYPE_FULL_GUEST else UserManager.USER_TYPE_FULL_SECONDARY
            val user = userManager.createUser(name, userType, 0)
            if (user != null) {
                Log.d(TAG, "User created: ${user.name} (ID: ${user.id}, Guest: $isGuest)")
            } else {
                Log.e(TAG, "Failed to create user: null")
            }
            user
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create user: ${e.message}")
            null
        }
    }

    fun switchUser(userId: Int): Boolean {
        return try {
            Log.d(TAG, "Switching to user ID: $userId")
            val result = activityManager?.switchUser(userId) ?: false
            if (result) {
                Log.d(TAG, "Switched to user ID: $userId")
            } else {
                Log.e(TAG, "SwitchUser returned false")
            }
            result
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to switch user: ${e.message}")
            false
        }
    }

    fun deleteUser(userId: Int): Boolean {
        if (userId == getCurrentUserId()) {
            userManager.removeUserWhenPossible(UserHandle.of(userId), false)
            activityManager?.switchUser(0)
        }
        if (userId == 0) {
            Log.e(TAG, "Cannot delete system user (user 0)")
            return false
        }

        return try {
            val result = userManager.removeUser(userId)
            if (result) {
                Log.d(TAG, "User deleted: ID $userId")
            } else {
                Log.e(TAG, "Failed to delete user ID $userId")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Exception while deleting user: ${e.message}")
            false
        }
    }

    fun getCurrentUserId(): Int {
        return try {
            UserHandle.myUserId().also {
                Log.d(TAG, "Current user ID: $it")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current user ID: ${e.message}")
            -1
        }
    }

    companion object {
        private const val TAG = "UserManagerHelper"
    }
}

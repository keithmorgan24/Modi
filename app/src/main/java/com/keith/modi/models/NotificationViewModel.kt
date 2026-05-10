package com.keith.modi.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keith.modi.Supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationViewModel : ViewModel() {
    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        fetchNotifications()
    }

    fun fetchNotifications() {
        val userId = Supabase.client.auth.currentUserOrNull()?.id ?: return
        
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val list = Supabase.client.postgrest["notifications"]
                    .select {
                        filter { eq("user_id", userId) }
                    }.decodeList<Notification>()
                _notifications.value = list.sortedByDescending { it.createdAt }
            } catch (e: Exception) {
                // Handle error silently or via state
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            try {
                Supabase.client.postgrest["notifications"].update(
                    mapOf("is_read" to true)
                ) {
                    filter { eq("id", notificationId) }
                }
                _notifications.value = _notifications.value.map {
                    if (it.id == notificationId) it.copy(isRead = true) else it
                }
            } catch (e: Exception) {}
        }
    }
}

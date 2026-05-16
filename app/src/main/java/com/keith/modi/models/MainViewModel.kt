package com.keith.modi.models

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.keith.modi.Supabase
import com.keith.modi.utils.NetworkUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _userProfile = MutableStateFlow<Profile?>(null)
    val userProfile: StateFlow<Profile?> = _userProfile.asStateFlow()

    private val _appRelease = MutableStateFlow<AppRelease?>(null)
    val appRelease: StateFlow<AppRelease?> = _appRelease.asStateFlow()

    private val _currentRole = MutableStateFlow(UserRole.CUSTOMER)
    val currentRole: StateFlow<UserRole> = _currentRole.asStateFlow()

    private val _isGuest = MutableStateFlow(false)
    val isGuest: StateFlow<Boolean> = _isGuest.asStateFlow()

    private val _isOnline = MutableStateFlow(NetworkUtils.isNetworkAvailable(application))
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isMapViewActive = MutableStateFlow(false)
    val isMapViewActive: StateFlow<Boolean> = _isMapViewActive.asStateFlow()

    private val _showOfflineDialog = MutableStateFlow(false)
    val showOfflineDialog: StateFlow<Boolean> = _showOfflineDialog.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    
    // PENDO: Fail-Safe Cache Initialization
    private val prefs: android.content.SharedPreferences = try {
        val masterKey = MasterKey.Builder(application)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            application,
            "modi_cache",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        application.getSharedPreferences("modi_cache_fallback", android.content.Context.MODE_PRIVATE)
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
            if (_userProfile.value == null) {
                fetchUserProfile()
            }
        }

        override fun onLost(network: Network) {
            _isOnline.value = false
        }
    }

    init {
        // PENDO: Pre-fetch session status for immediate UI consistency
        updateGuestStatus()
        loadCachedProfile()
        observeSessionStatus()
        observeNetwork()
        checkForUpdates()
    }

    private fun checkForUpdates() {
        viewModelScope.launch {
            try {
                // PENDO: Robust query for the latest release, ordering by version then creation date
                val latestRelease = try {
                    Supabase.client.postgrest["app_releases"]
                        .select {
                            order("version_code", Order.DESCENDING)
                            order("created_at", Order.DESCENDING)
                            limit(1)
                        }
                        .decodeList<AppRelease>()
                        .firstOrNull()
                } catch (e: Exception) {
                    println("[UPDATE] Query failed: ${e.message}")
                    null
                }
                
                if (latestRelease == null) {
                    println("[UPDATE] No release information found in database.")
                    return@launch
                }

                val context = getApplication<Application>()
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }

                println("[UPDATE] Local version: $currentVersionCode, Remote version: ${latestRelease.versionCode}")

                if (latestRelease.versionCode > currentVersionCode) {
                    // PENDO: Intelligent URL resolution - Ensure the APK path is a valid public URL
                    val resolvedPath = if (latestRelease.apkPath.startsWith("http")) {
                        latestRelease.apkPath
                    } else {
                        try {
                            Supabase.client.storage["app-distribution"].publicUrl(latestRelease.apkPath)
                        } catch (e: Exception) {
                            println("[UPDATE] Storage resolution failed: ${e.message}")
                            latestRelease.apkPath // Fallback to raw path
                        }
                    }
                    
                    val finalizedRelease = latestRelease.copy(apkPath = resolvedPath)
                    
                    println("[UPDATE] New version found: ${finalizedRelease.versionName} (${finalizedRelease.versionCode})")
                    _appRelease.value = finalizedRelease
                } else {
                    println("[UPDATE] App is up to date.")
                    _appRelease.value = null
                }
            } catch (e: Exception) {
                println("[UPDATE] Check failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun dismissUpdateDialog() {
        _appRelease.value = null
    }

    private fun updateGuestStatus() {
        viewModelScope.launch {
            val user = Supabase.client.auth.currentUserOrNull()
            val isAnonymous = (user != null && user.email == null)
            val persistentGuest = prefs.getBoolean("persistent_guest_mode", false)
            
            // PENDO: Data Integrity - Cross-reference session with persistent flag
            _isGuest.value = isAnonymous || (persistentGuest && user == null)
            
            if (_isGuest.value) {
                _userProfile.value = null
            }
        }
    }

    private fun observeNetwork() {
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    override fun onCleared() {
        super.onCleared()
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {}
    }

    private fun observeSessionStatus() {
        viewModelScope.launch {
            Supabase.client.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    fetchUserProfile()
                    listenToProfileChanges() // Start listening only when authenticated
                } else if (status is SessionStatus.NotAuthenticated) {
                    _userProfile.value = null
                    clearCachedProfile()
                }
            }
        }
    }

    fun setGuestMode(isGuest: Boolean) {
        println("[GUEST_DEBUG] setGuestMode called with: $isGuest")
        _isGuest.value = isGuest
        prefs.edit { putBoolean("persistent_guest_mode", isGuest) }
        if (isGuest) {
            _userProfile.value = null
            clearCachedProfile()
        }
    }

    fun toggleRole() {
        val baseRole = _userProfile.value?.role ?: "CUSTOMER"
        if (baseRole != "HOST" && _currentRole.value == UserRole.CUSTOMER) {
            // PENDO: Prevent customer from switching to host if they don't have the role in DB
            println("[SECURITY] Customer attempted to switch to hosting without authorization")
            return
        }
        _currentRole.value = if (_currentRole.value == UserRole.CUSTOMER) UserRole.HOST else UserRole.CUSTOMER
    }

    fun fetchUserProfile() {
        viewModelScope.launch {
            try {
                val user = Supabase.client.auth.currentUserOrNull()
                val isAnonymous = (user != null && user.email == null)
                val persistentGuest = prefs.getBoolean("persistent_guest_mode", false)
                
                // PENDO: High-Security Guest State Determination
                val resolvedGuestState = isAnonymous || (persistentGuest && user == null)
                println("[GUEST_DEBUG] fetchUserProfile resolvedGuestState: $resolvedGuestState (isAnon: $isAnonymous, persistent: $persistentGuest)")
                
                _isGuest.value = resolvedGuestState

                if (resolvedGuestState) {
                    _userProfile.value = null
                    clearCachedProfile()
                    return@launch
                }

                val userId = user?.id
                if (userId != null) {
                    try {
                        val profile = Supabase.client.postgrest["profiles"]
                            .select { filter { eq("id", userId) } }
                            .decodeSingle<Profile>()
                        _userProfile.value = profile
                        cacheProfile(profile)
                        
                        try {
                            _currentRole.value = UserRole.valueOf(profile.role)
                        } catch (e: Exception) {
                            _currentRole.value = UserRole.CUSTOMER
                        }
                    } catch (e: Exception) {
                        val name = user.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
                        val avatar = user.userMetadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull
                        val roleStr = user.userMetadata?.get("role")?.jsonPrimitive?.contentOrNull ?: "CUSTOMER"
                        
                        val fallbackProfile = Profile(
                            id = userId,
                            fullName = name,
                            avatarUrl = avatar,
                            role = roleStr
                        )
                        _userProfile.value = fallbackProfile
                        cacheProfile(fallbackProfile)
                        
                        try {
                            _currentRole.value = UserRole.valueOf(roleStr)
                        } catch (re: Exception) {
                            _currentRole.value = UserRole.CUSTOMER
                        }
                    }
                } else {
                    if (_isOnline.value) {
                        _userProfile.value = null
                    }
                }
            } catch (e: Exception) {
                println("Error in fetchUserProfile: ${e.message}")
            }
        }
    }

    private fun cacheProfile(profile: Profile) {
        prefs.edit { putString("cached_profile", json.encodeToString(profile)) }
    }

    private fun loadCachedProfile() {
        val profileStr = prefs.getString("cached_profile", null)
        if (profileStr != null) {
            try {
                _userProfile.value = json.decodeFromString<Profile>(profileStr)
            } catch (e: Exception) {}
        }
    }

    private fun clearCachedProfile() {
        prefs.edit { remove("cached_profile") }
    }

    fun showOfflineNotice() {
        _showOfflineDialog.value = true
    }

    fun dismissOfflineNotice() {
        _showOfflineDialog.value = false
    }

    fun updateOnlineStatus(online: Boolean) {
        _isOnline.value = online
        if (online && _userProfile.value == null) {
            fetchUserProfile()
        }
    }

    private fun listenToProfileChanges() {
        viewModelScope.launch {
            val userId = Supabase.client.auth.currentUserOrNull()?.id ?: return@launch
            
            // PENDO: Robust Realtime initialization
            // We use a unique channel ID per ViewModel instance to avoid "already joined" errors
            val channelId = "profile_changes_${System.currentTimeMillis()}"
            val channel = Supabase.client.channel(channelId)
            
            try {
                val profileFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") { 
                    table = "profiles" 
                }

                profileFlow.onEach { action ->
                    if (action is PostgresAction.Update) {
                        val updatedProfile = action.decodeRecord<Profile>()
                        if (updatedProfile.id == userId) {
                            _userProfile.value = updatedProfile
                            cacheProfile(updatedProfile)
                        }
                    }
                }.launchIn(viewModelScope)

                channel.subscribe()
            } catch (e: Exception) {
                // If it fails, we still have the initial fetch and polling options
                println("[REALTIME] Profile listener failed: ${e.message}")
            }
        }
    }
}

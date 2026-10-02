package com.nuvio.app.features.profiles

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.auth.isAnonymous
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.core.sync.ProfileSettingsSync
import com.nuvio.app.core.sync.putSyncOriginClientId
import com.nuvio.app.core.tracking.ensureTrackingProvidersRegistered
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.collection.CollectionMobileSettingsRepository
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.core.ui.CardDepthStyleRepository
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.LibraryDisplaySettingsRepository
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsRepository
import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.search.SearchHistoryRepository
import com.nuvio.app.features.search.SearchRepository
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.streams.StreamBadgeSettingsRepository
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

@Serializable
private data class StoredProfilePayload(
    val userId: String,
    val activeProfileIndex: Int = 1,
    val hasEverSelectedProfile: Boolean = false,
    val rememberLastProfileEnabled: Boolean = false,
    val profiles: List<NuvioProfile> = emptyList(),
)

object ProfileRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("ProfileRepository")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun localizedString(resource: StringResource): String = runBlocking { getString(resource) }

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private var activeProfileIndex: Int = 1
    private var loadedCacheForUserId: String? = null

    val activeProfileId: Int get() = activeProfileIndex

    fun setRememberLastProfileEnabled(enabled: Boolean) {
        if (_state.value.rememberLastProfileEnabled == enabled) return
        _state.value = _state.value.copy(rememberLastProfileEnabled = enabled)
        persist()
    }

    fun loadCachedProfiles(): Boolean {
        val stored = decodeStoredPayload() ?: return false
        loadedCacheForUserId = stored.userId
        applyStoredPayload(stored)
        ThemeSettingsRepository.onProfileChanged()
        return _state.value.profiles.isNotEmpty()
    }

    fun ensureLoaded(userId: String) {
        if (loadedCacheForUserId == userId && _state.value.isLoaded) return
        val stored = decodeStoredPayload()
        loadedCacheForUserId = userId
        if (stored == null || stored.userId != userId) {
            _state.value = ProfileState()
            activeProfileIndex = 1
            return
        }
        applyStoredPayload(stored)
    }

    fun clearInMemory() {
        loadedCacheForUserId = null
        activeProfileIndex = 1
        _state.value = ProfileState()
    }

    suspend fun pullProfiles() {
        if (AuthRepository.state.value.isAnonymous) {
            if (!_state.value.isLoaded) _state.value = _state.value.copy(isLoaded = true)
            return
        }
        try {
            val result = SupabaseProvider.client.postgrest.rpc("sync_pull_profiles")
            val profiles = result.decodeList<NuvioProfile>()
            _state.value = _state.value.copy(
                profiles = profiles.sortedBy { it.profileIndex }, isLoaded = true,
                activeProfile = profiles.find { it.profileIndex == activeProfileIndex } ?: profiles.firstOrNull(),
            )
            if (_state.value.activeProfile != null) activeProfileIndex = _state.value.activeProfile!!.profileIndex
            persist()
        } catch (e: Throwable) {
            if (AuthRepository.signOutIfSessionInvalid(e, "Profile pull")) return
            log.e(e) { "Failed to pull profiles" }
            if (!_state.value.isLoaded) _state.value = _state.value.copy(isLoaded = true)
        }
    }

    fun selectProfile(profileIndex: Int) {
        activeProfileIndex = profileIndex
        val selectedProfile = _state.value.profiles.find { it.profileIndex == profileIndex }
        _state.value = _state.value.copy(activeProfile = selectedProfile, hasEverSelectedProfile = selectedProfile != null || _state.value.hasEverSelectedProfile)
        persist()
        WatchedRepository.onProfileChanged(profileIndex)
        TrackingSettingsRepository.onProfileChanged(); ensureTrackingProvidersRegistered(); TrackingProviderRegistry.onProfileChanged()
        LibraryRepository.onProfileChanged(profileIndex); LibraryDisplaySettingsRepository.onProfileChanged(); WatchProgressRepository.onProfileChanged(profileIndex)
        AddonRepository.onProfileChanged(profileIndex)
        if (com.nuvio.app.core.build.AppFeaturePolicy.pluginsEnabled) PluginRepository.onProfileChanged(profileIndex)
        ThemeSettingsRepository.onProfileChanged(); PosterCardStyleRepository.onProfileChanged(); CardDepthStyleRepository.onProfileChanged()
        PlayerSettingsRepository.onProfileChanged(); StreamBadgeSettingsRepository.onProfileChanged(); P2pSettingsRepository.onProfileChanged()
        HomeCatalogSettingsRepository.onProfileChanged(); HomeRepository.clear(); MetaScreenSettingsRepository.onProfileChanged()
        ContinueWatchingPreferencesRepository.onProfileChanged(); com.nuvio.app.features.watchprogress.ContinueWatchingEnrichmentCache.onProfileChanged()
        EpisodeReleaseNotificationsRepository.onProfileChanged(); TmdbSettingsRepository.onProfileChanged(); MdbListSettingsRepository.onProfileChanged()
        SearchHistoryRepository.onProfileChanged(); SearchRepository.reset(); CollectionRepository.onProfileChanged(); CollectionMobileSettingsRepository.onProfileChanged()
        DownloadsRepository.onProfileChanged(); ProfileSettingsSync.onProfileChanged()
    }

    suspend fun pushProfiles(profiles: List<ProfilePushPayload>) {
        if (AuthRepository.state.value.isAnonymous) { applyPayloadsLocally(profiles); return }
        try {
            val params = buildJsonObject { put("p_client_max_profiles", MAX_PROFILES); put("p_profiles", json.encodeToJsonElement(profiles)); putSyncOriginClientId() }
            SupabaseProvider.client.postgrest.rpc("sync_push_profiles", params); pullProfiles()
        } catch (e: Throwable) {
            if (AuthRepository.signOutIfSessionInvalid(e, "Profile push")) return
            log.e(e) { "Failed to push profiles" }
        }
    }

    suspend fun createProfile(name: String, avatarColorHex: String, avatarId: String? = null, avatarUrl: String? = null, usesPrimaryAddons: Boolean = false, contentMode: IvyPlayContentMode = IvyPlayContentMode.STANDARD) {
        val existing = _state.value.profiles
        val nextIndex = ((1..MAX_PROFILES).toSet() - existing.map { it.profileIndex }.toSet()).minOrNull() ?: return
        val allPayloads = existing.map { profile ->
            ProfilePushPayload(profile.profileIndex, profile.name, profile.avatarColorHex, profile.usesPrimaryAddons, profile.usesPrimaryPlugins, profile.avatarId, profile.avatarUrl, profile.profileBackgroundId, profile.profileBackgroundUrl, profile.contentMode)
        } + ProfilePushPayload(nextIndex, name, avatarColorHex, usesPrimaryAddons = usesPrimaryAddons, avatarId = avatarId, avatarUrl = avatarUrl, contentMode = contentMode)
        pushProfiles(allPayloads)
    }

    suspend fun updateProfile(profileIndex: Int, name: String, avatarColorHex: String, avatarId: String? = null, avatarUrl: String? = null, profileBackgroundId: String? = null, profileBackgroundUrl: String? = null, usesPrimaryAddons: Boolean = false, contentMode: IvyPlayContentMode = IvyPlayContentMode.STANDARD) {
        val allPayloads = _state.value.profiles.map { profile ->
            if (profile.profileIndex == profileIndex) ProfilePushPayload(profileIndex, name, avatarColorHex, usesPrimaryAddons = usesPrimaryAddons, avatarId = avatarId, avatarUrl = avatarUrl, profileBackgroundId = profileBackgroundId, profileBackgroundUrl = profileBackgroundUrl, contentMode = contentMode)
            else ProfilePushPayload(profile.profileIndex, profile.name, profile.avatarColorHex, profile.usesPrimaryAddons, profile.usesPrimaryPlugins, profile.avatarId, profile.avatarUrl, profile.profileBackgroundId, profile.profileBackgroundUrl, profile.contentMode)
        }
        pushProfiles(allPayloads)
    }

    suspend fun deleteProfile(profileIndex: Int) {
        if (AuthRepository.state.value.isAnonymous) {
            val remaining = _state.value.profiles.filter { it.profileIndex != profileIndex }; ProfilePinCacheStorage.removePayload(profileIndex)
            _state.value = _state.value.copy(profiles = remaining, activeProfile = if (_state.value.activeProfile?.profileIndex == profileIndex) remaining.firstOrNull() else _state.value.activeProfile)
            if (_state.value.activeProfile != null) activeProfileIndex = _state.value.activeProfile!!.profileIndex
            persist(); return
        }
        try { val params = buildJsonObject { put("p_profile_id", profileIndex); putSyncOriginClientId() }; SupabaseProvider.client.postgrest.rpc("sync_delete_profile_data", params); pullProfiles() }
        catch (e: Throwable) { if (AuthRepository.signOutIfSessionInvalid(e, "Profile delete")) return; log.e(e) { "Failed to delete profile $profileIndex" } }
    }

    suspend fun verifyPin(profileIndex: Int, pin: String): PinVerifyResult {
        if (AuthRepository.state.value !is AuthState.Authenticated) return verifyPinLocally(profileIndex, pin)
        return runCatching { val params = buildJsonObject { put("p_profile_id", profileIndex); put("p_pin", pin) }; val result = SupabaseProvider.client.postgrest.rpc("verify_profile_pin", params); result.decodeSingle<PinVerifyResult>().also { if (it.unlocked) { pullProfiles(); rememberVerifiedPin(profileIndex, pin) } } }
            .getOrElse { e -> log.e(e) { "Failed to verify pin" }; verifyPinLocally(profileIndex, pin) }
    }

    suspend fun setPin(profileIndex: Int, pin: String, currentPin: String? = null): PinVerifyResult {
        if (AuthRepository.state.value !is AuthState.Authenticated) return PinVerifyResult(unlocked = false, message = getString(Res.string.profile_pin_set_requires_internet))
        return runCatching { val params = buildJsonObject { put("p_profile_id", profileIndex); put("p_pin", pin); currentPin?.let { put("p_current_pin", it) } }; SupabaseProvider.client.postgrest.rpc("set_profile_pin", params); pullProfiles(); rememberVerifiedPin(profileIndex, pin); PinVerifyResult(unlocked = true) }
            .onFailure { log.e(it) { "Failed to set pin" } }.getOrElse { PinVerifyResult(unlocked = false, message = getString(Res.string.profile_pin_set_failed)) }
    }

    suspend fun clearPin(profileIndex: Int, currentPin: String? = null): PinVerifyResult {
        if (AuthRepository.state.value !is AuthState.Authenticated) return PinVerifyResult(unlocked = false, message = getString(Res.string.profile_pin_clear_requires_internet))
        return runCatching { val params = buildJsonObject { put("p_profile_id", profileIndex); currentPin?.let { put("p_current_pin", it) } }; SupabaseProvider.client.postgrest.rpc("clear_profile_pin", params); pullProfiles(); ProfilePinCacheStorage.removePayload(profileIndex); PinVerifyResult(unlocked = true) }
            .onFailure { log.e(it) { "Failed to clear pin" } }.getOrElse { PinVerifyResult(unlocked = false, message = getString(Res.string.profile_pin_clear_failed)) }
    }

    suspend fun clearPinWithPassword(profileIndex: Int, accountPassword: String) { runCatching { val params = buildJsonObject { put("p_account_password", accountPassword); put("p_profile_id", profileIndex) }; SupabaseProvider.client.postgrest.rpc("clear_profile_pin_with_account_password", params); pullProfiles(); ProfilePinCacheStorage.removePayload(profileIndex) }.onFailure { log.e(it) { "Failed to clear pin with password" } } }
    suspend fun pullProfileLocks(): List<ProfileLockState> = runCatching { SupabaseProvider.client.postgrest.rpc("sync_pull_profile_locks").decodeList<ProfileLockState>() }.getOrElse { log.e(it) { "Failed to pull profile locks" }; emptyList() }

    private fun applyPayloadsLocally(payloads: List<ProfilePushPayload>) {
        val authState = AuthRepository.state.value as? AuthState.Authenticated ?: return
        val profiles = payloads.map { p -> NuvioProfile(id = "", userId = authState.userId, profileIndex = p.profileIndex, name = p.name, avatarColorHex = p.avatarColorHex, avatarId = p.avatarId, avatarUrl = p.avatarUrl, profileBackgroundId = p.profileBackgroundId, profileBackgroundUrl = p.profileBackgroundUrl, usesPrimaryAddons = p.usesPrimaryAddons, usesPrimaryPlugins = p.usesPrimaryPlugins, contentMode = p.contentMode) }.sortedBy { it.profileIndex }
        _state.value = _state.value.copy(profiles = profiles, isLoaded = true, activeProfile = profiles.find { it.profileIndex == activeProfileIndex } ?: profiles.firstOrNull())
        if (_state.value.activeProfile != null) activeProfileIndex = _state.value.activeProfile!!.profileIndex
        syncPinCache(profiles); persist()
    }

    private fun decodeStoredPayload(): StoredProfilePayload? { val payload = ProfileStorage.loadPayload().orEmpty().trim(); if (payload.isEmpty()) return null; return runCatching { json.decodeFromString<StoredProfilePayload>(payload) }.getOrNull() }
    private fun applyStoredPayload(stored: StoredProfilePayload) { val profiles = stored.profiles.sortedBy { it.profileIndex }; activeProfileIndex = stored.activeProfileIndex; _state.value = ProfileState(profiles, profiles.find { it.profileIndex == activeProfileIndex } ?: profiles.firstOrNull(), profiles.isNotEmpty(), stored.hasEverSelectedProfile, stored.rememberLastProfileEnabled); _state.value.activeProfile?.let { activeProfileIndex = it.profileIndex }; syncPinCache(profiles) }
    private fun rememberVerifiedPin(profileIndex: Int, pin: String) { val profile = _state.value.profiles.find { it.profileIndex == profileIndex }; val salt = generateProfilePinSalt(); ProfilePinCacheStorage.savePayload(profileIndex, json.encodeToString(CachedProfilePinPayload(salt, hashProfilePin(profileIndex, salt, pin), profile?.updatedAt.orEmpty()))) }
    private fun verifyPinLocally(profileIndex: Int, pin: String): PinVerifyResult { val profile = _state.value.profiles.find { it.profileIndex == profileIndex }; if (profile?.pinEnabled != true) return PinVerifyResult(unlocked = true); val payload = ProfilePinCacheStorage.loadPayload(profileIndex).orEmpty().trim(); if (payload.isEmpty()) return PinVerifyResult(false, message = localizedString(Res.string.profile_pin_offline_verification_requires_online)); val cached = runCatching { json.decodeFromString<CachedProfilePinPayload>(payload) }.getOrNull() ?: return PinVerifyResult(false, message = localizedString(Res.string.profile_pin_offline_verification_requires_online)); if (cached.profileUpdatedAt.isNotBlank() && profile.updatedAt.isNotBlank() && cached.profileUpdatedAt != profile.updatedAt) { ProfilePinCacheStorage.removePayload(profileIndex); return PinVerifyResult(false, message = localizedString(Res.string.profile_pin_changed_requires_refresh)) }; return if (hashProfilePin(profileIndex, cached.salt, pin) == cached.digest) PinVerifyResult(true) else PinVerifyResult(false, message = localizedString(Res.string.pin_incorrect)) }
    private fun syncPinCache(profiles: List<NuvioProfile>) { val profilesByIndex = profiles.associateBy { it.profileIndex }; for (profileIndex in 1..MAX_PROFILES) { val profile = profilesByIndex[profileIndex]; if (profile == null || !profile.pinEnabled) { ProfilePinCacheStorage.removePayload(profileIndex); continue }; val raw = ProfilePinCacheStorage.loadPayload(profileIndex).orEmpty().trim(); if (raw.isEmpty()) continue; val cached = runCatching { json.decodeFromString<CachedProfilePinPayload>(raw) }.getOrNull() ?: run { ProfilePinCacheStorage.removePayload(profileIndex); continue }; if (cached.profileUpdatedAt.isNotBlank() && profile.updatedAt.isNotBlank() && cached.profileUpdatedAt != profile.updatedAt) ProfilePinCacheStorage.removePayload(profileIndex) } }
    private fun persist() { val authState = AuthRepository.state.value as? AuthState.Authenticated ?: return; val state = _state.value; ProfileStorage.savePayload(json.encodeToString(StoredProfilePayload(authState.userId, activeProfileIndex, state.hasEverSelectedProfile, state.rememberLastProfileEnabled, state.profiles))) }
}

@kotlinx.serialization.Serializable
data class ProfileLockState(@kotlinx.serialization.SerialName("profile_index") val profileIndex: Int, @kotlinx.serialization.SerialName("pin_enabled") val pinEnabled: Boolean = false, @kotlinx.serialization.SerialName("pin_locked_until") val pinLockedUntil: String? = null)

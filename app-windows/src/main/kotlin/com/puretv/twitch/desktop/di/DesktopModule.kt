package com.puretv.twitch.desktop.di

import com.puretv.twitch.desktop.auth.DesktopOAuthManager
import com.puretv.twitch.desktop.data.DesktopSettingsStore
import com.puretv.twitch.desktop.data.FollowStore
import com.puretv.twitch.desktop.data.ViewPrefsStore
import com.puretv.twitch.desktop.data.HistoryEntry
import com.puretv.twitch.desktop.discover.DiscoverRepository
import com.puretv.twitch.desktop.discover.TwitchDirectoryGql
import com.puretv.twitch.desktop.channel.RaidWatcher
import com.puretv.twitch.core.di.TokenHolder
import com.puretv.twitch.core.api.TwitchApiClient
import com.puretv.twitch.core.repository.ChannelRepository
import com.puretv.twitch.desktop.data.ViewerHistoryStore
import com.puretv.twitch.desktop.data.WatchProgressStore
import com.puretv.twitch.core.model.PlaybackBackend
import com.puretv.twitch.desktop.player.DesktopPlayer
import com.puretv.twitch.desktop.player.LocalStreamProxy
import com.puretv.twitch.desktop.player.MpvPlayer
import com.puretv.twitch.desktop.player.VlcPlayer
import com.puretv.twitch.desktop.update.UpdateManager
import com.puretv.twitch.core.follows.FollowedChannelsService
import com.puretv.twitch.core.follows.FollowedChannelsSource
import com.puretv.twitch.core.follows.FollowedRef
import com.puretv.twitch.desktop.ui.BrowseViewModel
import com.puretv.twitch.desktop.ui.CategoryViewModel
import com.puretv.twitch.desktop.ui.ChannelStatsViewModel
import com.puretv.twitch.desktop.ui.ChannelViewModel
import com.puretv.twitch.desktop.ui.DiscoverViewModel
import com.puretv.twitch.desktop.ui.FollowedRailViewModel
import com.puretv.twitch.desktop.ui.HomeViewModel
import com.puretv.twitch.desktop.ui.LoginViewModel
import com.puretv.twitch.desktop.ui.SearchViewModel
import com.puretv.twitch.desktop.ui.SettingsViewModel
import com.puretv.twitch.desktop.ui.emotes.EmoteFrameCache
import com.puretv.twitch.desktop.ui.StreamViewModel
import com.puretv.twitch.desktop.ui.VodListViewModel
import com.puretv.twitch.desktop.ui.VodLaunch
import com.puretv.twitch.desktop.ui.VodChatViewModel
import com.puretv.twitch.desktop.ui.VodPlayerViewModel
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/**
 * SECTION 11/12.4 — Windows-specific Koin bindings layered on top of
 * `coreModule` (shared API client, repositories, ad-block engine, chat
 * client — Sections 3–5). Mirrors `androidModule`/`tvModule` in shape;
 * differs in *what* backs persistence (file-based, not Room/DataStore) and
 * playback (VLCJ + local proxy, not ExoPlayer).
 *
 * NOTE — ViewModels are bound with `factory { }`, not Koin's `viewModel { }`
 * DSL (that's an AndroidX-lifecycle integration this plain-JVM module doesn't
 * pull in). Screens obtain them via `rememberDesktopViewModel { koin.get() }`
 * / `rememberDesktopViewModel(login) { koin.get { parametersOf(login) } }`.
 */
val desktopModule = module {
    // --- Networking engine (coreModule provides `single { buildKtorClient(get()) }`,
    // which resolves an `HttpClientEngine` — OkHttp here, matching Android/TV). ---
    single<HttpClientEngine> { OkHttp.create() }

    // --- Persistence -------------------------------------------------------
    // Takes TokenHolder (from coreModule) so saved tokens flow back into the
    // shared HttpClient auth path on both fresh login and cold-start restore.
    single { DesktopSettingsStore(get()) }
    // Local "Following" list (the in-app library) — see FollowStore.
    single { FollowStore() }
    // Browsing prefs: list sort order and the last Discover filters — see ViewPrefsStore.
    single { ViewPrefsStore() }
    // Followed-rail data: real Twitch follows (paginated) ∪ local pins.
    single<FollowedChannelsSource> { FollowedChannelsService(get()) }
    // Per-VOD playback positions ("continue watching") — see WatchProgressStore.
    single { WatchProgressStore() }
    // Locally-persisted viewer-count history for the channel stats panel — see ViewerHistoryStore.
    single { ViewerHistoryStore() }
    // In-app auto-updater (GitHub Releases) — see UpdateManager.
    single { UpdateManager() }
    single { EmoteFrameCache(get()) }

    // --- Playback ------------------------------------------------------------
    single<DesktopPlayer> {
        val store = get<DesktopSettingsStore>()
        if (store.settings.value.playbackBackend == PlaybackBackend.MPV) MpvPlayer(store) else VlcPlayer()
    }
    // LocalStreamProxy takes HttpClient (variant fetches) + BackupStreamResolver
    // (the player-type swap that actually removes ads) on top of StreamRepository
    // and AdBlockEngine — see the /variant route.
    single { LocalStreamProxy(get(), get(), get(), get()) }

    // --- Auth ------------------------------------------------------------------
    single { DesktopOAuthManager() }

    // --- ViewModels (plain factories — see note above) -------------------------
    factory { HomeViewModel(get(), get(), get()) }
    factory {
        val store = get<DesktopSettingsStore>()
        val follows = get<FollowStore>()
        FollowedRailViewModel(
            source = get(),
            loggedInUserId = { if (store.isLoggedIn) store.sessionUserId else null },
            localPins = { follows.followed.value.map { FollowedRef(it.id, it.login, it.displayName) } },
        )
    }
    // Directory reads Helix can't do (category viewer totals, lowest-first stream scans).
    single { TwitchDirectoryGql(get()) }
    // TwitchTracker's public 30-day channel summary, for Stats for nerds.
    single { com.puretv.twitch.desktop.channel.TwitchTrackerClient(get()) }
    // Raid alerts over Twitch's EventSub WebSocket; needs the signed-in user's token.
    single { val holder = get<TokenHolder>(); RaidWatcher(get()) { holder.current() } }
    single { DiscoverRepository(get<TwitchApiClient>(), get<TwitchDirectoryGql>()) }
    factory {
        val gql = get<TwitchDirectoryGql>()
        BrowseViewModel(get(), viewerCounts = { ids -> gql.gameViewerCounts(ids) })
    }
    factory {
        val prefs = get<ViewPrefsStore>()
        val channels = get<ChannelRepository>()
        DiscoverViewModel(
            repository = get(),
            searchCategories = { q -> channels.searchCategories(q) },
            initialFilters = prefs.prefs.value.discover,
            persistFilters = { prefs.setDiscoverFilters(it) },
            followedLogins = {
                // Local follows plus real Twitch follows (when signed in).
                val local = get<FollowStore>().followed.value.map { it.login }
                val store = get<DesktopSettingsStore>()
                val userId = if (store.isLoggedIn) store.sessionUserId else null
                val remote = userId?.let { id ->
                    runCatching { get<TwitchApiClient>().getAllFollowedChannels(id).map { it.broadcaster_login } }.getOrDefault(emptyList())
                }.orEmpty()
                (local + remote).toSet()
            },
        )
    }
    // Category drill-down takes (gameId, gameName) from the tapped Browse card.
    factory { (gameId: String, gameName: String) -> CategoryViewModel(gameId, gameName, get()) }
    factory { SearchViewModel(get()) }
    factory { (channelLogin: String) ->
        val prefs = get<ViewPrefsStore>()
        StreamViewModel(
            channelLogin, get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            raids = get(),
            onWatched = { ch, info ->
                prefs.recordWatch(
                    HistoryEntry(
                        login = ch.login,
                        displayName = ch.displayName,
                        avatarUrl = ch.profileImageUrl,
                        gameName = info?.gameName.orEmpty(),
                        watchedAt = System.currentTimeMillis(),
                    ),
                )
            },
        )
    }
    factory { (channelLogin: String) -> ChannelViewModel(channelLogin, get(), get(), get()) }
    factory { (channelLogin: String) -> ChannelStatsViewModel(channelLogin, get(), get()) }
    factory { SettingsViewModel(get()) }
    // LoginViewModel collaborators: settingsStore, oauthManager, httpClient,
    // tokenHolder (for "set token BEFORE first authed call"), apiClient (for
    // resolving the broadcaster's own userId/login via GET /users).
    factory { LoginViewModel(get(), get(), get(), get(), get()) }
    factory { (userId: String) -> VodListViewModel(userId, get()) }
    factory { (launch: VodLaunch) -> VodPlayerViewModel(launch, get(), get(), get(), get()) }
    factory { (vodId: String, channelLogin: String) -> VodChatViewModel(vodId, channelLogin, get(), get(), get(), get()) }
}

/** Convenience for screens: `koin.get<StreamViewModel> { parametersOf(channelLogin) }`. */
internal inline fun <reified T : Any> org.koin.core.Koin.getWithParam(param: Any): T =
    get(parameters = { parametersOf(param) })

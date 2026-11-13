package com.taras.nexusplay.app.tv.main

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.taras.nexusplay.R
import com.taras.nexusplay.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.taras.nexusplay.app.shared.GameInteractor
import com.taras.nexusplay.app.shared.game.BaseGameActivity
import com.taras.nexusplay.app.shared.game.GameLauncher
import com.taras.nexusplay.app.shared.main.BusyActivity
import com.taras.nexusplay.app.shared.main.GameLaunchTaskHandler
import com.taras.nexusplay.app.tv.channel.ChannelUpdateWork
import com.taras.nexusplay.app.tv.favorites.TVFavoritesFragment
import com.taras.nexusplay.app.tv.games.TVGamesFragment
import com.taras.nexusplay.app.tv.home.TVHomeFragment
import com.taras.nexusplay.app.tv.search.TVSearchFragment
import com.taras.nexusplay.app.tv.shared.BaseTVActivity
import com.taras.nexusplay.app.tv.shared.TVHelper
import com.taras.nexusplay.common.coroutines.launchOnState
import com.taras.nexusplay.common.coroutines.safeCollect
import com.taras.nexusplay.common.coroutines.safeLaunch
import com.taras.nexusplay.lib.injection.PerActivity
import com.taras.nexusplay.lib.injection.PerFragment
import com.taras.nexusplay.lib.library.covers.CustomCoverManager
import com.taras.nexusplay.lib.library.db.RetrogradeDatabase
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class)
class MainTVActivity : BaseTVActivity(), BusyActivity {
    @Inject
    lateinit var gameLaunchTaskHandler: GameLaunchTaskHandler

    var mainViewModel: MainTVViewModel? = null

    override fun activity(): Activity = this

    override fun isBusy(): Boolean = mainViewModel?.inProgress?.value ?: false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_main)

        val factory = MainTVViewModel.Factory(applicationContext)
        mainViewModel = ViewModelProvider(this, factory).get(MainTVViewModel::class.java)

        launchOnState(Lifecycle.State.CREATED) {
            mainViewModel?.inProgress?.safeCollect {
                findViewById<View>(R.id.tv_loading).isVisible = it
            }
        }

        ensureLegacyStoragePermissionsIfNeeded()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            BaseGameActivity.REQUEST_PLAY_GAME -> {
                GlobalScope.safeLaunch {
                    gameLaunchTaskHandler.handleGameFinish(false, this@MainTVActivity, resultCode, data)
                    ChannelUpdateWork.enqueue(applicationContext)
                }
            }
        }
    }

    private fun ensureLegacyStoragePermissionsIfNeeded() {
        if (TVHelper.isSAFSupported(this) || hasLegacyPermissions()) {
            return
        }

        val requestPermission = ActivityResultContracts.RequestPermission()
        val requestPermissionLauncher =
            registerForActivityResult(requestPermission) { isGranted ->
                if (!isGranted) {
                    finish()
                }
            }
        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasLegacyPermissions(): Boolean {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    @dagger.Module
    abstract class Module {
        @PerFragment
        @ContributesAndroidInjector(modules = [TVHomeFragment.Module::class])
        abstract fun tvHomeFragment(): TVHomeFragment

        @PerFragment
        @ContributesAndroidInjector(modules = [TVGamesFragment.Module::class])
        abstract fun tvGamesFragment(): TVGamesFragment

        @PerFragment
        @ContributesAndroidInjector(modules = [TVSearchFragment.Module::class])
        abstract fun tvSearchFragment(): TVSearchFragment

        @PerFragment
        @ContributesAndroidInjector(modules = [TVFavoritesFragment.Module::class])
        abstract fun tvFavoritesFragment(): TVFavoritesFragment

        @dagger.Module
        companion object {
            @Provides
            @PerActivity
            @JvmStatic
            fun gameInteractor(
                activity: MainTVActivity,
                retrogradeDb: RetrogradeDatabase,
                shortcutsGenerator: ShortcutsGenerator,
                gameLauncher: GameLauncher,
                customCoverManager: CustomCoverManager,
            ) = GameInteractor(activity, retrogradeDb, true, shortcutsGenerator, gameLauncher, customCoverManager)
        }
    }
}

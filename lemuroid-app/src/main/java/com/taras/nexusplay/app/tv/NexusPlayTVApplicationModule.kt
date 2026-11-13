package com.taras.nexusplay.app.tv

import com.taras.nexusplay.app.tv.folderpicker.TVFolderPickerActivity
import com.taras.nexusplay.app.tv.folderpicker.TVFolderPickerLauncher
import com.taras.nexusplay.app.tv.game.TVGameActivity
import com.taras.nexusplay.app.tv.gamemenu.TVGameMenuActivity
import com.taras.nexusplay.app.tv.input.TVGamePadBindingActivity
import com.taras.nexusplay.app.tv.input.TVGamePadShortcutBindingActivity
import com.taras.nexusplay.app.tv.main.MainTVActivity
import com.taras.nexusplay.app.tv.settings.TVSettingsActivity
import com.taras.nexusplay.lib.injection.PerActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class NexusPlayTVApplicationModule {
    @PerActivity
    @ContributesAndroidInjector(modules = [MainTVActivity.Module::class])
    abstract fun tvMainActivity(): MainTVActivity

    @PerActivity
    @ContributesAndroidInjector
    abstract fun tvGameActivity(): TVGameActivity

    @PerActivity
    @ContributesAndroidInjector
    abstract fun tvGameMenuActivity(): TVGameMenuActivity

    @PerActivity
    @ContributesAndroidInjector
    abstract fun tvFolderPickerLauncher(): TVFolderPickerLauncher

    @PerActivity
    @ContributesAndroidInjector
    abstract fun tvFolderPickerActivity(): TVFolderPickerActivity

    @PerActivity
    @ContributesAndroidInjector
    abstract fun tvGamePadBindingActivity(): TVGamePadBindingActivity

    @PerActivity
    @ContributesAndroidInjector
    abstract fun tvGamePadShortcutBindingActivity(): TVGamePadShortcutBindingActivity

    @PerActivity
    @ContributesAndroidInjector(modules = [TVSettingsActivity.Module::class])
    abstract fun tvSettingsActivity(): TVSettingsActivity
}

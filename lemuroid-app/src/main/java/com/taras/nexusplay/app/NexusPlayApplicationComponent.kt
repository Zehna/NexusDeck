/*
 * RetrogradeApplicationComponent.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.taras.nexusplay.app

import com.taras.nexusplay.app.shared.library.CoreUpdateWork
import com.taras.nexusplay.app.shared.library.LibraryIndexWork
import com.taras.nexusplay.app.shared.savesync.SaveSyncWork
import com.taras.nexusplay.app.shared.storage.cache.CacheCleanerWork
import com.taras.nexusplay.app.tv.NexusPlayTVApplicationModule
import com.taras.nexusplay.app.tv.channel.ChannelUpdateWork
import com.taras.nexusplay.lib.injection.AndroidWorkerInjectionModule
import com.taras.nexusplay.lib.injection.PerApp
import dagger.Component
import dagger.android.AndroidInjector
import dagger.android.support.AndroidSupportInjectionModule

@Component(
    modules = [
        AndroidSupportInjectionModule::class,
        AndroidWorkerInjectionModule::class,
        NexusPlayApplicationModule::class,
        LibraryIndexWork.Module::class,
        SaveSyncWork.Module::class,
        ChannelUpdateWork.Module::class,
        CoreUpdateWork.Module::class,
        CacheCleanerWork.Module::class,
        NexusPlayTVApplicationModule::class,
    ],
)
@PerApp
interface NexusPlayApplicationComponent : AndroidInjector<NexusPlayApplication> {
    @Component.Builder
    abstract class Builder : AndroidInjector.Builder<NexusPlayApplication>()
}

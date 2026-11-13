package com.taras.nexusplay.app.shared.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.taras.nexusplay.app.mobile.shared.NotificationsManager
import com.taras.nexusplay.app.utils.android.createSyncForegroundInfo
import com.taras.nexusplay.lib.injection.AndroidWorkerInjection
import com.taras.nexusplay.lib.injection.WorkerKey
import com.taras.nexusplay.lib.library.LemuroidLibrary
import dagger.Binds
import dagger.android.AndroidInjector
import dagger.multibindings.IntoMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class LibraryIndexWork(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    @Inject
    lateinit var lemuroidLibrary: LemuroidLibrary

    override suspend fun doWork(): Result {
        AndroidWorkerInjection.inject(this)

        val notificationsManager = NotificationsManager(applicationContext)

        val foregroundInfo =
            createSyncForegroundInfo(
                NotificationsManager.LIBRARY_INDEXING_NOTIFICATION_ID,
                notificationsManager.libraryIndexingNotification(),
            )

        setForegroundAsync(foregroundInfo)

        val result =
            withContext(Dispatchers.IO) {
                kotlin.runCatching {
                    lemuroidLibrary.indexLibrary()
                }
            }

        result.exceptionOrNull()?.let {
            Timber.e("Library indexing work terminated with an exception:", it)
        }

        LibraryIndexScheduler.scheduleCoreUpdate(applicationContext)

        return Result.success()
    }

    @dagger.Module(subcomponents = [Subcomponent::class])
    abstract class Module {
        @Binds
        @IntoMap
        @WorkerKey(LibraryIndexWork::class)
        abstract fun bindMyWorkerFactory(builder: Subcomponent.Builder): AndroidInjector.Factory<out ListenableWorker>
    }

    @dagger.Subcomponent
    interface Subcomponent : AndroidInjector<LibraryIndexWork> {
        @dagger.Subcomponent.Builder
        abstract class Builder : AndroidInjector.Builder<LibraryIndexWork>()
    }
}

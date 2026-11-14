package com.taras.nexusplay.lib.injection

import androidx.work.ListenableWorker
import dagger.android.AndroidInjector

interface HasWorkerInjector {
    fun workerInjector(): AndroidInjector<ListenableWorker>
}

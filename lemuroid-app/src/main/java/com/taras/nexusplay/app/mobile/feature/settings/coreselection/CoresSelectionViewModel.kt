package com.taras.nexusplay.app.mobile.feature.settings.coreselection

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.taras.nexusplay.app.shared.library.LibraryIndexScheduler
import com.taras.nexusplay.app.shared.library.PendingOperationsMonitor
import com.taras.nexusplay.lib.core.CoresSelection
import com.taras.nexusplay.lib.library.GameSystem
import com.taras.nexusplay.lib.library.SystemCoreConfig
import kotlinx.coroutines.launch

class CoresSelectionViewModel(
    context: Context,
    private val coresSelection: CoresSelection,
) : ViewModel() {
    class Factory(
        val context: Context,
        val coresSelection: CoresSelection,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CoresSelectionViewModel(context, coresSelection) as T
        }
    }

    val indexingInProgress = PendingOperationsMonitor(context).anyLibraryOperationInProgress()

    fun getSelectedCores() = coresSelection.getSelectedCores()

    fun changeCore(
        system: GameSystem,
        coreConfig: SystemCoreConfig,
        context: Context,
    ) {
        viewModelScope.launch {
            coresSelection.updateCoreConfigForSystem(system, coreConfig.coreID)
            LibraryIndexScheduler.scheduleCoreUpdate(context)
        }
    }
}

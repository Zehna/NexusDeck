package com.taras.nexusplay.app.tv.favorites

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.paging.PagingDataAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.taras.nexusplay.R
import com.taras.nexusplay.app.shared.GameInteractor
import com.taras.nexusplay.app.tv.shared.GamePresenter
import com.taras.nexusplay.common.coroutines.launchOnState
import com.taras.nexusplay.lib.library.db.RetrogradeDatabase
import com.taras.nexusplay.lib.library.db.entity.Game
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

class TVFavoritesFragment : VerticalGridSupportFragment() {
    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var gameInteractor: GameInteractor

    init {
        val gridPresenter = VerticalGridPresenter()
        gridPresenter.numberOfColumns = 4
        setGridPresenter(gridPresenter)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val factory = TVFavoritesViewModel.Factory(retrogradeDb)
        val favoritesViewModel = ViewModelProvider(this, factory)[TVFavoritesViewModel::class.java]

        val cardSize = resources.getDimensionPixelSize(com.taras.nexusplay.lib.R.dimen.card_size)
        val pagingAdapter =
            PagingDataAdapter(
                GamePresenter(cardSize, gameInteractor),
                Game.DIFF_CALLBACK,
            )

        this.adapter = pagingAdapter

        launchOnState(Lifecycle.State.RESUMED) {
            favoritesViewModel.favorites
                .collect { pagingAdapter.submitData(lifecycle, it) }
        }

        onItemViewClickedListener =
            OnItemViewClickedListener { _, item, _, _ ->
                when (item) {
                    is Game -> gameInteractor.onGamePlay(item)
                }
            }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    @dagger.Module
    class Module
}

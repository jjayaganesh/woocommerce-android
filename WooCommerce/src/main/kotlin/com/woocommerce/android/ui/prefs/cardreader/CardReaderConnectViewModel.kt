package com.woocommerce.android.ui.prefs.cardreader

import com.woocommerce.android.di.ViewModelAssistedFactory
import com.woocommerce.android.ui.prefs.cardreader.CardReaderConnectViewModel.NavigationTarget.InitiateCardReaderScan
import com.woocommerce.android.util.CoroutineDispatchers
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event
import com.woocommerce.android.viewmodel.SavedStateWithArgs
import com.woocommerce.android.viewmodel.ScopedViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import org.wordpress.android.fluxc.utils.AppLogWrapper
import org.wordpress.android.util.AppLog.T

class CardReaderConnectViewModel @AssistedInject constructor(
    @Assisted savedState: SavedStateWithArgs,
    dispatchers: CoroutineDispatchers,
    private val appLogWrapper: AppLogWrapper
) : ScopedViewModel(savedState, dispatchers) {

    fun onInitiateScanBtnClicked() {
        triggerEvent(InitiateCardReaderScan)
    }

    sealed class NavigationTarget : Event() {
        object InitiateCardReaderScan : NavigationTarget()
    }

    @AssistedFactory
    interface Factory : ViewModelAssistedFactory<CardReaderConnectViewModel>
}

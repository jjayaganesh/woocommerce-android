package com.woocommerce.android.ui.login.jetpack.start

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class JetpackActivationStartViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ScopedViewModel(savedStateHandle) {
    private val navArgs: JetpackActivationStartFragmentArgs by savedStateHandle.navArgs()

    val viewState: LiveData<JetpackActivationState> = MutableLiveData(
        JetpackActivationState(
            url = navArgs.siteUrl,
            isJetpackInstalled = navArgs.isJetpackInstalled,
            isJetpackConnected = navArgs.isJetpackConnected
        )
    )

    data class JetpackActivationState(
        val url: String,
        val isJetpackInstalled: Boolean,
        // This would be true if the site is connected to a different account
        val isJetpackConnected: Boolean
    )
}

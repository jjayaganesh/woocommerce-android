package com.woocommerce.android.ui.products.variations

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.model.ProductVariation
import com.woocommerce.android.ui.products.ParameterRepository
import com.woocommerce.android.ui.products.models.SiteParameters
import com.woocommerce.android.viewmodel.LiveDataDelegate
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.parcelize.Parcelize
import org.wordpress.android.fluxc.model.WCSettingsModel
import org.wordpress.android.fluxc.model.WCSettingsModel.CurrencyPosition.LEFT
import org.wordpress.android.fluxc.model.WCSettingsModel.CurrencyPosition.LEFT_SPACE
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class VariationsBulkUpdatePriceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    parameterRepository: ParameterRepository,
) : ScopedViewModel(savedStateHandle) {

    private val args: VariationsBulkUpdatePriceFragmentArgs by savedStateHandle.navArgs()
    private val variationsToUpdate: List<ProductVariation> = args.priceUpdateData.variationsToUpdate

    private val parameters: SiteParameters by lazy {
        parameterRepository.getParameters("key_product_parameters", savedState)
    }

    val viewStateData = LiveDataDelegate(savedState, ViewState())
    private var viewState by viewStateData

    init {
        val data : PriceUpdateData = args.priceUpdateData
        viewState = viewState.copy(
            currencyPosition = parameters.currencyFormattingParameters?.currencyPosition,
            currency = parameters.currencySymbol,
            pricesGroupType = data.getPriceCollection().groupType(),
            priceType = data.priceType,
            variationsToUpdateCount = data.variationsToUpdate.size,
        )
    }

    fun onDoneClicked() {
        // TODO perform bulk update 🚀
    }

    fun onPriceEntered(price: BigDecimal?) {
        viewState = viewState.copy(price = price)
    }

    @Parcelize
    data class ViewState(
        val currency: String? = null,
        val price: BigDecimal? = null,
        val priceType: PriceType? = null,
        val pricesGroupType: ValuesGroupType? = null,
        val variationsToUpdateCount: Int? = null,
        private val currencyPosition: WCSettingsModel.CurrencyPosition? = null,
    ) : Parcelable {
        val isCurrencyPrefix: Boolean
            get() = currencyPosition == LEFT || currencyPosition == LEFT_SPACE
    }

    @Parcelize
    data class PriceUpdateData(
        val variationsToUpdate: List<ProductVariation>,
        val priceType: PriceType,
    ) : Parcelable

    private fun PriceUpdateData.getPriceCollection() : Collection<BigDecimal?> {
        return when(priceType) {
            PriceType.Sale -> variationsToUpdate.map { it.salePrice }
            PriceType.Regular -> variationsToUpdate.map { it.regularPrice }
        }
    }

    enum class PriceType {
        Regular, Sale
    }
}

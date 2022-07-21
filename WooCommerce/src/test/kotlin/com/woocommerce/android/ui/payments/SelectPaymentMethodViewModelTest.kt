package com.woocommerce.android.ui.payments

import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsEvent
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTrackerWrapper
import com.woocommerce.android.initSavedStateHandle
import com.woocommerce.android.model.Order
import com.woocommerce.android.model.OrderMapper
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.payments.SelectPaymentMethodViewModel.NavigateToCardReaderHubFlow
import com.woocommerce.android.ui.payments.SelectPaymentMethodViewModel.NavigateToCardReaderRefundFlow
import com.woocommerce.android.ui.payments.SelectPaymentMethodViewModel.TakePaymentViewState.Loading
import com.woocommerce.android.ui.payments.SelectPaymentMethodViewModel.TakePaymentViewState.Success
import com.woocommerce.android.ui.payments.cardreader.onboarding.CardReaderFlowParam
import com.woocommerce.android.ui.payments.cardreader.onboarding.CardReaderFlowParam.CardReadersHub
import com.woocommerce.android.ui.payments.cardreader.onboarding.CardReaderFlowParam.PaymentOrRefund.Payment
import com.woocommerce.android.ui.payments.cardreader.onboarding.CardReaderFlowParam.PaymentOrRefund.Payment.PaymentType.ORDER
import com.woocommerce.android.ui.payments.cardreader.onboarding.CardReaderFlowParam.PaymentOrRefund.Payment.PaymentType.SIMPLE
import com.woocommerce.android.ui.payments.cardreader.onboarding.CardReaderFlowParam.PaymentOrRefund.Refund
import com.woocommerce.android.ui.payments.cardreader.payment.CardReaderPaymentCollectibilityChecker
import com.woocommerce.android.util.CurrencyFormatter
import com.woocommerce.android.util.captureValues
import com.woocommerce.android.viewmodel.BaseUnitTest
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowDialog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.wordpress.android.fluxc.model.OrderEntity
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.network.rest.wpcom.wc.order.CoreOrderStatus
import org.wordpress.android.fluxc.store.WCOrderStore
import org.wordpress.android.fluxc.store.WCOrderStore.OnOrderChanged
import org.wordpress.android.fluxc.store.WooCommerceStore
import java.math.BigDecimal

private const val PAYMENT_URL = "paymentUrl"
private const val ORDER_TOTAL = "100$"

@OptIn(ExperimentalCoroutinesApi::class)
class SelectPaymentMethodViewModelTest : BaseUnitTest() {
    private val site: SiteModel = mock {
        on { name }.thenReturn("siteName")
    }
    private val order: Order = mock {
        on { paymentUrl }.thenReturn(PAYMENT_URL)
        on { total }.thenReturn(BigDecimal(1L))
    }
    private val orderEntity: OrderEntity = mock()

    private val selectedSite: SelectedSite = mock {
        on { get() }.thenReturn(site)
    }
    private val orderStore: WCOrderStore = mock {
        onBlocking { getOrderByIdAndSite(any(), any()) }.thenReturn(orderEntity)
        on { getOrderStatusForSiteAndKey(any(), any()) }.thenReturn(mock())
    }
    private val networkStatus: NetworkStatus = mock()
    private val currencyFormatter: CurrencyFormatter = mock {
        on { formatCurrency(any<BigDecimal>(), any(), any()) }.thenReturn(ORDER_TOTAL)
    }
    private val wooCommerceStore: WooCommerceStore = mock {
        on { getSiteSettings(site) }.thenReturn(mock())
    }
    private val orderMapper: OrderMapper = mock {
        on { toAppModel(orderEntity) }.thenReturn(order)
    }
    private val cardPaymentCollectibilityChecker: CardReaderPaymentCollectibilityChecker = mock {
        onBlocking { isCollectable(order) }.thenReturn(false)
    }
    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper = mock()

    @Test
    fun `given hub flow, when view model init, then navigate to hub flow emitted`() = testBlocking {
        // GIVEN & WHEN
        val viewModel = initViewModel(CardReadersHub)

        // THEN
        assertThat(viewModel.event.value).isEqualTo(NavigateToCardReaderHubFlow(CardReadersHub))
    }

    @Test
    fun `given hub flow, when view model init, then loading state emitted`() = testBlocking {
        // GIVEN & WHEN
        val viewModel = initViewModel(CardReadersHub)

        // THEN
        assertThat(viewModel.viewStateData.value).isEqualTo(Loading)
    }

    @Test
    fun `given refund flow, when view model init, then loading state emitted`() = testBlocking {
        // GIVEN & WHEN
        val orderId = 1L
        val refundAmount = BigDecimal(23)
        val viewModel = initViewModel(Refund(orderId, refundAmount))

        // THEN
        assertThat(viewModel.viewStateData.value).isEqualTo(Loading)
    }

    @Test
    fun `given payment flow, when view model init, then no events emitted`() = testBlocking {
        // GIVEN & WHEN
        val orderId = 1L
        val viewModel = initViewModel(Payment(orderId, ORDER))

        // THEN
        assertThat(viewModel.event.value).isNull()
    }

    @Test
    fun `given payment flow, when view model init, then success state emitted`() = testBlocking {
        // GIVEN & WHEN
        val orderId = 1L
        val viewModel = initViewModel(Payment(orderId, ORDER))

        // THEN
        assertThat(viewModel.viewStateData.value).isEqualTo(
            Success(
                orderTotal = ORDER_TOTAL,
                paymentUrl = PAYMENT_URL,
                isPaymentCollectableWithCardReader = false,
            )
        )
    }

    @Test
    fun `given payment flow and payment collectable, when view model init, then success emitted with collect true`() =
        testBlocking {
            // GIVEN & WHEN
            whenever(cardPaymentCollectibilityChecker.isCollectable(order)).thenReturn(true)
            val orderId = 1L
            val viewModel = initViewModel(Payment(orderId, ORDER))

            // THEN
            assertThat(viewModel.viewStateData.value).isEqualTo(
                Success(
                    orderTotal = ORDER_TOTAL,
                    paymentUrl = PAYMENT_URL,
                    isPaymentCollectableWithCardReader = true,
                )
            )
        }

    @Test
    fun `given refund flow, when view model init, then navigate to refund flow emitted`() = testBlocking {
        // GIVEN & WHEN
        val orderId = 1L
        val refundAmount = BigDecimal(23)
        val viewModel = initViewModel(Refund(orderId, refundAmount))

        // THEN
        assertThat(viewModel.event.value).isEqualTo(NavigateToCardReaderRefundFlow(Refund(orderId, refundAmount)))
    }

    @Test
    fun `given order payment flow, when on cash payment clicked, then show dialog event emitted`() = testBlocking {
        // GIVEN
        val orderId = 1L
        val viewModel = initViewModel(Payment(orderId, ORDER))

        // WHEN
        viewModel.onCashPaymentClicked()

        // THEN
        val events = viewModel.event.captureValues()
        assertThat(events.last()).isInstanceOf(ShowDialog::class.java)
        assertThat((events.last() as ShowDialog).titleId).isEqualTo(R.string.simple_payments_cash_dlg_title)
        assertThat((events.last() as ShowDialog).messageId).isEqualTo(R.string.existing_order_cash_dlg_message)
        assertThat((events.last() as ShowDialog).positiveButtonId).isEqualTo(R.string.simple_payments_cash_dlg_button)
        assertThat((events.last() as ShowDialog).negativeButtonId).isEqualTo(R.string.cancel)
    }

    @Test
    fun `given simple payment flow, when on cash payment clicked, then show dialog event emitted`() =
        testBlocking {
            // GIVEN
            val orderId = 1L
            val viewModel = initViewModel(Payment(orderId, SIMPLE))

            // WHEN
            viewModel.onCashPaymentClicked()

            // THEN
            val events = viewModel.event.captureValues()
            assertThat(events.last()).isInstanceOf(ShowDialog::class.java)
            assertThat((events.last() as ShowDialog).titleId).isEqualTo(R.string.simple_payments_cash_dlg_title)
            assertThat((events.last() as ShowDialog).messageId).isEqualTo(R.string.simple_payments_cash_dlg_message)
            assertThat((events.last() as ShowDialog).positiveButtonId).isEqualTo(
                R.string.simple_payments_cash_dlg_button
            )
            assertThat((events.last() as ShowDialog).negativeButtonId).isEqualTo(R.string.cancel)
        }

    @Test
    fun `given order payment flow, when on cash payment clicked, then collect tracked with order payment flow`() =
        testBlocking {
            // GIVEN
            val viewModel = initViewModel(Payment(1L, ORDER))

            // WHEN
            viewModel.onCashPaymentClicked()

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_COLLECT,
                mapOf(
                    AnalyticsTracker.KEY_PAYMENT_METHOD to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_COLLECT_CASH,
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_ORDER_PAYMENTS_FLOW,
                )
            )
        }

    @Test
    fun `given simple payment flow, when on cash payment clicked, then collect tracked with simple payment flow`() =
        testBlocking {
            // GIVEN
            val viewModel = initViewModel(Payment(1L, SIMPLE))

            // WHEN
            viewModel.onCashPaymentClicked()

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_COLLECT,
                mapOf(
                    AnalyticsTracker.KEY_PAYMENT_METHOD to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_COLLECT_CASH,
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_FLOW,
                )
            )
        }

    @Test
    fun `given order payment flow, when on card payment clicked, then collect tracked with order payment flow`() =
        testBlocking {
            // GIVEN
            val viewModel = initViewModel(Payment(1L, ORDER))

            // WHEN
            viewModel.onCardPaymentClicked()

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_COLLECT,
                mapOf(
                    AnalyticsTracker.KEY_PAYMENT_METHOD to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_COLLECT_CARD,
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_ORDER_PAYMENTS_FLOW,
                )
            )
        }

    @Test
    fun `given simple payment flow, when on card payment clicked, then collect tracked with simple payment flow`() =
        testBlocking {
            // GIVEN
            val viewModel = initViewModel(Payment(1L, SIMPLE))

            // WHEN
            viewModel.onCardPaymentClicked()

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_COLLECT,
                mapOf(
                    AnalyticsTracker.KEY_PAYMENT_METHOD to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_COLLECT_CARD,
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_FLOW,
                )
            )
        }

    @Test
    fun `given simple payment flow, when on connect to reader result, then failed tracked with simple payment flow`() =
        testBlocking {
            // GIVEN
            val viewModel = initViewModel(Payment(1L, SIMPLE))

            // WHEN
            viewModel.onConnectToReaderResultReceived(false)

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_FAILED,
                mapOf(
                    AnalyticsTracker.KEY_SOURCE to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_SOURCE_PAYMENT_METHOD,
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_FLOW,
                )
            )
        }

    @Test
    fun `given order payment flow, when on connect to reader result, then failed tracked with order payment flow`() =
        testBlocking {
            // GIVEN
            val viewModel = initViewModel(Payment(1L, ORDER))

            // WHEN
            viewModel.onConnectToReaderResultReceived(false)

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_FAILED,
                mapOf(
                    AnalyticsTracker.KEY_SOURCE to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_SOURCE_PAYMENT_METHOD,
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_ORDER_PAYMENTS_FLOW,
                )
            )
        }

    @Test
    fun `given order payment flow, when on reader payment compl success, then complected tracked with order flow`() =
        testBlocking {
            // GIVEN
            whenever(orderEntity.status).thenReturn(CoreOrderStatus.COMPLETED.value)
            val viewModel = initViewModel(Payment(1L, ORDER))

            // WHEN
            viewModel.onCardReaderPaymentCompleted()

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_COMPLETED,
                mapOf(
                    AnalyticsTracker.KEY_AMOUNT to "100$",
                    AnalyticsTracker.KEY_PAYMENT_METHOD to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_COLLECT_CARD,
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_ORDER_PAYMENTS_FLOW,
                )
            )
        }

    @Test
    fun `given simple payment flow, when on reader payment compl success, then completed tracked with simple flow`() =
        testBlocking {
            // GIVEN
            whenever(orderEntity.status).thenReturn(CoreOrderStatus.COMPLETED.value)
            val viewModel = initViewModel(Payment(1L, SIMPLE))

            // WHEN
            viewModel.onCardReaderPaymentCompleted()

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_COMPLETED,
                mapOf(
                    AnalyticsTracker.KEY_AMOUNT to "100$",
                    AnalyticsTracker.KEY_PAYMENT_METHOD to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_COLLECT_CARD,
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_FLOW,
                )
            )
        }

    @Test
    fun `given simple payment flow, when on reader payment compl fail, then fail tracked with simple flow`() =
        testBlocking {
            // GIVEN
            whenever(orderEntity.status).thenReturn(CoreOrderStatus.FAILED.value)
            val viewModel = initViewModel(Payment(1L, SIMPLE))

            // WHEN
            viewModel.onCardReaderPaymentCompleted()

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_FAILED,
                mapOf(
                    AnalyticsTracker.KEY_SOURCE to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_SOURCE_PAYMENT_METHOD,
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_FLOW,
                )
            )
        }

    @Test
    fun `given order payment flow, when on reader payment compl fail, then fail tracked with order flow`() =
        testBlocking {
            // GIVEN
            whenever(orderEntity.status).thenReturn(CoreOrderStatus.FAILED.value)
            val viewModel = initViewModel(Payment(1L, ORDER))

            // WHEN
            viewModel.onCardReaderPaymentCompleted()

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_FAILED,
                mapOf(
                    AnalyticsTracker.KEY_SOURCE to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_SOURCE_PAYMENT_METHOD,
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_ORDER_PAYMENTS_FLOW,
                )
            )
        }

    @Test
    fun `given order payment flow, when on share link clicked, then coll tracked with order flow`() =
        testBlocking {
            // GIVEN
            val viewModel = initViewModel(Payment(1L, ORDER))

            // WHEN
            viewModel.onSharePaymentUrlClicked()

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_COLLECT,
                mapOf(
                    AnalyticsTracker.KEY_PAYMENT_METHOD to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_COLLECT_LINK,
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_ORDER_PAYMENTS_FLOW,
                )
            )
        }

    @Test
    fun `given simple payment flow, when on share link clicked, then collect tracked with simple flow`() =
        testBlocking {
            // GIVEN
            val viewModel = initViewModel(Payment(1L, SIMPLE))

            // WHEN
            viewModel.onSharePaymentUrlClicked()

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_COLLECT,
                mapOf(
                    AnalyticsTracker.KEY_PAYMENT_METHOD to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_COLLECT_LINK,
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_FLOW,
                )
            )
        }

    @Test
    fun `given simple payment flow, when on share link completed, then completed tracked with simple flow`() =
        testBlocking {
            // GIVEN
            val viewModel = initViewModel(Payment(1L, SIMPLE))

            // WHEN
            viewModel.onSharePaymentUrlCompleted()

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_COMPLETED,
                mapOf(
                    AnalyticsTracker.KEY_PAYMENT_METHOD to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_COLLECT_LINK,
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_FLOW,
                )
            )
        }

    @Test
    fun `given order payment flow, when on share link completed, then completed tracked with order flow`() =
        testBlocking {
            // GIVEN
            val viewModel = initViewModel(Payment(1L, ORDER))

            // WHEN
            viewModel.onSharePaymentUrlCompleted()

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_COMPLETED,
                mapOf(
                    AnalyticsTracker.KEY_PAYMENT_METHOD to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_COLLECT_LINK,
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_ORDER_PAYMENTS_FLOW,
                )
            )
        }

    @Test
    fun `given order payment flow, when on share link completed update fail, then fail tracked with order flow`() =
        testBlocking {
            // GIVEN
            val event = mock<OnOrderChanged> {
                on { isError }.thenReturn(true)
            }
            val error = WCOrderStore.UpdateOrderResult.RemoteUpdateResult(event)
            whenever(
                orderStore.updateOrderStatus(
                    any(),
                    any(),
                    any()
                )
            ).thenReturn(flowOf(error))
            val viewModel = initViewModel(Payment(1L, ORDER))

            // WHEN
            viewModel.onSharePaymentUrlCompleted()

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_FAILED,
                mapOf(
                    AnalyticsTracker.KEY_SOURCE to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_SOURCE_PAYMENT_METHOD,
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_ORDER_PAYMENTS_FLOW,
                )
            )
        }

    @Test
    fun `given simple payment flow, when on share link completed update fail, then fail tracked with simple flow`() =
        testBlocking {
            // GIVEN
            val event = mock<OnOrderChanged> {
                on { isError }.thenReturn(true)
            }
            val error = WCOrderStore.UpdateOrderResult.RemoteUpdateResult(event)
            whenever(
                orderStore.updateOrderStatus(
                    any(),
                    any(),
                    any()
                )
            ).thenReturn(flowOf(error))
            val viewModel = initViewModel(Payment(1L, SIMPLE))

            // WHEN
            viewModel.onSharePaymentUrlCompleted()

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_FAILED,
                mapOf(
                    AnalyticsTracker.KEY_SOURCE to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_SOURCE_PAYMENT_METHOD,
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_FLOW,
                )
            )
        }

    @Test
    fun `given simple payment flow, when on back pressed, then nothing is tracked`() =
        testBlocking {
            // GIVEN
            val viewModel = initViewModel(Payment(1L, SIMPLE))

            // WHEN
            viewModel.onBackPressed()

            // THEN
            verify(analyticsTrackerWrapper, never()).track(any(), any())
        }

    @Test
    fun `given order payment flow, when on back pressed, then flow cancelation is tracked`() =
        testBlocking {
            // GIVEN
            val viewModel = initViewModel(Payment(1L, ORDER))

            // WHEN
            viewModel.onBackPressed()

            // THEN
            verify(analyticsTrackerWrapper).track(
                AnalyticsEvent.PAYMENTS_FLOW_CANCELED,
                mapOf(
                    AnalyticsTracker.KEY_FLOW to AnalyticsTracker.VALUE_ORDER_PAYMENTS_FLOW,
                )
            )
        }

    private fun initViewModel(cardReaderFlowParam: CardReaderFlowParam): SelectPaymentMethodViewModel {
        return SelectPaymentMethodViewModel(
            SelectPaymentMethodFragmentArgs(cardReaderFlowParam = cardReaderFlowParam).initSavedStateHandle(),
            selectedSite,
            orderStore,
            coroutinesTestRule.testDispatchers,
            networkStatus,
            currencyFormatter,
            wooCommerceStore,
            orderMapper,
            analyticsTrackerWrapper,
            cardPaymentCollectibilityChecker,
        )
    }
}
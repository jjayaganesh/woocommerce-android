package com.woocommerce.android.ui.analytics

import com.woocommerce.android.model.DeltaPercentage
import com.woocommerce.android.model.ProductItem
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.analytics.AnalyticsRepository.Companion.ANALYTICS_ORDERS_PATH
import com.woocommerce.android.ui.analytics.AnalyticsRepository.Companion.ANALYTICS_PRODUCTS_PATH
import com.woocommerce.android.ui.analytics.AnalyticsRepository.Companion.ANALYTICS_REVENUE_PATH
import com.woocommerce.android.ui.analytics.AnalyticsRepository.FetchStrategy.ForceNew
import com.woocommerce.android.ui.analytics.AnalyticsRepository.OrdersResult.OrdersData
import com.woocommerce.android.ui.analytics.AnalyticsRepository.OrdersResult.OrdersError
import com.woocommerce.android.ui.analytics.AnalyticsRepository.ProductsResult.ProductsData
import com.woocommerce.android.ui.analytics.AnalyticsRepository.ProductsResult.ProductsError
import com.woocommerce.android.ui.analytics.AnalyticsRepository.RevenueResult.RevenueData
import com.woocommerce.android.ui.analytics.AnalyticsRepository.RevenueResult.RevenueError
import com.woocommerce.android.ui.analytics.daterangeselector.AnalyticTimePeriod
import com.woocommerce.android.ui.analytics.daterangeselector.AnalyticsDateRange.MultipleDateRange
import com.woocommerce.android.ui.analytics.daterangeselector.AnalyticsDateRange.SimpleDateRange
import com.woocommerce.android.ui.mystore.data.StatsRepository
import com.woocommerce.android.viewmodel.BaseUnitTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import org.mockito.kotlin.*
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.WCProductModel
import org.wordpress.android.fluxc.model.WCRevenueStatsModel
import org.wordpress.android.fluxc.model.leaderboards.WCTopPerformerProductModel
import org.wordpress.android.fluxc.store.WCStatsStore.StatsGranularity.YEARS
import org.wordpress.android.fluxc.store.WooCommerceStore
import java.lang.NullPointerException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class AnalyticsRepositoryTest : BaseUnitTest() {
    private val statsRepository: StatsRepository = mock()
    private val selectedSite: SelectedSite = mock()
    private val wooCommerceStore: WooCommerceStore = mock()
    private val analyticsStorage: AnalyticsStorage = mock()

    private val sut: AnalyticsRepository = AnalyticsRepository(
        statsRepository,
        selectedSite,
        wooCommerceStore,
        analyticsStorage,
        coroutinesTestRule.testDispatchers
    )

    @Test
    fun `given no currentPeriodRevenue, when fetchRevenueData, then result is RevenueError`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val previousPeriodRevenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success(previousPeriodRevenue)).asFlow())

            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.failure<WCRevenueStatsModel?>(StatsRepository.StatsException(null))).asFlow())

            // When
            val result = sut.fetchRevenueData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is RevenueError)
        }

    @Test
    fun `given no currentPeriodRevenue when fetchOrderData result is RevenueError`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val previousPeriodOrdersStats = givenRevenueOrderStats(TEN_VALUE.toInt(), TEN_VALUE)
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success(previousPeriodOrdersStats)).asFlow())

            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.failure<WCRevenueStatsModel?>(StatsRepository.StatsException(null))).asFlow())

            // When
            val result = sut.fetchOrdersData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is OrdersError)
        }

    @Test
    fun `given no previousRevenuePeriod, when fetchRevenueData, then result is RevenueError`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val currentPeriodRevenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success(currentPeriodRevenue)).asFlow())

            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.failure<WCRevenueStatsModel?>(StatsRepository.StatsException(null))).asFlow())

            // When
            val result = sut.fetchRevenueData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is RevenueError)
        }

    @Test
    fun `given no previousRevenuePeriod when fetchOrdersData result is OrdersError`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val currentPeriodOrdersStats = givenRevenueOrderStats(TEN_VALUE.toInt(), TEN_VALUE)
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success(currentPeriodOrdersStats)).asFlow())

            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.failure<WCRevenueStatsModel?>(StatsRepository.StatsException(null))).asFlow())

            // When
            val result = sut.fetchOrdersData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is OrdersError)
        }

    @Test
    fun `given previous and current period revenue, when fetchRevenueData, then result is the expected`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val revenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success(revenue)).asFlow())

            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success(revenue)).asFlow())

            // When
            val result = sut.fetchRevenueData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is RevenueData)
            assertEquals(TEN_VALUE, result.revenueStat.totalValue)
            assertEquals(TEN_VALUE, result.revenueStat.netValue)
            assertTrue(result.revenueStat.totalDelta is DeltaPercentage.Value)
            assertTrue(result.revenueStat.netDelta is DeltaPercentage.Value)
        }

    @Test
    fun `given previous revenue and current zero revenue, when fetchRevenueData, then deltas are the expected`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val revenue = givenARevenue(ZERO_VALUE, ZERO_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success(revenue)).asFlow())

            val previousRevenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success(previousRevenue)).asFlow())

            // When
            val result = sut.fetchRevenueData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is RevenueData)
            assertTrue(result.revenueStat.totalDelta is DeltaPercentage.Value)
            assertEquals(ONE_HUNDRED_DECREASE, (result.revenueStat.totalDelta as DeltaPercentage.Value).value)
            assertTrue(result.revenueStat.netDelta is DeltaPercentage.Value)
            assertEquals(ONE_HUNDRED_DECREASE, (result.revenueStat.netDelta as DeltaPercentage.Value).value)
        }

    @Test
    fun `given zero previous and current revenue, when fetchRevenueData, then deltas are the expected`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val revenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success(revenue)).asFlow())

            val previousRevenue = givenARevenue(ZERO_VALUE, ZERO_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success(previousRevenue)).asFlow())

            // When
            val result = sut.fetchRevenueData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is RevenueData)
            assertTrue(result.revenueStat.totalDelta is DeltaPercentage.NotExist)
            assertTrue(result.revenueStat.netDelta is DeltaPercentage.NotExist)
        }

    @Test
    fun `given zero previous and current revenue, when fetchOrdersData, then deltas are the expected`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val ordersStats = givenRevenueOrderStats(TEN_VALUE.toInt(), TEN_VALUE)
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success(ordersStats)).asFlow())

            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success(ordersStats)).asFlow())

            // When
            val result = sut.fetchOrdersData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is OrdersData)
            assertTrue(result.ordersStat.avgOrderDelta is DeltaPercentage.Value)
            assertTrue(result.ordersStat.ordersCountDelta is DeltaPercentage.Value)
            assertEquals(ZERO_DELTA, (result.ordersStat.avgOrderDelta as DeltaPercentage.Value).value)
            assertEquals(ZERO_DELTA, (result.ordersStat.ordersCountDelta as DeltaPercentage.Value).value)
        }

    @Test
    fun `given previous and current period revenue when fetchOrdersData result is the expected`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val ordersStats = givenRevenueOrderStats(TEN_VALUE.toInt(), TEN_VALUE)
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success(ordersStats)).asFlow())

            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success(ordersStats)).asFlow())

            // When
            val result = sut.fetchOrdersData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is OrdersData)
            assertEquals(TEN_VALUE.toInt(), result.ordersStat.ordersCount)
            assertEquals(TEN_VALUE, result.ordersStat.avgOrderValue)
        }

    @Test
    fun `given zero previous total revenue, when fetchRevenueData, then result is the expected`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val previousPeriodRevenue = givenARevenue(ZERO_VALUE, ZERO_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(previousPeriodRevenue)).asFlow())

            val currentPeriodRevenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(currentPeriodRevenue)).asFlow())

            // When
            val result = sut.fetchRevenueData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertNotNull(this)
            assertTrue(result is RevenueData)
            assertEquals(TEN_VALUE, result.revenueStat.totalValue)
            assertEquals(TEN_VALUE, result.revenueStat.netValue)
        }

    @Test
    fun `given zero previous orders revenue when fetchOrdersData result is the expected`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val previousPeriodOrdersStats = givenRevenueOrderStats(ZERO_VALUE.toInt(), ZERO_VALUE)
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(previousPeriodOrdersStats)).asFlow())

            val currentPeriodOrdersStats = givenRevenueOrderStats(TEN_VALUE.toInt(), TEN_VALUE)
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(currentPeriodOrdersStats)).asFlow())

            // When
            val result = sut.fetchOrdersData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is OrdersData)
            assertEquals(TEN_VALUE.toInt(), result.ordersStat.ordersCount)
            assertEquals(TEN_VALUE, result.ordersStat.avgOrderValue)
            assertTrue(result.ordersStat.ordersCountDelta is DeltaPercentage.NotExist)
            assertTrue(result.ordersStat.avgOrderDelta is DeltaPercentage.NotExist)
        }

    @Test
    fun `given zero previous net revenue, when fetchRevenueData, then result is the expected`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val previousPeriodRevenue = givenARevenue(TEN_VALUE, ZERO_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(previousPeriodRevenue)).asFlow())

            val currentPeriodRevenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(currentPeriodRevenue)).asFlow())

            // When
            val result = sut.fetchRevenueData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is RevenueData)
            assertEquals(TEN_VALUE, result.revenueStat.totalValue)
            assertEquals(TEN_VALUE, result.revenueStat.netValue)
        }

    @Test
    fun `given zero previous avg order, when fetchOrderData, result is the expected`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val previousPeriodOrdersStats = givenRevenueOrderStats(TEN_VALUE.toInt(), ZERO_VALUE)
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(previousPeriodOrdersStats)).asFlow())

            val currentPeriodOrdersStats = givenRevenueOrderStats(TEN_VALUE.toInt(), TEN_VALUE)
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(currentPeriodOrdersStats)).asFlow())

            // When
            val result = sut.fetchOrdersData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is OrdersData)
            assertEquals(TEN_VALUE.toInt(), result.ordersStat.ordersCount)
            assertEquals(TEN_VALUE, result.ordersStat.avgOrderValue)
        }

    @Test
    fun `given null previous total revenue, when fetchRevenueData, then result is the expected`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val previousPeriodRevenue = givenARevenue(null, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(previousPeriodRevenue)).asFlow())

            val currentPeriodRevenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(currentPeriodRevenue)).asFlow())

            // When
            val result = sut.fetchRevenueData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is RevenueData)
            assertEquals(TEN_VALUE, result.revenueStat.totalValue)
        }

    @Test
    fun `given null previous orders, when fetchOrdersData, then result is the expected`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val previousPeriodOrdersStats = givenRevenueOrderStats(null, TEN_VALUE)
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(previousPeriodOrdersStats)).asFlow())

            val currentPeriodOrdersStats = givenRevenueOrderStats(TEN_VALUE.toInt(), TEN_VALUE)
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(currentPeriodOrdersStats)).asFlow())

            // When
            val result = sut.fetchOrdersData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is OrdersData)
            assertEquals(TEN_VALUE.toInt(), result.ordersStat.ordersCount)
            assertEquals(TEN_VALUE, result.ordersStat.avgOrderValue)
        }

    @Test
    fun `given null previous net revenue, when fetchRevenueData, then result is the expected`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val previousPeriodRevenue = givenARevenue(TEN_VALUE, null, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(previousPeriodRevenue)).asFlow())

            val currentPeriodRevenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(currentPeriodRevenue)).asFlow())

            // When
            val result = sut.fetchRevenueData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is RevenueData)
            assertEquals(TEN_VALUE, result.revenueStat.totalValue)
            assertEquals(TEN_VALUE, result.revenueStat.netValue)
        }

    @Test
    fun `given null previous avg order, when fetchOrdersData, then result is the expected`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val previousPeriodOrdersStats = givenRevenueOrderStats(TEN_VALUE.toInt(), null)
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(previousPeriodOrdersStats)).asFlow())

            val currentPeriodOrdersStats = givenRevenueOrderStats(TEN_VALUE.toInt(), TEN_VALUE)
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(currentPeriodOrdersStats)).asFlow())

            // When
            val result = sut.fetchOrdersData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is OrdersData)
            assertEquals(TEN_VALUE.toInt(), result.ordersStat.ordersCount)
        }

    @Test
    fun `given previous and current revenue, when fetchRevenueData multiple date range, then result is the expected`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val previousPeriodRevenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(previousPeriodRevenue)).asFlow())

            val currentPeriodRevenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(currentPeriodRevenue)).asFlow())

            // When
            val result = sut.fetchRevenueData(
                MultipleDateRange(
                    SimpleDateRange(previousDate!!, previousDate),
                    SimpleDateRange(currentDate!!, currentDate)
                ),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is RevenueData)
            assertEquals(TEN_VALUE, result.revenueStat.totalValue)
            assertEquals(TEN_VALUE, result.revenueStat.netValue)
        }

    @Test
    fun `given previous and current period revenue, when fetchOrdersData multiple date range, result is expected`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val previousPeriodOrdersStats = givenRevenueOrderStats(TEN_VALUE.toInt(), TEN_VALUE)
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(previousPeriodOrdersStats)).asFlow())

            val currentPeriodRevenue = givenRevenueOrderStats(TEN_VALUE.toInt(), TEN_VALUE)
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success<WCRevenueStatsModel?>(currentPeriodRevenue)).asFlow())

            // When
            val result = sut.fetchOrdersData(
                MultipleDateRange(
                    SimpleDateRange(previousDate!!, previousDate),
                    SimpleDateRange(currentDate!!, currentDate)
                ),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is OrdersData)
            assertEquals(TEN_VALUE.toInt(), result.ordersStat.ordersCount)
            assertEquals(TEN_VALUE, result.ordersStat.avgOrderValue)
        }

    @Test
    fun `given no currentPeriodRevenue, when fetchProductsData, then result is ProductsError`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val previousPeriodRevenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success(previousPeriodRevenue)).asFlow())

            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.failure<WCRevenueStatsModel?>(StatsRepository.StatsException(null))).asFlow())

            val productLeaderBoards = Result.success(givenAProductsStats())
            whenever(statsRepository.fetchProductLeaderboards(any(), any(), any(), any(), any()))
                .thenReturn(flowOf(productLeaderBoards))

            // When
            val result = sut.fetchProductsData(SimpleDateRange(previousDate!!, currentDate!!), ANY_RANGE, anyFetchStrategy)

            // Then
            assertTrue(result is ProductsError)
        }

    @Test
    fun `given no previousPeriodRevenue, when fetchProductsData, then result is ProductsError`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.failure<WCRevenueStatsModel?>(StatsRepository.StatsException(null))).asFlow())

            val currentPeriodRevenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success(currentPeriodRevenue)).asFlow())

            val productLeaderBoards = Result.success(givenAProductsStats())
            whenever(statsRepository.fetchProductLeaderboards(any(), any(), any(), any(), any()))
                .thenReturn(flowOf(productLeaderBoards))
            // When
            val result = sut.fetchProductsData(SimpleDateRange(previousDate!!, currentDate!!), ANY_RANGE, anyFetchStrategy)

            // Then
            assertTrue(result is ProductsError)

        }

    @Test
    fun `given previousPeriodRevenue with null items sold, when fetchProductsData, then result is ProductsError`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val previousPeriodRevenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success(previousPeriodRevenue)).asFlow())

            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.failure<WCRevenueStatsModel?>(StatsRepository.StatsException(null))).asFlow())

            val productLeaderBoards = Result.success(givenAProductsStats())
            whenever(statsRepository.fetchProductLeaderboards(any(), any(), any(), any(), any()))
                .thenReturn(flowOf(productLeaderBoards))
            // When
            val result = sut.fetchProductsData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is ProductsError)
        }

    @Test
    fun `given currentPeriodRevenue with null items sold, when fetchProductsData, then result is ProductsError`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val previousPeriodRevenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(PREVIOUS_DATE), eq(PREVIOUS_DATE)))
                .thenReturn(listOf(Result.success(previousPeriodRevenue)).asFlow())

            val currentPeriodRevenue = givenARevenue(TEN_VALUE, TEN_VALUE, null)
            whenever(statsRepository.fetchRevenueStats(any(), any(), eq(CURRENT_DATE), eq(CURRENT_DATE)))
                .thenReturn(listOf(Result.success(currentPeriodRevenue)).asFlow())

            val productLeaderBoards = Result.success(givenAProductsStats())
            whenever(statsRepository.fetchProductLeaderboards(any(), any(), any(), any(), any()))
                .thenReturn(flowOf(productLeaderBoards))
            // When
            val result = sut.fetchProductsData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is ProductsError)
        }

    @Test
    fun `given no products leader board with null items sold, when fetchProductsData, then result is ProductsError`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val revenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), any(), any()))
                .thenReturn(listOf(Result.success(revenue)).asFlow())

            whenever(statsRepository.fetchProductLeaderboards(any(), any(), any(), any(), any()))
                .thenReturn(flowOf(Result.failure(NullPointerException())))

            // When
            val result = sut.fetchProductsData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is ProductsError)
        }

    @Test
    fun `given products and revenue with null items sold, when fetchProductsData, then result is expected`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val revenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), any(), any()))
                .thenReturn(listOf(Result.success(revenue)).asFlow())

            val productLeaderBoards = Result.success(givenAProductsStats())
            whenever(statsRepository.fetchProductLeaderboards(any(), any(), any(), any(), any()))
                .thenReturn(flowOf(productLeaderBoards))

            // When
            val result = sut.fetchProductsData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            assertTrue(result is ProductsData)
            assertEquals(TEN_VALUE.toInt(), result.productsStat.itemsSold)
            assertEquals(DeltaPercentage.Value(ZERO_DELTA), result.productsStat.itemsSoldDelta)
            assertEquals(expectedProducts, result.productsStat.products)
        }

    @Test
    fun `given stats in analytics storage, when fetch product data, stats repository is called once `() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val revenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), any(), any()))
                .thenReturn(listOf(Result.success(revenue)).asFlow())

            val productLeaderBoards = Result.success(givenAProductsStats())

            whenever(statsRepository.fetchProductLeaderboards(any(), any(), any(), any(), any()))
                .thenReturn(flowOf(productLeaderBoards))

            val previousFromDate = "2021-01-01"
            val previousToDate = "2021-01-02"

            val currentFromDate = "2021-08-01"
            val currentToDate = "2021-08-10"

            whenever(analyticsStorage.getStats(currentFromDate, currentToDate)).thenReturn(revenue)

            // When
            sut.fetchProductsData(
                MultipleDateRange(
                    SimpleDateRange(sdf.parse(previousFromDate)!!, sdf.parse(previousToDate)!!),
                    SimpleDateRange(sdf.parse(currentFromDate)!!, sdf.parse(currentToDate)!!)
                ),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            val startDatesCaptor = argumentCaptor<String>()
            val endDatesCaptor = argumentCaptor<String>()

            verify(statsRepository, times(1))
                .fetchRevenueStats(
                    eq(YEARS),
                    eq(true),
                    startDatesCaptor.capture(),
                    endDatesCaptor.capture()
                )

            assertEquals(listOf(previousFromDate), startDatesCaptor.allValues)
            assertEquals(listOf(previousToDate), endDatesCaptor.allValues)
        }

    @Test
    fun `given no stats in analytics storage, when fetch product data, stats are saved `() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // Given
            val revenue = givenARevenue(TEN_VALUE, TEN_VALUE, TEN_VALUE.toInt())
            whenever(statsRepository.fetchRevenueStats(any(), any(), any(), any()))
                .thenReturn(listOf(Result.success(revenue)).asFlow())

            val productLeaderBoards = Result.success(givenAProductsStats())

            whenever(statsRepository.fetchProductLeaderboards(any(), any(), any(), any(), any()))
                .thenReturn(flowOf(productLeaderBoards))

            whenever(analyticsStorage.getStats(any(), any())).thenReturn(null)

            // When
            val result = sut.fetchProductsData(
                SimpleDateRange(previousDate!!, currentDate!!),
                ANY_RANGE,
                anyFetchStrategy
            )

            // Then
            verify(analyticsStorage, times(1)).saveStats(PREVIOUS_DATE, PREVIOUS_DATE, revenue)
        }

    @Test
    fun `when get revenue admin url panel, then is expected`() {
        val siteModel: SiteModel = mock()
        whenever(siteModel.adminUrl).thenReturn(ANY_URL)
        whenever(selectedSite.getIfExists()).thenReturn(siteModel)

        val adminPanelUrl = sut.getRevenueAdminPanelUrl()

        assertEquals(ANY_URL + ANALYTICS_REVENUE_PATH, adminPanelUrl)
    }

    @Test
    fun `when get orders admin url panel, then is expected`() {
        val siteModel: SiteModel = mock()
        whenever(siteModel.adminUrl).thenReturn(ANY_URL)
        whenever(selectedSite.getIfExists()).thenReturn(siteModel)

        val adminPanelUrl = sut.getOrdersAdminPanelUrl()

        assertEquals(ANY_URL + ANALYTICS_ORDERS_PATH, adminPanelUrl)
    }

    @Test
    fun `when get products admin url panel, then is expected`() {
        val siteModel: SiteModel = mock()
        whenever(siteModel.adminUrl).thenReturn(ANY_URL)
        whenever(selectedSite.getIfExists()).thenReturn(siteModel)

        val adminPanelUrl = sut.getProductsAdminPanelUrl()

        assertEquals(ANY_URL + ANALYTICS_PRODUCTS_PATH, adminPanelUrl)
    }

    private fun givenARevenue(totalSales: Double?, netValue: Double?, itemsSold: Int?): WCRevenueStatsModel {
        val stats: WCRevenueStatsModel = mock()
        val revenueStatsTotal: WCRevenueStatsModel.Total = mock()
        whenever(revenueStatsTotal.totalSales).thenReturn(totalSales)
        whenever(revenueStatsTotal.netRevenue).thenReturn(netValue)
        whenever(revenueStatsTotal.itemsSold).thenReturn(itemsSold)
        whenever(stats.parseTotal()).thenReturn(revenueStatsTotal)
        return stats
    }

    private fun givenRevenueOrderStats(orders: Int?, avgOrderValue: Double?): WCRevenueStatsModel {
        val stats: WCRevenueStatsModel = mock()
        val revenueStatsTotal: WCRevenueStatsModel.Total = mock()
        whenever(revenueStatsTotal.ordersCount).thenReturn(orders)
        whenever(revenueStatsTotal.avgOrderValue).thenReturn(avgOrderValue)
        whenever(stats.parseTotal()).thenReturn(revenueStatsTotal)
        return stats
    }

    private fun givenAProductsStats(): List<WCTopPerformerProductModel> {
        val product: WCProductModel = mock()
        val toPerformerProductModel: WCTopPerformerProductModel = mock()
        whenever(product.name).thenReturn(NAME)
        whenever(product.getFirstImageUrl()).thenReturn(IMAGE_URL)
        whenever(toPerformerProductModel.total).thenReturn(TEN_VALUE)
        whenever(toPerformerProductModel.currency).thenReturn(CURRENCY)
        whenever(toPerformerProductModel.quantity).thenReturn(TEN_VALUE.toInt())
        whenever(toPerformerProductModel.product).thenReturn(product)
        return mutableListOf(toPerformerProductModel)
    }

    companion object {
        const val PREVIOUS_DATE = "2021-01-01"
        const val CURRENT_DATE = "2021-01-02"

        const val TEN_VALUE = 10.0
        const val ZERO_VALUE = 0.0

        const val ZERO_DELTA = 0
        const val ONE_HUNDRED_DECREASE = -100

        const val ANY_URL = "https://a8c.com"
        val anyFetchStrategy = ForceNew

        val ANY_RANGE = AnalyticTimePeriod.LAST_YEAR
        private val sdf = SimpleDateFormat("yyyy-MM-dd")
        val previousDate: Date? = sdf.parse(PREVIOUS_DATE)
        val currentDate: Date? = sdf.parse(CURRENT_DATE)
        const val CURRENCY = "EUR"
        const val IMAGE_URL = "url"
        const val NAME = "name"

        val expectedProducts = listOf(
            ProductItem(
                NAME,
                TEN_VALUE,
                IMAGE_URL,
                TEN_VALUE.toInt(),
                CURRENCY
            )
        )
    }
}

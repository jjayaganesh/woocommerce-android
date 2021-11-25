package com.woocommerce.android.ui.analytics

import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.R
import com.woocommerce.android.ui.analytics.daterangeselector.AnalyticsDateRangeCalculator
import com.woocommerce.android.ui.analytics.daterangeselector.AnalyticsDateRangeSelectorContract.AnalyticsDateRangeSelectorViewState
import com.woocommerce.android.ui.analytics.daterangeselector.AnalyticsDateRanges
import com.woocommerce.android.ui.analytics.daterangeselector.DateRange
import com.woocommerce.android.ui.analytics.daterangeselector.DateRange.MultipleDateRange
import com.woocommerce.android.ui.analytics.daterangeselector.DateRange.SimpleDateRange
import com.woocommerce.android.ui.analytics.daterangeselector.formatDatesToFriendlyPeriod
import com.woocommerce.android.util.DateUtils
import com.woocommerce.android.viewmodel.ResourceProvider
import com.woocommerce.android.viewmodel.ScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val dateUtils: DateUtils,
    private val analyticsDateRange: AnalyticsDateRangeCalculator,
    savedState: SavedStateHandle
) : ScopedViewModel(savedState) {
    private val dateRange = SimpleDateRange(Date(dateUtils.getCurrentDateTimeMinusDays(1)), dateUtils.getCurrentDate())
    private val mutableState = MutableStateFlow(AnalyticsContract.AnalyticsState(
        analyticsDateRangeSelectorState = AnalyticsDateRangeSelectorViewState(
            fromDatePeriod = calculateFromDatePeriod(dateRange),
            toDatePeriod = calculateToDatePeriod(AnalyticsDateRanges.TODAY, dateRange),
            availableRangeDates = getAvailableDateRanges(),
            selectedPeriod = getDefaultSelectedPeriod()
        )
    ))

    val state: StateFlow<AnalyticsContract.AnalyticsState> = mutableState

    fun onSelectedDateRangeChanged(newSelection: String) {
        val selectedRange: AnalyticsDateRanges = AnalyticsDateRanges.from(newSelection)
        val newDateRange = analyticsDateRange.getAnalyticsDateRangeFrom(selectedRange)

        mutableState.value = state.value.copy(
            analyticsDateRangeSelectorState = state.value.analyticsDateRangeSelectorState.copy(
                fromDatePeriod = calculateFromDatePeriod(newDateRange),
                toDatePeriod = calculateToDatePeriod(selectedRange, newDateRange),
                selectedPeriod = getDateSelectedMessage(selectedRange)
            )
        )
    }

    private fun calculateToDatePeriod(analyticsDateRange: AnalyticsDateRanges, dateRange: DateRange) =
        when (dateRange) {
            is MultipleDateRange -> resourceProvider.getString(
                R.string.analytics_date_range_to_date,
                getDateSelectedMessage(analyticsDateRange),
                dateRange.to.formatDatesToFriendlyPeriod()
            )
            is SimpleDateRange -> resourceProvider.getString(
                R.string.analytics_date_range_to_date,
                getDateSelectedMessage(analyticsDateRange),
                dateUtils.getShortMonthDayAndYearString(dateUtils.getYearMonthDayStringFromDate(dateRange.to)).orEmpty()
            )
        }

    private fun calculateFromDatePeriod(dateRange: DateRange) = when (dateRange) {
        is MultipleDateRange -> resourceProvider.getString(
            R.string.analytics_date_range_from_date,
            dateRange.from.formatDatesToFriendlyPeriod()
        )
        is SimpleDateRange -> resourceProvider.getString(
            R.string.analytics_date_range_from_date,
            dateUtils.getShortMonthDayAndYearString(dateUtils.getYearMonthDayStringFromDate(dateRange.from)).orEmpty()
        )
    }

    private fun getAvailableDateRanges() = resourceProvider.getStringArray(R.array.date_range_selectors).asList()
    private fun getDefaultSelectedPeriod() = getDateSelectedMessage(AnalyticsDateRanges.TODAY)
    private fun getDateSelectedMessage(analyticsDateRange: AnalyticsDateRanges): String =
        when (analyticsDateRange) {
            AnalyticsDateRanges.TODAY -> resourceProvider.getString(R.string.date_timeframe_today)
            AnalyticsDateRanges.YESTERDAY -> resourceProvider.getString(R.string.date_timeframe_yesterday)
            AnalyticsDateRanges.LAST_WEEK -> resourceProvider.getString(R.string.date_timeframe_last_week)
            AnalyticsDateRanges.LAST_MONTH -> resourceProvider.getString(R.string.date_timeframe_last_month)
            AnalyticsDateRanges.LAST_QUARTER -> resourceProvider.getString(R.string.date_timeframe_last_quarter)
            AnalyticsDateRanges.LAST_YEAR -> resourceProvider.getString(R.string.date_timeframe_last_year)
            AnalyticsDateRanges.WEEK_TO_DATE -> resourceProvider.getString(R.string.date_timeframe_week_to_date)
            AnalyticsDateRanges.MONTH_TO_DATE -> resourceProvider.getString(R.string.date_timeframe_month_to_date)
            AnalyticsDateRanges.QUARTER_TO_DATE -> resourceProvider.getString(R.string.date_timeframe_quarter_to_date)
            AnalyticsDateRanges.YEAR_TO_DATE -> resourceProvider.getString(R.string.date_timeframe_year_to_date)
        }
}

package com.woocommerce.android.ui.orders.list

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.woocommerce.android.R
import com.woocommerce.android.ui.orders.list.OrderListItemUIType.LoadingItem
import com.woocommerce.android.ui.orders.list.OrderListItemUIType.OrderListItemUI
import com.woocommerce.android.ui.orders.list.OrderListItemUIType.SectionHeader
import com.woocommerce.android.util.CurrencyFormatter
import kotlinx.android.synthetic.main.order_list_item.view.*
import org.wordpress.android.util.DateTimeUtils
import javax.inject.Inject

private const val VIEW_TYPE_ORDER_ITEM = 0
private const val VIEW_TYPE_SECTION_HEADER = 2
private const val VIEW_TYPE_LOADING = 1

class OrderListAdapterNew @Inject constructor(
    val currencyFormatter: CurrencyFormatter
) : PagedListAdapter<OrderListItemUIType, ViewHolder>(OrderListDiffItemCallback) {
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is OrderListItemUI -> VIEW_TYPE_ORDER_ITEM
            is LoadingItem -> VIEW_TYPE_LOADING
            is SectionHeader -> VIEW_TYPE_SECTION_HEADER
            null -> VIEW_TYPE_LOADING // Placeholder by paged list
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ORDER_ITEM -> OrderItemUIViewHolder(R.layout.order_list_item, parent)
            VIEW_TYPE_LOADING -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.skeleton_order_list_item, parent, false)
                LoadingViewHolder(view)
            }
            VIEW_TYPE_SECTION_HEADER -> SectionHeaderViewHolder(R.layout.order_list_header, parent)
            else -> {
                // Fail fast if a new view type is added so we can handle it
                throw IllegalStateException("The view type '$viewType' needs to be handled")
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is OrderItemUIViewHolder) {
            assert(item is OrderListItemUI) {
                "If we are presenting WCOrderItemUIViewHolder, the item has to be of type WCOrderListUIItem " +
                        "for position: $position"
            }
            holder.onBind((item as OrderListItemUI))
        } else if (holder is SectionHeaderViewHolder) {
            assert(item is SectionHeader) {
                "If we are presenting SectionHeaderViewHolder, the item has to be of type SectionHeader " +
                        "for position: $position"
            }
            holder.onBind((item as SectionHeader))
        }
    }

    /**
     * Returns the order date formatted as a date string, or null if the date is missing or invalid.
     * Note that the year is not shown when it's the same as the current one
     */
    private fun getFormattedOrderDate(context: Context, orderDate: String): String? {
        DateTimeUtils.dateUTCFromIso8601(orderDate)?.let { date ->
            val flags = if (DateTimeUtils.isSameYear(date, DateTimeUtils.nowUTC())) {
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR
            } else {
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
            }
            return DateUtils.formatDateTime(context, date.time, flags)
        } ?: return null
    }

    inner class OrderItemUIViewHolder(
        @LayoutRes layout: Int,
        parentView: ViewGroup
    ) : RecyclerView.ViewHolder(LayoutInflater.from(parentView.context).inflate(layout, parentView, false)) {
        private val orderDateView = itemView.orderDate
        private val orderNumView = itemView.orderNum
        private val orderNameView = itemView.orderName
        private val orderTotalView = itemView.orderTotal
        private val orderTagList = itemView.orderTags
        fun onBind(orderItemUI: OrderListItemUI) {
            // Grab the current context from the underlying view
            val ctx = this.itemView.context
            orderDateView.text = getFormattedOrderDate(ctx, orderItemUI.dateCreated)
            orderNumView.text = orderItemUI.orderNumber
            orderNameView.text = orderItemUI.orderName
            orderTotalView.text = currencyFormatter.formatCurrency(orderItemUI.orderTotal, orderItemUI.currencyCode)
        }
    }

    private class LoadingViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private class SectionHeaderViewHolder(
        @LayoutRes layout: Int,
        parentView: ViewGroup
    ) : RecyclerView.ViewHolder(LayoutInflater.from(parentView.context).inflate(layout, parentView, false)) {
        private val titleView: TextView = itemView.findViewById(R.id.orderListHeader)
        fun onBind(header: SectionHeader) {
            // FIXME: Add logic to convert raw headers into localized labels

            titleView.text = header.title.name
        }
    }
}

private val OrderListDiffItemCallback = object : DiffUtil.ItemCallback<OrderListItemUIType>() {
    override fun areItemsTheSame(oldItem: OrderListItemUIType, newItem: OrderListItemUIType): Boolean {
        if (oldItem is SectionHeader && newItem is SectionHeader) {
            return oldItem.title == newItem.title
        }
        if (oldItem is LoadingItem && newItem is LoadingItem) {
            return oldItem.remoteId == newItem.remoteId
        }
        if (oldItem is OrderListItemUI && newItem is OrderListItemUI) {
            return oldItem.remoteOrderId == newItem.remoteOrderId
        }
        if (oldItem is LoadingItem && newItem is OrderListItemUI) {
            return oldItem.remoteId == newItem.remoteOrderId
        }
        return false
    }

    override fun areContentsTheSame(oldItem: OrderListItemUIType, newItem: OrderListItemUIType): Boolean {
        if (oldItem is SectionHeader && newItem is SectionHeader) {
            return oldItem.title == newItem.title
        }
        if (oldItem is LoadingItem && newItem is LoadingItem) {
            return oldItem.remoteId == newItem.remoteId
        }
        if (oldItem is OrderListItemUI && newItem is OrderListItemUI) {
            // AS is lying, it's not actually smart casting, so we have to do it :sigh:
            return (oldItem as OrderListItemUI) == (newItem as OrderListItemUI)
        }
        return false
    }
}

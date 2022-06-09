package com.woocommerce.android.ui.orders.creation.customerlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.woocommerce.android.R
import com.woocommerce.android.databinding.FragmentCustomerListBinding
import com.woocommerce.android.ui.base.BaseFragment
import com.woocommerce.android.ui.base.UIMessageResolver
import com.woocommerce.android.ui.compose.theme.WooThemeWithBackground
import com.woocommerce.android.viewmodel.MultiLiveEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CustomerListFragment :
    BaseFragment(R.layout.fragment_customer_list),
    MenuItem.OnActionExpandListener,
    SearchView.OnQueryTextListener
{
    @Inject lateinit var uiMessageResolver: UIMessageResolver

    private val viewModel by viewModels<CustomerListViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val binding = FragmentCustomerListBinding.inflate(inflater, container, false)

        binding.customerComposeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                WooThemeWithBackground {
                    CustomerListScreen(
                        onCustomerClick = viewModel::onCustomerClick
                    )
                }
            }
        }

        setupObservers()

        return binding.root
    }

    override fun getFragmentTitle() = getString(R.string.order_creation_customer_search_title)

    private fun setupObservers() {
        viewModel.event.observe(
            viewLifecycleOwner
        ) { event ->
            when (event) {
                is MultiLiveEvent.Event.ShowSnackbar -> uiMessageResolver.showSnack(event.message)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_search, menu)

        menu.findItem(R.id.menu_search)?.let { searchMenuItem ->
            searchMenuItem.expandActionView()
            searchMenuItem.setOnActionExpandListener(this)
            (searchMenuItem.actionView as? SearchView)?.let { searchView ->
                searchView.queryHint = getString(R.string.order_creation_customer_search_hint)
                searchView.setIconifiedByDefault(false)
                searchView.setOnQueryTextListener(this)
            }
        }

        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onMenuItemActionExpand(item: MenuItem?) = true

    /**
     * We want the search action view always expanded, so if the user taps the back button
     * to collapse it we leave the screen rather than collapse it
     */
    override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
        findNavController().navigateUp()
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        viewModel.onSearchQueryChanged(query)
        return true
    }

    override fun onQueryTextChange(query: String?): Boolean {
        viewModel.onSearchQueryChanged(query)
        return true
    }
}

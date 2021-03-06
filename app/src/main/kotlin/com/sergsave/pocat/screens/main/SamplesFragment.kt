package com.sergsave.pocat.screens.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.sergsave.pocat.MyApplication
import com.sergsave.pocat.R
import com.sergsave.pocat.models.CatData

class SamplesFragment : Fragment() {
    private val viewModel: SamplesViewModel by viewModels {
        (requireActivity().application as MyApplication)
            .appContainer.provideSamplesViewModelFactory()
    }

    private val navigation by activityViewModels<NavigationViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_samples, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fragment = childFragmentManager.findFragmentById(R.id.list_container)
                as? CatsListFragment ?: CatsListFragment.newInstance(isSelectionEnabled = false)

        if(savedInstanceState == null) {
            childFragmentManager
                .beginTransaction()
                .add(R.id.list_container, fragment)
                .commit()
        }

        viewModel.cats.observe(viewLifecycleOwner, Observer { fragment.cats = it })

        fragment.onItemClickListener = object : CatsListFragment.OnItemClickListener {
            override fun onItemClick(
                id: String,
                data: CatData,
                transition: SharedElementTransitionData
            ) {
                viewModel.onCardClicked(id)
                val card = viewModel.makeCard(data)
                navigation.openCat(card, transition)
            }
        }

    }
}
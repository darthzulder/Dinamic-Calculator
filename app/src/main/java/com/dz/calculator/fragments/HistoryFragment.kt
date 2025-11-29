package com.dz.calculator.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.dz.calculator.App
import com.dz.calculator.R
import com.dz.calculator.databinding.FragmentHistoryBinding
import com.dz.calculator.session.SessionData
import com.dz.calculator.session.SessionDataActionListener
import com.dz.calculator.session.SessionDataAdapter
import com.dz.calculator.session.SessionDataListListener
import com.dz.calculator.session.SessionService
import kotlin.properties.Delegates.notNull

class HistoryFragment : Fragment() {

    companion object {
        private var oldSizeRecyclerViewHistory = 0
        private var newSizeRecyclerViewHistory = 0
        private var currentPositionRecyclerViewHistory = 0
        private var recyclerViewHistoryElementIsAdded = false
        private var recyclerViewHistoryElementIsDeleted = false

        var recyclerViewHistoryIsRecreated = false
    }

    private var binding: FragmentHistoryBinding by notNull()
    private var adapter: SessionDataAdapter by notNull()
    private var callback: OnSessionInteractionListener? = null

    private val sessionService: SessionService
        get() = (requireContext().applicationContext as App).sessionService

    interface OnSessionInteractionListener {
        fun onSessionClick(sessionId: Int)
        fun onNewSessionClick()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // El callback se configurará en onViewCreated cuando el parentFragment esté disponible
    }

    private fun setupCallback() {
        try {
            // En ViewPager2, el parentFragment es el fragment que contiene el ViewPager2
            callback = parentFragment as? OnSessionInteractionListener
        } catch (e: ClassCastException) {
            android.util.Log.e(
                    getString(R.string.log_history_fragment_tag),
                    getString(R.string.log_parent_fragment_error),
                    e
            )
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        binding = FragmentHistoryBinding.inflate(inflater, container, false)

        adapter =
                SessionDataAdapter(
                        requireContext(),
                        object : SessionDataActionListener {
                            override fun onSessionClick(sessionData: SessionData) {
                                callback?.onSessionClick(sessionData.id)
                            }

                            override fun onDeleteSessionClick(sessionData: SessionData) {
                                adapter.deleteSession(sessionData)
                            }
                        }
                )

        val layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter

        // New Session button handler
        binding.newSessionButton?.setOnClickListener { callback?.onNewSessionClick() }

        sessionService.addListener(sessionDataListListener)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Configurar el callback después de que la vista esté creada
        setupCallback()
    }

    override fun onDestroy() {
        super.onDestroy()

        sessionService.removeListener(sessionDataListListener)
    }

    override fun onStop() {
        super.onStop()

        val layoutManager = binding.recyclerView.layoutManager as LinearLayoutManager?
        currentPositionRecyclerViewHistory = layoutManager!!.findFirstVisibleItemPosition()
    }

    private val sessionDataListListener: SessionDataListListener = {
        adapter.sessionList = it

        newSizeRecyclerViewHistory = adapter.sessionList.size

        if (oldSizeRecyclerViewHistory < newSizeRecyclerViewHistory ||
                        recyclerViewHistoryElementIsAdded
        ) {
            binding.recyclerView.scrollToPosition(0)
            if (recyclerViewHistoryIsRecreated) {
                recyclerViewHistoryElementIsAdded = !recyclerViewHistoryElementIsAdded
            }
        } else if (oldSizeRecyclerViewHistory > newSizeRecyclerViewHistory ||
                        recyclerViewHistoryElementIsDeleted
        ) {
            if (recyclerViewHistoryIsRecreated) {
                recyclerViewHistoryElementIsDeleted = !recyclerViewHistoryElementIsDeleted
            }
        } else {
            binding.recyclerView.scrollToPosition(currentPositionRecyclerViewHistory)
        }

        oldSizeRecyclerViewHistory = adapter.sessionList.size

        if (adapter.sessionList.isEmpty()) {
            val fadeInAnimation200: Animation =
                    AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in_200)

            binding.recyclerView.visibility = View.GONE
            binding.emptyHistoryImageView.visibility = View.VISIBLE
            binding.emptyHistoryText.visibility = View.VISIBLE

            binding.emptyHistoryImageView.startAnimation(fadeInAnimation200)
            binding.emptyHistoryText.startAnimation(fadeInAnimation200)
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyHistoryImageView.visibility = View.GONE
            binding.emptyHistoryText.visibility = View.GONE
        }
    }
}

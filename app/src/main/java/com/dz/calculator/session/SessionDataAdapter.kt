package com.dz.calculator.session

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.dz.calculator.App
import com.dz.calculator.R
import com.dz.calculator.databinding.ItemHistoryDataBinding
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit

// Al principio de SessionDataAdapter.kt, junto a los otros imports
class SessionDataDiffCallback(
    private val oldList: List<SessionData>,
    private val newList: List<SessionData>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int {
        return oldList.size
    }

    override fun getNewListSize(): Int {
        return newList.size
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldSessionData = oldList[oldItemPosition]
        val newSessionData = newList[newItemPosition]
        return oldSessionData.id == newSessionData.id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldSessionData = oldList[oldItemPosition]
        val newSessionData = newList[newItemPosition]
        return oldSessionData == newSessionData
    }
}

class SessionDataAdapter(
    private val context: Context,
    private val actionListener: SessionDataActionListener
) : RecyclerView.Adapter<SessionDataAdapter.SessionDataViewHolder>(),
    View.OnClickListener {

    private val sessionService: SessionService
        get() = (context.applicationContext as App).sessionService

    var sessionList: List<SessionData> = emptyList()
        set(newValue) {
            val diffCallback = SessionDataDiffCallback(field, newValue)
            val diffResult = DiffUtil.calculateDiff(diffCallback)
            field = newValue
            diffResult.dispatchUpdatesTo(this)
        }

    fun deleteSession(sessionData: SessionData) {
        val index = sessionList.indexOfFirst { it.id == sessionData.id }
        val newSessionList = ArrayList(sessionList)
        newSessionList.removeIf { it.id == sessionData.id }
        sessionList = newSessionList
        sessionService.deleteSession(sessionData.id)

        notifyItemRangeChanged(index, 2)
    }

    override fun onClick(v: View) {
        val sessionData = v.tag as SessionData
        when (v.id) {
            R.id.expressionText, R.id.resultText -> {
                actionListener.onSessionClick(sessionData)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionDataViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemHistoryDataBinding.inflate(inflater, parent, false)

        binding.expressionText.setOnClickListener(this)
        binding.resultText.setOnClickListener(this)

        return SessionDataViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return sessionList.size
    }

    private var lastDate: LocalDate? = null
    override fun onBindViewHolder(holder: SessionDataViewHolder, position: Int) {
        val sessionData = sessionList[position]
        with(holder.binding) {
            holder.itemView.tag = sessionData
            expressionText.tag = sessionData
            resultText.tag = sessionData

            toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.delete -> {
                        actionListener.onDeleteSessionClick(sessionData)
                        true
                    }
                    else -> false
                }
            }

            lastDate = if (position > 0) {
                sessionList[position - 1].date
            } else {
                null
            }

            if (lastDate == sessionData.date) {
                dividingLine.visibility = View.GONE
                dateText.visibility = View.GONE
                dateText.text = ""
            } else {
                if (position == 0) {
                    dividingLine.visibility = View.GONE
                } else {
                    dividingLine.visibility = View.VISIBLE
                }

                dateText.visibility = View.VISIBLE
                dateText.text = formatDate(sessionData.date)
            }

            // Display session name as "expression" and hide result
            expressionText.text = sessionData.name
            resultText.visibility = View.GONE

            lastDate = sessionData.date
        }
    }

    private fun formatDate(date: LocalDate): String {
        val dateNow = LocalDate.now()
        val outputDate: String

        val differenceInDays = ChronoUnit.DAYS.between(date, dateNow).toInt()
        if (differenceInDays in 0..7) {
            when (differenceInDays) {
                0 -> {
                    outputDate = context.getString(R.string.today_date)
                }
                1 -> {
                    outputDate = context.getString(R.string.yesterday_date)
                }
                2 -> {
                    outputDate = context.getString(R.string.days_ago_2_date)
                }
                3 -> {
                    outputDate = context.getString(R.string.days_ago_3_date)
                }
                4 -> {
                    outputDate = context.getString(R.string.days_ago_4_date)
                }
                5 -> {
                    outputDate = context.getString(R.string.days_ago_5_date)
                }
                6 -> {
                    outputDate = context.getString(R.string.days_ago_6_date)
                }
                else -> {
                    outputDate = context.getString(R.string.days_ago_7_date)
                }
            }
        } else {
            val day = date.dayOfMonth.toString()
            val month: String

            when (date.month) {
                Month.JANUARY -> {
                    month = context.getString(R.string.month_1_date)
                }
                Month.FEBRUARY -> {
                    month = context.getString(R.string.month_2_date)
                }
                Month.MARCH -> {
                    month = context.getString(R.string.month_3_date)
                }
                Month.APRIL -> {
                    month = context.getString(R.string.month_4_date)
                }
                Month.MAY -> {
                    month = context.getString(R.string.month_5_date)
                }
                Month.JUNE -> {
                    month = context.getString(R.string.month_6_date)
                }
                Month.JULY -> {
                    month = context.getString(R.string.month_7_date)
                }
                Month.AUGUST -> {
                    month = context.getString(R.string.month_8_date)
                }
                Month.SEPTEMBER -> {
                    month = context.getString(R.string.month_9_date)
                }
                Month.OCTOBER -> {
                    month = context.getString(R.string.month_10_date)
                }
                Month.NOVEMBER -> {
                    month = context.getString(R.string.month_11_date)
                }
                else -> {
                    month = context.getString(R.string.month_12_date)
                }
            }

            val year: String = if (dateNow.year == date.year) {
                ""
            } else {
                date.year.toString()
            }

            outputDate = "$day $month $year"
        }

        return outputDate
    }

    class SessionDataViewHolder(
        val binding: ItemHistoryDataBinding
    ) : RecyclerView.ViewHolder(binding.root)
}

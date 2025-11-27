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
    View.OnClickListener,
    View.OnLongClickListener {

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
            R.id.sessionLabel, R.id.sessionName -> {
                actionListener.onSessionClick(sessionData)
            }
        }
    }
    
    override fun onLongClick(v: View): Boolean {
        val sessionData = v.tag as SessionData
        when (v.id) {
            R.id.sessionName -> {
                showEditNameDialog(sessionData)
                return true
            }
        }
        return false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionDataViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemHistoryDataBinding.inflate(inflater, parent, false)

        binding.sessionLabel?.setOnClickListener(this)
        binding.sessionName?.setOnClickListener(this)
        binding.sessionName?.setOnLongClickListener(this)

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
            sessionLabel?.tag = sessionData
            sessionName?.tag = sessionData

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

            // Separar el nombre de sesión en sus partes
            // El formato del nombre es "N° X HH:MM" donde X es el número de sesión
            // Queremos mostrar "N° X:" en sessionLabel y "HH:MM" en sessionDate
            val parts = sessionData.name.split(" ")
            
            if (parts.size >= 3) {
                // parts[0] = "N°", parts[1] = número, parts[2] = hora
                sessionLabel?.text = "${parts[0]} ${parts[1]}:"  // "N° X:"
                // Mostrar customName si existe, sino la hora por defecto
                sessionName?.text = sessionData.customName.ifEmpty { parts[2] }
            } else {
                // Fallback si el formato no es el esperado
                sessionLabel?.text = "N°:"
                sessionName?.text = sessionData.customName.ifEmpty { sessionData.name }
            }

            lastDate = sessionData.date
        }
    }

    private fun formatDate(date: LocalDate): String {
        val dateNow = LocalDate.now()
        
        // Si es hoy, mostrar "Today"
        if (date == dateNow) {
            return context.getString(R.string.today_date)
        }
        
        // Para cualquier otra fecha, usar el formato del dispositivo
        val calendar = java.util.Calendar.getInstance().apply {
            set(date.year, date.monthValue - 1, date.dayOfMonth)
        }
        
        val dateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, context.resources.configuration.locale)
        return dateFormat.format(calendar.time)
    }
    
    private fun showEditNameDialog(sessionData: SessionData) {
        val editText = android.widget.EditText(context).apply {
            setText(sessionData.customName.ifEmpty { 
                // Extraer la hora por defecto del nombre
                val parts = sessionData.name.split(" ")
                if (parts.size >= 3) parts[2] else ""
            })
            hint = "Nombre de la sesión"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            selectAll()
        }
        
        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(context).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(editText)
        }
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Editar nombre de sesión")
            .setView(container)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = editText.text.toString().trim()
                sessionService.updateSessionName(sessionData.id, newName)
            }
            .setNegativeButton("Cancelar", null)
            .show()
            
        // Mostrar el teclado
        editText.requestFocus()
        editText.post {
            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    class SessionDataViewHolder(
        val binding: ItemHistoryDataBinding
    ) : RecyclerView.ViewHolder(binding.root)
}

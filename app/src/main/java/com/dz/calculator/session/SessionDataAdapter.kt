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

data class SessionUiModel(val sessionData: SessionData, val showHeader: Boolean)

class SessionUiDataDiffCallback(
        private val oldList: List<SessionUiModel>,
        private val newList: List<SessionUiModel>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int {
        return oldList.size
    }

    override fun getNewListSize(): Int {
        return newList.size
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].sessionData.id == newList[newItemPosition].sessionData.id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}

class SessionDataAdapter(
        private val context: Context,
        private val actionListener: SessionDataActionListener
) :
        RecyclerView.Adapter<SessionDataAdapter.SessionDataViewHolder>(),
        View.OnClickListener,
        View.OnLongClickListener {

    private val sessionService: SessionService
        get() = (context.applicationContext as App).sessionService

    private var uiList: List<SessionUiModel> = emptyList()

    var sessionList: List<SessionData> = emptyList()
        set(newValue) {
            val newUiList = generateUiList(newValue)
            val diffCallback = SessionUiDataDiffCallback(uiList, newUiList)
            val diffResult = DiffUtil.calculateDiff(diffCallback)

            field = newValue
            uiList = newUiList

            diffResult.dispatchUpdatesTo(this)
        }

    private fun generateUiList(dataList: List<SessionData>): List<SessionUiModel> {
        val uiList = mutableListOf<SessionUiModel>()
        var lastDate: LocalDate? = null

        for (data in dataList) {
            // Mostrar encabezado si es el primer elemento o si la fecha es diferente al anterior
            val showHeader = lastDate == null || !data.date.isEqual(lastDate)
            uiList.add(SessionUiModel(data, showHeader))
            lastDate = data.date
        }
        return uiList
    }

    fun deleteSession(sessionData: SessionData) {
        val newSessionList = ArrayList(sessionList)
        newSessionList.removeIf { it.id == sessionData.id }

        // Al actualizar sessionList, el setter se encargará de regenerar la UI list y notificar los
        // cambios
        sessionList = newSessionList
        sessionService.deleteSession(sessionData.id)
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
        return uiList.size
    }

    override fun onBindViewHolder(holder: SessionDataViewHolder, position: Int) {
        val uiModel = uiList[position]
        val sessionData = uiModel.sessionData

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

            if (!uiModel.showHeader) {
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
                sessionLabel?.text = "${parts[0]} ${parts[1]}:" // "N° X:"
                // Mostrar customName si existe, sino la hora por defecto
                sessionName?.text = sessionData.customName.ifEmpty { parts[2] }
            } else {
                // Fallback si el formato no es el esperado
                sessionLabel?.text = context.getString(R.string.session_label_fallback)
                sessionName?.text = sessionData.customName.ifEmpty { sessionData.name }
            }
        }
    }

    private fun formatDate(date: LocalDate): String {
        val dateNow = LocalDate.now()

        // Si es hoy, mostrar "Today"
        if (date == dateNow) {
            return context.getString(R.string.today_date)
        }

        // Para cualquier otra fecha, usar el formato del dispositivo
        val calendar =
                java.util.Calendar.getInstance().apply {
                    set(date.year, date.monthValue - 1, date.dayOfMonth)
                }

        val dateFormat =
                java.text.DateFormat.getDateInstance(
                        java.text.DateFormat.MEDIUM,
                        context.resources.configuration.locale
                )
        return dateFormat.format(calendar.time)
    }

    private fun showEditNameDialog(sessionData: SessionData) {
        val editText =
                android.widget.EditText(context).apply {
                    setText(
                            sessionData.customName.ifEmpty {
                                // Extraer la hora por defecto del nombre
                                val parts = sessionData.name.split(" ")
                                if (parts.size >= 3) parts[2] else ""
                            }
                    )
                    hint = context.getString(R.string.session_name_hint)
                    inputType = android.text.InputType.TYPE_CLASS_TEXT
                    selectAll()
                }

        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val container =
                android.widget.FrameLayout(context).apply {
                    setPadding(padding, padding / 2, padding, 0)
                    addView(editText)
                }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.dialog_edit_session_name_title))
                .setView(container)
                .setPositiveButton(context.getString(R.string.dialog_save_button)) { _, _ ->
                    val newName = editText.text.toString().trim()
                    sessionService.updateSessionName(sessionData.id, newName)
                }
                .setNegativeButton(context.getString(R.string.dialog_cancel_button), null)
                .show()

        // Mostrar el teclado
        editText.requestFocus()
        editText.post {
            val imm =
                    context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as
                            android.view.inputmethod.InputMethodManager
            imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    class SessionDataViewHolder(val binding: ItemHistoryDataBinding) :
            RecyclerView.ViewHolder(binding.root)
}

package com.example.paymentacademy.ui

import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.paymentacademy.R
import com.example.paymentacademy.data.model.MemberTariffValue
import com.example.paymentacademy.domain.CurrencyFormatter
import com.example.paymentacademy.domain.PaymentSetting
import com.example.paymentacademy.domain.UpdateMemberRequest
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

object MemberDialogs {
  private val STATUS_PAIRS = listOf(
    "active" to R.string.status_active,
    "frozen" to R.string.status_frozen,
    "inactive" to R.string.status_inactive,
  )

  fun showEditMemberDialog(
    context: Context,
    fullName: String,
    documentNumber: String?,
    status: String,
    phone: String?,
    email: String?,
    notes: String?,
    onSave: (UpdateMemberRequest) -> Unit,
  ) {
    val density = context.resources.displayMetrics.density
    val pad = (20 * density).toInt()
    val spacing = (10 * density).toInt()

    val container = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(pad, (12 * density).toInt(), pad, 0)
    }

    fun makeField(
      hintText: String,
      initialValue: String?,
      inputTypeFlags: Int = InputType.TYPE_CLASS_TEXT,
      multiLine: Boolean = false,
    ): Pair<TextInputLayout, TextInputEditText> {
      val layout = TextInputLayout(context).apply {
        hint = hintText
        layoutParams = LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = spacing }
      }
      val edit = TextInputEditText(layout.context).apply {
        inputType = inputTypeFlags
        setText(initialValue.orEmpty())
        if (multiLine) {
          minLines = 2
          maxLines = 4
        } else {
          maxLines = 1
        }
      }
      layout.addView(edit)
      container.addView(layout)
      return layout to edit
    }

    val (nameLayout, nameInput) = makeField(
      context.getString(R.string.full_name),
      fullName,
      InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS,
    )
    val (_, docInput) = makeField(
      context.getString(R.string.document_number),
      documentNumber,
    )

    // Selector de Estado (Activo / Congelado / Inactivo)
    val statusLabels = STATUS_PAIRS.map { context.getString(it.second) }
    val currentStatusLabel = STATUS_PAIRS.firstOrNull { it.first == status }
      ?.let { context.getString(it.second) }
      ?: context.getString(R.string.status_active)

    val statusLayout = TextInputLayout(
      context,
      null,
      com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle,
    ).apply {
      hint = "Estado"
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
      ).apply { topMargin = spacing }
    }
    val statusAutoComplete = MaterialAutoCompleteTextView(statusLayout.context).apply {
      inputType = InputType.TYPE_NULL
      setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, statusLabels))
      setText(currentStatusLabel, false)
    }
    statusLayout.addView(statusAutoComplete)
    container.addView(statusLayout)

    val (_, phoneInput) = makeField(
      context.getString(R.string.phone),
      phone,
      InputType.TYPE_CLASS_PHONE,
    )
    val (emailLayout, emailInput) = makeField(
      context.getString(R.string.email_optional),
      email,
      InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
    )
    val (_, notesInput) = makeField(
      context.getString(R.string.notes),
      notes,
      InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
      multiLine = true,
    )

    val scrollView = ScrollView(context).apply {
      addView(container)
    }

    val dialog = MaterialAlertDialogBuilder(context)
      .setTitle("Editar Miembro")
      .setView(scrollView)
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton("Guardar cambios", null)
      .create()

    dialog.setOnShowListener {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val cleanName = nameInput.text?.toString()?.trim().orEmpty()
        val cleanEmail = emailInput.text?.toString()?.trim().orEmpty()
        nameLayout.error = if (cleanName.isBlank()) "El nombre es obligatorio." else null
        emailLayout.error = if (cleanEmail.isNotEmpty() && !EMAIL_REGEX.matches(cleanEmail)) {
          "Ingresa un correo válido."
        } else null
        if (nameLayout.error != null || emailLayout.error != null) return@setOnClickListener

        val selectedStatusLabel = statusAutoComplete.text?.toString().orEmpty()
        val selectedStatusCode = STATUS_PAIRS.firstOrNull {
          context.getString(it.second) == selectedStatusLabel
        }?.first ?: "active"

        onSave(
          UpdateMemberRequest(
            fullName = cleanName,
            documentNumber = docInput.text?.toString()?.trim()?.ifBlank { null },
            phone = phoneInput.text?.toString()?.trim()?.ifBlank { null },
            email = cleanEmail.ifBlank { null },
            status = selectedStatusCode,
            notes = notesInput.text?.toString()?.trim()?.ifBlank { null },
          ),
        )
        dialog.dismiss()
      }
    }
    dialog.show()
  }

  fun showMemberTariffDialog(
    context: Context,
    memberName: String,
    currentTariff: MemberTariffValue?,
    globalSetting: PaymentSetting,
    onSaveCustom: (MemberTariffValue) -> Unit,
    onResetToGlobal: () -> Unit,
  ) {
    val density = context.resources.displayMetrics.density
    val pad = (20 * density).toInt()
    val spacing = (12 * density).toInt()

    val container = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(pad, (12 * density).toInt(), pad, 0)
    }

    val subtitle = TextView(context).apply {
      text = "Miembro: $memberName\n" +
        "Tarifa global actual: ${CurrencyFormatter.formatCurrency(globalSetting.amountMonthCordoba, CurrencyFormatter.CURRENCY_NIO)} / " +
        CurrencyFormatter.formatCurrency(globalSetting.amountMonthDollar, CurrencyFormatter.CURRENCY_USD)
      setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
      textSize = 13f
    }
    container.addView(subtitle)

    val customSwitch = MaterialSwitch(context).apply {
      text = "¿Asignar tarifa personalizada?"
      isChecked = currentTariff != null
      setTextColor(ContextCompat.getColor(context, R.color.text_primary))
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
      ).apply { topMargin = spacing }
    }
    container.addView(customSwitch)

    val fieldsContainer = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      visibility = if (currentTariff != null) View.VISIBLE else View.GONE
    }

    val cordobaLayout = TextInputLayout(context).apply {
      hint = "Mensualidad en Córdobas (C$)"
      prefixText = "C$ "
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
      ).apply { topMargin = spacing }
    }
    val cordobaInput = TextInputEditText(cordobaLayout.context).apply {
      inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
      val initialCordoba = currentTariff?.AmountMonthCordoba ?: globalSetting.amountMonthCordoba
      setText(plainAmount(initialCordoba))
    }
    cordobaLayout.addView(cordobaInput)
    fieldsContainer.addView(cordobaLayout)

    val dollarLayout = TextInputLayout(context).apply {
      hint = "Mensualidad en Dólares ($)"
      prefixText = "$ "
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
      ).apply { topMargin = spacing }
    }
    val dollarInput = TextInputEditText(dollarLayout.context).apply {
      inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
      val initialDollar = currentTariff?.AmountMonthDollar ?: globalSetting.amountMonthDollar
      setText(plainAmount(initialDollar))
    }
    dollarLayout.addView(dollarInput)
    fieldsContainer.addView(dollarLayout)
    container.addView(fieldsContainer)

    customSwitch.setOnCheckedChangeListener { _, isChecked ->
      fieldsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
    }

    val builder = MaterialAlertDialogBuilder(context)
      .setTitle("Tarifa del Miembro")
      .setView(container)
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton("Guardar", null)

    if (currentTariff != null) {
      builder.setNeutralButton("Tarifa global") { _, _ ->
        onResetToGlobal()
      }
    }

    val dialog = builder.create()
    dialog.setOnShowListener {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        if (!customSwitch.isChecked) {
          onResetToGlobal()
          dialog.dismiss()
          return@setOnClickListener
        }
        val cordoba = cordobaInput.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
        val dollar = dollarInput.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
        cordobaLayout.error = if (cordoba == null || cordoba <= 0.0) "Ingresa un monto > 0" else null
        dollarLayout.error = if (dollar == null || dollar <= 0.0) "Ingresa un monto > 0" else null
        if (cordobaLayout.error != null || dollarLayout.error != null) return@setOnClickListener

        onSaveCustom(
          MemberTariffValue(
            AmountMonthCordoba = cordoba,
            AmountMonthDollar = dollar,
          ),
        )
        dialog.dismiss()
      }
    }
    dialog.show()
  }

  fun showDeleteMemberDialog(
    context: Context,
    memberName: String,
    onConfirmDelete: () -> Unit,
  ) {
    val dialog = MaterialAlertDialogBuilder(context)
      .setTitle("Eliminar Miembro")
      .setMessage(
        "¿Deseas dar de baja a $memberName?\n\n" +
          "El miembro y sus pagos asociados serán dados de baja del sistema (eliminación lógica). " +
          "No aparecerán en los listados activos ni en los totales.",
      )
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton("Eliminar Miembro") { _, _ ->
        onConfirmDelete()
      }
      .create()

    dialog.setOnShowListener {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
        setTextColor(ContextCompat.getColor(context, R.color.status_expired_text))
        backgroundTintList = ColorStateList.valueOf(
          ContextCompat.getColor(context, R.color.status_expired_container),
        )
      }
    }
    dialog.show()
  }

  private fun plainAmount(value: Double): String =
    value.toBigDecimal().stripTrailingZeros().toPlainString()

  private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
}


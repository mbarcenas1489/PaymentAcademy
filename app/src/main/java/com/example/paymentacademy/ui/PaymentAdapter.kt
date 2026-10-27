package com.example.paymentacademy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.paymentacademy.R
import com.example.paymentacademy.data.model.PaymentSummary
import com.example.paymentacademy.databinding.ItemPaymentBinding
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class PaymentAdapter(
  private val onEdit: (PaymentSummary) -> Unit,
  private val onDelete: (PaymentSummary) -> Unit,
) : ListAdapter<PaymentSummary, PaymentAdapter.PaymentViewHolder>(DiffCallback) {
  private val locale = Locale.forLanguageTag("es-CO")
  private val currency = NumberFormat.getCurrencyInstance(locale)
  private val shortDate = DateTimeFormatter.ofPattern("d MMM", locale)
  private val fullDate = DateTimeFormatter.ofPattern("d MMM uuuu", locale)

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder =
    PaymentViewHolder(
      ItemPaymentBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

  override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  inner class PaymentViewHolder(
    private val binding: ItemPaymentBinding,
  ) : RecyclerView.ViewHolder(binding.root) {
    fun bind(payment: PaymentSummary) = with(binding) {
      val context = root.context
      val from = parseDate(payment.validFrom ?: payment.paidOn)
      val until = parseDate(payment.validUntil ?: payment.paidOn)

      memberName.text = payment.memberName
      paymentAmount.text = currency.format(payment.amount)
      paymentMeta.text = context.getString(
        R.string.payment_meta,
        paymentTypeLabel(payment.paymentType, context),
        paymentMethodLabel(payment.paymentMethod, context),
      )
      paymentPeriod.text = formatPeriod(from, until, payment)
      paymentPaidOn.text = context.getString(
        R.string.payment_paid_on,
        parseDate(payment.paidOn)?.format(fullDate) ?: payment.paidOn,
      )
      bindStatus(paymentStatus(from, until))

      paymentCard.setOnClickListener { onEdit(payment) }
      paymentActions.setOnClickListener { anchor ->
        PopupMenu(context, anchor).apply {
          inflate(R.menu.payment_card_actions)
          setOnMenuItemClickListener { item ->
            when (item.itemId) {
              R.id.action_edit_payment -> onEdit(payment)
              R.id.action_delete_payment -> onDelete(payment)
              else -> return@setOnMenuItemClickListener false
            }
            true
          }
        }.show()
      }
    }

    private fun bindStatus(status: PaymentStatus) = with(binding) {
      val (label, containerColor, textColor) = when (status) {
        PaymentStatus.CURRENT -> Triple(
          R.string.payment_status_current,
          R.color.status_current_container,
          R.color.status_current_text,
        )
        PaymentStatus.UPCOMING -> Triple(
          R.string.payment_status_upcoming,
          R.color.status_upcoming_container,
          R.color.status_upcoming_text,
        )
        PaymentStatus.EXPIRED -> Triple(
          R.string.payment_status_expired,
          R.color.status_expired_container,
          R.color.status_expired_text,
        )
      }
      paymentStatus.setText(label)
      paymentStatus.setTextColor(ContextCompat.getColor(root.context, textColor))
      statusContainer.setCardBackgroundColor(ContextCompat.getColor(root.context, containerColor))
      val isCurrent = status == PaymentStatus.CURRENT
      paymentCard.strokeColor = ContextCompat.getColor(
        root.context,
        if (isCurrent) R.color.status_current_border else R.color.card_stroke,
      )
      paymentCard.strokeWidth = root.resources.getDimensionPixelSize(
        if (isCurrent) R.dimen.payment_card_current_stroke else R.dimen.payment_card_stroke,
      )
    }
  }

  private fun paymentStatus(from: LocalDate?, until: LocalDate?): PaymentStatus {
    val today = LocalDate.now()
    return when {
      from != null && today.isBefore(from) -> PaymentStatus.UPCOMING
      until != null && today.isAfter(until) -> PaymentStatus.EXPIRED
      else -> PaymentStatus.CURRENT
    }
  }

  private fun formatPeriod(
    from: LocalDate?,
    until: LocalDate?,
    payment: PaymentSummary,
  ): String = when {
    from == null || until == null ->
      "${payment.validFrom ?: payment.paidOn} – ${payment.validUntil ?: payment.paidOn}"
    from.year == until.year -> "${from.format(shortDate)} – ${until.format(fullDate)}"
    else -> "${from.format(fullDate)} – ${until.format(fullDate)}"
  }

  private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

  private fun paymentTypeLabel(type: String, context: android.content.Context): String = context.getString(
    when (type) {
      "monthly_fee" -> R.string.type_monthly_fee
      "enrollment" -> R.string.type_enrollment
      "class_package" -> R.string.type_class_package
      "personal_training" -> R.string.type_personal_training
      else -> R.string.type_other
    },
  )

  private fun paymentMethodLabel(method: String, context: android.content.Context): String = context.getString(
    when (method) {
      "cash" -> R.string.method_cash
      "card" -> R.string.method_card
      "transfer" -> R.string.method_transfer
      "online" -> R.string.method_online
      else -> R.string.method_other
    },
  )

  private enum class PaymentStatus { CURRENT, UPCOMING, EXPIRED }

  private object DiffCallback : DiffUtil.ItemCallback<PaymentSummary>() {
    override fun areItemsTheSame(oldItem: PaymentSummary, newItem: PaymentSummary) =
      oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: PaymentSummary, newItem: PaymentSummary) =
      oldItem == newItem
  }
}

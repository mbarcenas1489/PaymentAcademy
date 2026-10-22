package com.example.paymentacademy.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.paymentacademy.R
import com.example.paymentacademy.data.model.MemberPaymentSummary
import com.example.paymentacademy.databinding.ItemMemberPaymentSummaryBinding
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MemberPaymentSummaryAdapter(
  private val onMemberSelected: (MemberPaymentSummary) -> Unit,
) : ListAdapter<MemberPaymentSummary, MemberPaymentSummaryAdapter.MemberViewHolder>(DiffCallback) {
  private val locale = Locale.forLanguageTag("es-CO")
  private val currency = NumberFormat.getCurrencyInstance(locale)
  private val fullDate = DateTimeFormatter.ofPattern("d MMM uuuu", locale)

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder =
    MemberViewHolder(
      ItemMemberPaymentSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

  override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  inner class MemberViewHolder(
    private val binding: ItemMemberPaymentSummaryBinding,
  ) : RecyclerView.ViewHolder(binding.root) {
    fun bind(member: MemberPaymentSummary) = with(binding) {
      val context = root.context
      memberName.text = member.fullName
      memberStatus.text = context.getString(
        when (member.memberStatus) {
          "active" -> R.string.status_active
          "frozen" -> R.string.status_frozen
          else -> R.string.status_inactive
        },
      )
      memberDetails.text = listOfNotNull(
        member.documentNumber,
        member.phone,
        member.email,
      ).filter { it.isNotBlank() }.joinToString(" · ")
        .ifBlank { context.getString(R.string.no_contact) }

      if (member.lastPaymentId == null || member.lastAmount == null) {
        lastPaymentMain.setText(R.string.no_member_payments)
        lastPaymentPeriod.visibility = View.GONE
        paymentStatusContainer.visibility = View.GONE
      } else {
        lastPaymentMain.text = context.getString(
          R.string.latest_payment_main,
          currency.format(member.lastAmount),
          paymentTypeLabel(member.lastPaymentType, context),
        )
        val paidOn = parseDate(member.lastPaidOn)
        val validUntil = parseDate(member.lastValidUntil)
        lastPaymentPeriod.text = context.getString(
          R.string.latest_payment_period,
          paidOn?.format(fullDate) ?: member.lastPaidOn.orEmpty(),
          validUntil?.format(fullDate) ?: member.lastValidUntil.orEmpty(),
        )
        lastPaymentPeriod.visibility = View.VISIBLE
        paymentStatusContainer.visibility = View.VISIBLE
        bindPaymentStatus(parseDate(member.lastValidFrom), validUntil)
      }

      memberCard.setOnClickListener { onMemberSelected(member) }
    }

    private fun bindPaymentStatus(from: LocalDate?, until: LocalDate?) = with(binding) {
      val today = LocalDate.now()
      val status = when {
        from != null && today.isBefore(from) -> PaymentStatus.UPCOMING
        until != null && today.isAfter(until) -> PaymentStatus.EXPIRED
        else -> PaymentStatus.CURRENT
      }
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
      paymentStatusContainer.setCardBackgroundColor(
        ContextCompat.getColor(root.context, containerColor),
      )
    }
  }

  private fun paymentTypeLabel(type: String?, context: android.content.Context): String =
    context.getString(
      when (type) {
        "monthly_fee" -> R.string.type_monthly_fee
        "enrollment" -> R.string.type_enrollment
        "class_package" -> R.string.type_class_package
        "personal_training" -> R.string.type_personal_training
        else -> R.string.type_other
      },
    )

  private fun parseDate(value: String?): LocalDate? =
    value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

  private enum class PaymentStatus { CURRENT, UPCOMING, EXPIRED }

  private object DiffCallback : DiffUtil.ItemCallback<MemberPaymentSummary>() {
    override fun areItemsTheSame(oldItem: MemberPaymentSummary, newItem: MemberPaymentSummary) =
      oldItem.memberId == newItem.memberId

    override fun areContentsTheSame(oldItem: MemberPaymentSummary, newItem: MemberPaymentSummary) =
      oldItem == newItem
  }
}

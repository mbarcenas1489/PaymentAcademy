package com.example.paymentacademy

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.paymentacademy.data.model.PaymentSummary
import com.example.paymentacademy.data.remote.SupabaseProvider
import com.example.paymentacademy.data.repository.SupabasePaymentRepository
import com.example.paymentacademy.databinding.ActivityPaymentsBinding
import com.example.paymentacademy.databinding.ItemPaymentBinding
import com.example.paymentacademy.ui.PaymentsUiState
import com.example.paymentacademy.ui.PaymentsViewModel
import com.example.paymentacademy.ui.PaymentsViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class PaymentsActivity : AppCompatActivity() {
  private lateinit var binding: ActivityPaymentsBinding
  private val viewModel: PaymentsViewModel by viewModels {
    PaymentsViewModelFactory(SupabasePaymentRepository(SupabaseProvider.client))
  }
  private val paymentForm = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      Snackbar.make(binding.root, R.string.payment_saved, Snackbar.LENGTH_SHORT).show()
      viewModel.refresh()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    binding = ActivityPaymentsBinding.inflate(layoutInflater)
    setContentView(binding.root)
    ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
      insets
    }

    binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    binding.addPaymentButton.setOnClickListener { openPaymentForm() }
    binding.refreshPaymentsButton.setOnClickListener { viewModel.refresh() }

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect(::render)
      }
    }
  }

  private fun render(state: PaymentsUiState) = with(binding) {
    progressIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE
    addPaymentButton.isEnabled = !state.isLoading
    refreshPaymentsButton.isEnabled = !state.isLoading
    paymentCount.text = resources.getQuantityString(
      R.plurals.managed_payment_count,
      state.payments.size,
      state.payments.size,
    )
    paymentsContainer.removeAllViews()
    if (!state.isLoading && state.payments.isEmpty()) {
      emptyPayments.visibility = View.VISIBLE
    } else {
      emptyPayments.visibility = View.GONE
      state.payments.forEach { payment ->
        paymentsContainer.addView(paymentItem(payment))
      }
    }
    state.message?.let { message ->
      Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
      viewModel.messageShown()
    }
  }

  private fun paymentItem(payment: PaymentSummary): View {
    val item = ItemPaymentBinding.inflate(layoutInflater, binding.paymentsContainer, false)
    val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"))
    item.memberName.text = payment.memberName
    item.paymentDetails.text = getString(
      R.string.managed_payment_row,
      currency.format(payment.amount),
      paymentTypeLabel(payment.paymentType),
      payment.validFrom ?: payment.paidOn,
      payment.validUntil ?: payment.paidOn,
      paymentMethodLabel(payment.paymentMethod),
      payment.paidOn,
    )
    item.editPaymentButton.setOnClickListener { openPaymentForm(payment) }
    item.deletePaymentButton.setOnClickListener { confirmDelete(payment) }
    return item.root
  }

  private fun openPaymentForm(payment: PaymentSummary? = null) {
    val intent = Intent(this, PaymentFormActivity::class.java)
    payment?.let {
      intent.putExtra(PaymentFormActivity.EXTRA_PAYMENT_ID, it.id)
      intent.putExtra(PaymentFormActivity.EXTRA_MEMBER_ID, it.memberId)
      intent.putExtra(PaymentFormActivity.EXTRA_AMOUNT, it.amount)
      intent.putExtra(PaymentFormActivity.EXTRA_PAID_ON, it.paidOn)
      intent.putExtra(PaymentFormActivity.EXTRA_PAYMENT_TYPE, it.paymentType)
      intent.putExtra(PaymentFormActivity.EXTRA_METHOD, it.paymentMethod)
      intent.putExtra(PaymentFormActivity.EXTRA_VALID_FROM, it.validFrom)
      intent.putExtra(PaymentFormActivity.EXTRA_VALID_UNTIL, it.validUntil)
      intent.putExtra(PaymentFormActivity.EXTRA_REFERENCE, it.reference)
      intent.putExtra(PaymentFormActivity.EXTRA_NOTES, it.notes)
    }
    paymentForm.launch(intent)
  }

  private fun confirmDelete(payment: PaymentSummary) {
    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.delete_payment_title)
      .setMessage(getString(R.string.delete_payment_message, payment.memberName))
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.delete) { _, _ -> viewModel.deletePayment(payment.id) }
      .show()
  }

  private fun paymentMethodLabel(method: String): String = when (method) {
    "cash" -> getString(R.string.method_cash)
    "card" -> getString(R.string.method_card)
    "transfer" -> getString(R.string.method_transfer)
    "online" -> getString(R.string.method_online)
    else -> getString(R.string.method_other)
  }

  private fun paymentTypeLabel(type: String): String = when (type) {
    "monthly_fee" -> getString(R.string.type_monthly_fee)
    "enrollment" -> getString(R.string.type_enrollment)
    "class_package" -> getString(R.string.type_class_package)
    "personal_training" -> getString(R.string.type_personal_training)
    else -> getString(R.string.type_other)
  }
}

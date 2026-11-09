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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.paymentacademy.data.model.PaymentSummary
import com.example.paymentacademy.data.remote.SupabaseProvider
import com.example.paymentacademy.data.repository.SupabasePaymentRepository
import com.example.paymentacademy.databinding.ActivityPaymentsBinding
import com.example.paymentacademy.domain.CurrencyFormatter
import com.example.paymentacademy.receipt.PaymentReceiptSharer
import com.example.paymentacademy.ui.PaymentAdapter
import com.example.paymentacademy.ui.PaymentsUiState
import com.example.paymentacademy.ui.PaymentsViewModel
import com.example.paymentacademy.ui.PaymentsViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class PaymentsActivity : AppCompatActivity() {
  private lateinit var binding: ActivityPaymentsBinding
  private val memberId: String? by lazy { intent.getStringExtra(EXTRA_MEMBER_ID) }
  private val memberName: String? by lazy { intent.getStringExtra(EXTRA_MEMBER_NAME) }
  private val paymentsAdapter = PaymentAdapter(
    ::openPaymentForm,
    ::sharePaymentReceipt,
    ::confirmDelete,
    onRegisterInstallment = ::showInstallmentDialog,
    canManagePayments = { viewModel.uiState.value.organizationRole.canManagePayments },
  )
  private val viewModel: PaymentsViewModel by viewModels {
    PaymentsViewModelFactory(SupabasePaymentRepository(SupabaseProvider.client, applicationContext), memberId)
  }
  private val paymentForm = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      viewModel.refresh()
      Snackbar.make(binding.root, R.string.payment_saved, Snackbar.LENGTH_SHORT).show()
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

    binding.paymentsTitle.text = memberName ?: getString(R.string.manage_payments)
    val details = intent.getStringExtra(EXTRA_MEMBER_DETAILS).orEmpty()
    if (details.isNotBlank()) {
      binding.memberDetails.text = details
      binding.memberDetails.visibility = View.VISIBLE
    }
    binding.paymentsList.layoutManager = LinearLayoutManager(this)
    binding.paymentsList.adapter = paymentsAdapter
    binding.backButton.setOnClickListener { finish() }
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
    val countLabel = resources.getQuantityString(
      R.plurals.managed_payment_count,
      state.payments.size,
      state.payments.size,
    )
    val totalNio = state.payments
      .filter { CurrencyFormatter.normalizeCurrency(it.currency) != CurrencyFormatter.CURRENCY_USD }
      .sumOf { it.amount }
    val totalUsd = state.payments
      .filter { CurrencyFormatter.normalizeCurrency(it.currency) == CurrencyFormatter.CURRENCY_USD }
      .sumOf { it.amount }
    paymentCount.text =
      "$countLabel · ${CurrencyFormatter.formatCurrency(totalNio, CurrencyFormatter.CURRENCY_NIO)} · ${CurrencyFormatter.formatCurrency(totalUsd, CurrencyFormatter.CURRENCY_USD)}"
    paymentsAdapter.submitList(state.payments)
    if (!state.isLoading && state.payments.isEmpty()) {
      emptyPayments.visibility = View.VISIBLE
    } else {
      emptyPayments.visibility = View.GONE
    }
    state.message?.let { message ->
      Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
      viewModel.messageShown()
    }
  }

  private fun openPaymentForm(payment: PaymentSummary? = null) {
    val intent = Intent(this, PaymentFormActivity::class.java)
    intent.putExtra(PaymentFormActivity.EXTRA_MEMBER_ID, payment?.memberId ?: memberId)
    payment?.let {
      intent.putExtra(PaymentFormActivity.EXTRA_PAYMENT_ID, it.id)
      intent.putExtra(PaymentFormActivity.EXTRA_AMOUNT, it.amount)
      intent.putExtra(PaymentFormActivity.EXTRA_CURRENCY, it.currency)
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

  private fun showInstallmentDialog(payment: PaymentSummary) {
    val currency = CurrencyFormatter.normalizeCurrency(payment.currency)
    val input = android.widget.EditText(this).apply {
      inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
      setText(payment.pendingAmount.toBigDecimal().stripTrailingZeros().toPlainString())
      hint = "Monto a abonar (${CurrencyFormatter.currencySymbol(currency)})"
    }
    val container = android.widget.FrameLayout(this).apply {
      val pad = (20 * resources.displayMetrics.density).toInt()
      setPadding(pad, pad / 2, pad, 0)
      addView(input)
    }

    MaterialAlertDialogBuilder(this)
      .setTitle("Registrar abono")
      .setMessage(
        "Miembro: ${payment.memberName}\n" +
          "Abonado hasta hoy: ${CurrencyFormatter.formatCurrency(payment.amount, currency)}\n" +
          "Saldo pendiente: ${CurrencyFormatter.formatCurrency(payment.pendingAmount, currency)}",
      )
      .setView(container)
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton("Guardar abono") { _, _ ->
        val amount = input.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
        if (amount == null || amount <= 0.0 || amount > payment.pendingAmount) {
          Snackbar.make(
            binding.root,
            "Ingresa un abono válido (máx. ${CurrencyFormatter.formatCurrency(payment.pendingAmount, currency)})",
            Snackbar.LENGTH_LONG,
          ).show()
        } else {
          viewModel.registerInstallment(payment, amount) { updated ->
            sharePaymentReceipt(updated)
          }
        }
      }
      .show()
  }

  private fun confirmDelete(payment: PaymentSummary) {
    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.delete_payment_title)
      .setMessage(getString(R.string.delete_payment_message, payment.memberName))
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.delete) { _, _ -> viewModel.deletePayment(payment.id) }
      .show()
  }

  private fun sharePaymentReceipt(payment: PaymentSummary) {
    val shared = runCatching { PaymentReceiptSharer.share(this, payment) }.getOrDefault(false)
    if (!shared) {
      Snackbar.make(binding.root, R.string.receipt_share_error, Snackbar.LENGTH_LONG).show()
    }
  }

  companion object {
    const val EXTRA_MEMBER_ID = "member_id"
    const val EXTRA_MEMBER_NAME = "member_name"
    const val EXTRA_MEMBER_DETAILS = "member_details"
  }
}

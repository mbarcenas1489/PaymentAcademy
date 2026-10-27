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
  private val paymentsAdapter = PaymentAdapter(::openPaymentForm, ::confirmDelete)
  private val viewModel: PaymentsViewModel by viewModels {
    PaymentsViewModelFactory(SupabasePaymentRepository(SupabaseProvider.client, applicationContext), memberId)
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
    binding.paymentsList.layoutManager = LinearLayoutManager(this)
    binding.paymentsList.adapter = paymentsAdapter
    if (memberId != null) {
      binding.paymentsTitle.text = memberName ?: getString(R.string.member_payments)
      binding.memberDetails.text = intent.getStringExtra(EXTRA_MEMBER_DETAILS).orEmpty()
      binding.memberDetails.visibility =
        if (binding.memberDetails.text.isBlank()) View.GONE else View.VISIBLE
    }

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

  companion object {
    const val EXTRA_MEMBER_ID = "member_id"
    const val EXTRA_MEMBER_NAME = "member_name"
    const val EXTRA_MEMBER_DETAILS = "member_details"
  }
}

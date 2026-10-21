package com.example.paymentacademy

import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.paymentacademy.data.remote.SupabaseProvider
import com.example.paymentacademy.data.repository.SupabasePaymentRepository
import com.example.paymentacademy.databinding.ActivityMainBinding
import com.example.paymentacademy.ui.PaymentUiState
import com.example.paymentacademy.ui.PaymentViewModel
import com.example.paymentacademy.ui.PaymentViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private val viewModel: PaymentViewModel by viewModels {
    PaymentViewModelFactory(SupabasePaymentRepository(SupabaseProvider.client))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
      insets
    }

    binding.loginButton.setOnClickListener {
      viewModel.signIn(
        binding.emailInput.text?.toString().orEmpty(),
        binding.passwordInput.text?.toString().orEmpty(),
      )
    }
    binding.refreshButton.setOnClickListener { viewModel.refresh() }
    binding.membersButton.setOnClickListener {
      startActivity(Intent(this, MembersActivity::class.java))
    }
    binding.logoutButton.setOnClickListener { viewModel.signOut() }

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect(::render)
      }
    }
  }

  private fun render(state: PaymentUiState) = with(binding) {
    loginContainer.visibility = if (state.isAuthenticated) View.GONE else View.VISIBLE
    dashboardContainer.visibility = if (state.isAuthenticated) View.VISIBLE else View.GONE
    progressIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE
    loginButton.isEnabled = !state.isLoading
    refreshButton.isEnabled = !state.isLoading
    membersButton.isEnabled = !state.isLoading
    logoutButton.isEnabled = !state.isLoading

    if (state.isAuthenticated) {
      val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"))
      totalValue.text = currency.format(state.totalCollected)
      paymentCount.text = getString(R.string.payment_count, state.payments.size)
      paymentsContainer.removeAllViews()
      if (!state.isLoading && state.payments.isEmpty()) {
        paymentsContainer.addView(paymentText(getString(R.string.empty_payments)))
      } else {
        state.payments.forEach { payment ->
          paymentsContainer.addView(
            paymentText(
              getString(
                R.string.payment_row,
                payment.memberName,
                currency.format(payment.amount),
                payment.paidOn,
                payment.paymentMethod.replace('_', ' ').uppercase(),
              ),
            ),
          )
        }
      }
    }

    state.message?.let { message ->
      Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
      viewModel.messageShown()
    }
  }

  private fun paymentText(value: String) = TextView(this).apply {
    text = value
    textSize = 16f
    setTextColor(getColor(R.color.text_primary))
    setBackgroundResource(R.drawable.payment_item_background)
    setPadding(40, 28, 40, 28)
    val margin = resources.getDimensionPixelSize(R.dimen.payment_item_margin)
    layoutParams = android.widget.LinearLayout.LayoutParams(
      android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
      android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { setMargins(0, 0, 0, margin) }
  }
}

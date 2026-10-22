package com.example.paymentacademy

import android.app.Activity
import android.app.DatePickerDialog
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
import com.example.paymentacademy.data.model.MemberPaymentSummary
import com.example.paymentacademy.data.model.PaymentSummary
import com.example.paymentacademy.data.remote.SupabaseProvider
import com.example.paymentacademy.data.repository.SupabasePaymentRepository
import com.example.paymentacademy.databinding.ActivityMainBinding
import com.example.paymentacademy.ui.MemberPaymentSummaryAdapter
import com.example.paymentacademy.ui.PaymentAdapter
import com.example.paymentacademy.ui.PaymentUiState
import com.example.paymentacademy.ui.PaymentViewModel
import com.example.paymentacademy.ui.PaymentViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private val viewModel: PaymentViewModel by viewModels {
    PaymentViewModelFactory(SupabasePaymentRepository(SupabaseProvider.client))
  }
  private val memberAdapter = MemberPaymentSummaryAdapter(::openMemberPayments)
  private val movementAdapter = PaymentAdapter(::openPaymentForm, ::confirmDelete)
  private val paymentForm = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      Snackbar.make(binding.root, R.string.payment_saved, Snackbar.LENGTH_SHORT).show()
    }
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

    configureLists()
    configureTabs()
    configureDateFilter()
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

  private fun configureLists() = with(binding) {
    memberSummariesList.layoutManager = LinearLayoutManager(this@MainActivity)
    memberSummariesList.adapter = memberAdapter
    movementsList.layoutManager = LinearLayoutManager(this@MainActivity)
    movementsList.adapter = movementAdapter
  }

  private fun configureTabs() = with(binding) {
    dashboardTabs.addTab(dashboardTabs.newTab().setText(R.string.tab_summary))
    dashboardTabs.addTab(dashboardTabs.newTab().setText(R.string.tab_movements))
    dashboardTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
      override fun onTabSelected(tab: TabLayout.Tab) {
        val summarySelected = tab.position == 0
        summaryTab.visibility = if (summarySelected) View.VISIBLE else View.GONE
        movementsTab.visibility = if (summarySelected) View.GONE else View.VISIBLE
      }

      override fun onTabUnselected(tab: TabLayout.Tab) = Unit
      override fun onTabReselected(tab: TabLayout.Tab) = Unit
    })
  }

  private fun configureDateFilter() = with(binding) {
    movementFromInput.setOnClickListener {
      showDatePicker(movementFromInput.text?.toString().orEmpty()) {
        movementFromInput.setText(it.toString())
      }
    }
    movementFromLayout.setEndIconOnClickListener { movementFromInput.performClick() }
    movementToInput.setOnClickListener {
      showDatePicker(movementToInput.text?.toString().orEmpty()) {
        movementToInput.setText(it.toString())
      }
    }
    movementToLayout.setEndIconOnClickListener { movementToInput.performClick() }
    applyMovementFilter.setOnClickListener {
      val from = runCatching { LocalDate.parse(movementFromInput.text.toString()) }.getOrNull()
      val to = runCatching { LocalDate.parse(movementToInput.text.toString()) }.getOrNull()
      if (from == null || to == null) {
        Snackbar.make(root, R.string.date_range_error, Snackbar.LENGTH_LONG).show()
      } else {
        viewModel.setMovementRange(from, to)
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
    applyMovementFilter.isEnabled = !state.isLoading
    logoutButton.isEnabled = !state.isLoading

    if (state.isAuthenticated) {
      val locale = Locale.forLanguageTag("es-CO")
      val currency = NumberFormat.getCurrencyInstance(locale)
      val month = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM 'de' uuuu", locale))
        .replaceFirstChar { it.titlecase(locale) }
      monthTotalLabel.text = getString(R.string.monthly_total, month)
      totalValue.text = currency.format(state.monthlyTotal)
      movementFromInput.setText(state.fromDate.toString())
      movementToInput.setText(state.toDate.toString())
      movementCount.text = resources.getQuantityString(
        R.plurals.movement_count,
        state.movements.size,
        state.movements.size,
      )
      memberAdapter.submitList(state.members)
      movementAdapter.submitList(state.movements)
      emptyMemberSummaries.visibility =
        if (!state.isLoading && state.members.isEmpty()) View.VISIBLE else View.GONE
      emptyMovements.visibility =
        if (!state.isLoading && state.movements.isEmpty()) View.VISIBLE else View.GONE
    }

    state.message?.let { message ->
      Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
      viewModel.messageShown()
    }
  }

  private fun openMemberPayments(member: MemberPaymentSummary) {
    startActivity(
      Intent(this, PaymentsActivity::class.java)
        .putExtra(PaymentsActivity.EXTRA_MEMBER_ID, member.memberId)
        .putExtra(PaymentsActivity.EXTRA_MEMBER_NAME, member.fullName)
        .putExtra(
          PaymentsActivity.EXTRA_MEMBER_DETAILS,
          listOfNotNull(member.documentNumber, member.phone, member.email)
            .filter { it.isNotBlank() }
            .joinToString(" · "),
        ),
    )
  }

  private fun openPaymentForm(payment: PaymentSummary) {
    paymentForm.launch(
      Intent(this, PaymentFormActivity::class.java).apply {
        putExtra(PaymentFormActivity.EXTRA_PAYMENT_ID, payment.id)
        putExtra(PaymentFormActivity.EXTRA_MEMBER_ID, payment.memberId)
        putExtra(PaymentFormActivity.EXTRA_AMOUNT, payment.amount)
        putExtra(PaymentFormActivity.EXTRA_PAID_ON, payment.paidOn)
        putExtra(PaymentFormActivity.EXTRA_PAYMENT_TYPE, payment.paymentType)
        putExtra(PaymentFormActivity.EXTRA_METHOD, payment.paymentMethod)
        putExtra(PaymentFormActivity.EXTRA_VALID_FROM, payment.validFrom)
        putExtra(PaymentFormActivity.EXTRA_VALID_UNTIL, payment.validUntil)
        putExtra(PaymentFormActivity.EXTRA_REFERENCE, payment.reference)
        putExtra(PaymentFormActivity.EXTRA_NOTES, payment.notes)
      },
    )
  }

  private fun confirmDelete(payment: PaymentSummary) {
    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.delete_payment_title)
      .setMessage(getString(R.string.delete_payment_message, payment.memberName))
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.delete) { _, _ -> viewModel.deletePayment(payment.id) }
      .show()
  }

  private fun showDatePicker(currentValue: String, onSelected: (LocalDate) -> Unit) {
    val initial = runCatching { LocalDate.parse(currentValue) }.getOrDefault(LocalDate.now())
    DatePickerDialog(
      this,
      { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
      initial.year,
      initial.monthValue - 1,
      initial.dayOfMonth,
    ).show()
  }

  override fun onResume() {
    super.onResume()
    if (::binding.isInitialized && viewModel.uiState.value.isAuthenticated) {
      viewModel.refresh()
    }
  }
}

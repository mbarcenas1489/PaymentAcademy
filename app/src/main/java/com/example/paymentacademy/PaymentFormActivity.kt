package com.example.paymentacademy

import android.app.Activity
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.paymentacademy.data.model.Member
import com.example.paymentacademy.data.remote.SupabaseProvider
import com.example.paymentacademy.data.repository.SupabaseMemberRepository
import com.example.paymentacademy.data.repository.SupabasePaymentRepository
import com.example.paymentacademy.databinding.ActivityPaymentFormBinding
import com.example.paymentacademy.ui.PaymentFormUiState
import com.example.paymentacademy.ui.PaymentFormViewModel
import com.example.paymentacademy.ui.PaymentFormViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.time.LocalDate

class PaymentFormActivity : AppCompatActivity() {
  private lateinit var binding: ActivityPaymentFormBinding
  private val viewModel: PaymentFormViewModel by viewModels {
    PaymentFormViewModelFactory(
      SupabasePaymentRepository(SupabaseProvider.client),
      SupabaseMemberRepository(SupabaseProvider.client),
    )
  }
  private var selectedMemberId: String? = null
  private var memberOptionsLoaded = false
  private val paymentId: String? by lazy { intent.getStringExtra(EXTRA_PAYMENT_ID) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    binding = ActivityPaymentFormBinding.inflate(layoutInflater)
    setContentView(binding.root)
    ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
      insets
    }

    binding.formTitle.setText(
      if (paymentId == null) R.string.new_payment_title else R.string.edit_payment_title,
    )
    populateExistingPayment()
    configureMethods()
    configurePaymentTypes()
    binding.cancelButton.setOnClickListener { finish() }
    binding.paidOnInput.setOnClickListener {
      showDatePicker(binding.paidOnInput.text?.toString().orEmpty()) { date ->
        binding.paidOnInput.setText(date.toString())
        binding.paidOnLayout.error = null
      }
    }
    binding.paidOnLayout.setEndIconOnClickListener { binding.paidOnInput.performClick() }
    binding.validFromInput.setOnClickListener {
      showDatePicker(binding.validFromInput.text?.toString().orEmpty()) { date ->
        binding.validFromInput.setText(date.toString())
        binding.validUntilInput.setText(date.plusMonths(1).toString())
        binding.validFromLayout.error = null
        binding.validUntilLayout.error = null
      }
    }
    binding.validFromLayout.setEndIconOnClickListener { binding.validFromInput.performClick() }
    binding.validUntilInput.setOnClickListener {
      showDatePicker(binding.validUntilInput.text?.toString().orEmpty()) { date ->
        binding.validUntilInput.setText(date.toString())
        binding.validUntilLayout.error = null
      }
    }
    binding.validUntilLayout.setEndIconOnClickListener { binding.validUntilInput.performClick() }
    binding.savePaymentButton.setOnClickListener {
      viewModel.save(
        paymentId = paymentId,
        memberId = selectedMemberId,
        amountText = binding.amountInput.text?.toString().orEmpty(),
        paidOn = binding.paidOnInput.text?.toString().orEmpty(),
        paymentType = selectedPaymentTypeValue(),
        paymentMethod = selectedMethodValue(),
        validFrom = binding.validFromInput.text?.toString().orEmpty(),
        validUntil = binding.validUntilInput.text?.toString().orEmpty(),
        reference = binding.referenceInput.text?.toString().orEmpty(),
        notes = binding.notesInput.text?.toString().orEmpty(),
      )
    }

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect(::render)
      }
    }
  }

  private fun populateExistingPayment() = with(binding) {
    selectedMemberId = intent.getStringExtra(EXTRA_MEMBER_ID)
    val amount = intent.getDoubleExtra(EXTRA_AMOUNT, Double.NaN)
    if (!amount.isNaN()) amountInput.setText(amount.toBigDecimal().stripTrailingZeros().toPlainString())
    paidOnInput.setText(intent.getStringExtra(EXTRA_PAID_ON) ?: LocalDate.now().toString())
    val defaultStart = intent.getStringExtra(EXTRA_VALID_FROM) ?: LocalDate.now().toString()
    validFromInput.setText(defaultStart)
    validUntilInput.setText(
      intent.getStringExtra(EXTRA_VALID_UNTIL)
        ?: LocalDate.parse(defaultStart).plusMonths(1).toString(),
    )
    referenceInput.setText(intent.getStringExtra(EXTRA_REFERENCE).orEmpty())
    notesInput.setText(intent.getStringExtra(EXTRA_NOTES).orEmpty())
  }

  private fun configureMethods() {
    val labels = METHOD_VALUES.map { paymentMethodLabel(it) }
    binding.methodInput.setAdapter(
      ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels),
    )
    val existing = intent.getStringExtra(EXTRA_METHOD) ?: "cash"
    binding.methodInput.setText(paymentMethodLabel(existing), false)
  }

  private fun configurePaymentTypes() {
    val labels = PAYMENT_TYPE_VALUES.map { paymentTypeLabel(it) }
    binding.typeInput.setAdapter(
      ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels),
    )
    val existing = intent.getStringExtra(EXTRA_PAYMENT_TYPE) ?: "monthly_fee"
    binding.typeInput.setText(paymentTypeLabel(existing), false)
  }

  private fun configureMembers(members: List<Member>) {
    if (memberOptionsLoaded) return
    memberOptionsLoaded = true
    binding.memberInput.setAdapter(
      ArrayAdapter(
        this,
        android.R.layout.simple_dropdown_item_1line,
        members.map { it.fullName },
      ),
    )
    binding.memberInput.setOnItemClickListener { _, _, position, _ ->
      selectedMemberId = members[position].id
      binding.memberLayout.error = null
    }
    val selected = members.firstOrNull { it.id == selectedMemberId }
    if (selected != null) binding.memberInput.setText(selected.fullName, false)
  }

  private fun render(state: PaymentFormUiState) = with(binding) {
    progressIndicator.visibility = if (state.isLoading || state.isSaving) View.VISIBLE else View.GONE
    savePaymentButton.isEnabled = !state.isLoading && !state.isSaving && state.members.isNotEmpty()
    cancelButton.isEnabled = !state.isSaving
    memberLayout.error = state.memberError
    amountLayout.error = state.amountError
    paidOnLayout.error = state.dateError
    validFromLayout.error = state.validFromError
    validUntilLayout.error = state.validUntilError
    configureMembers(state.members)
    if (state.members.isEmpty() && !state.isLoading) {
      memberLayout.error = getString(R.string.no_active_members)
    }
    if (state.isSaved) {
      setResult(Activity.RESULT_OK)
      finish()
      return@with
    }
    state.message?.let {
      Snackbar.make(root, it, Snackbar.LENGTH_LONG).show()
      viewModel.messageShown()
    }
  }

  private fun showDatePicker(currentValue: String, onSelected: (LocalDate) -> Unit) {
    val initial = runCatching {
      LocalDate.parse(currentValue)
    }.getOrDefault(LocalDate.now())
    DatePickerDialog(
      this,
      { _, year, month, day ->
        onSelected(LocalDate.of(year, month + 1, day))
      },
      initial.year,
      initial.monthValue - 1,
      initial.dayOfMonth,
    ).show()
  }

  private fun selectedPaymentTypeValue(): String {
    val selectedLabel = binding.typeInput.text?.toString().orEmpty()
    return PAYMENT_TYPE_VALUES.firstOrNull { paymentTypeLabel(it) == selectedLabel } ?: "other"
  }

  private fun selectedMethodValue(): String {
    val selectedLabel = binding.methodInput.text?.toString().orEmpty()
    return METHOD_VALUES.firstOrNull { paymentMethodLabel(it) == selectedLabel } ?: "other"
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

  companion object {
    const val EXTRA_PAYMENT_ID = "payment_id"
    const val EXTRA_MEMBER_ID = "member_id"
    const val EXTRA_AMOUNT = "amount"
    const val EXTRA_PAID_ON = "paid_on"
    const val EXTRA_PAYMENT_TYPE = "payment_type"
    const val EXTRA_METHOD = "payment_method"
    const val EXTRA_VALID_FROM = "valid_from"
    const val EXTRA_VALID_UNTIL = "valid_until"
    const val EXTRA_REFERENCE = "reference"
    const val EXTRA_NOTES = "notes"
    private val METHOD_VALUES = listOf("cash", "card", "transfer", "online", "other")
    private val PAYMENT_TYPE_VALUES =
      listOf("monthly_fee", "enrollment", "class_package", "personal_training", "other")
  }
}

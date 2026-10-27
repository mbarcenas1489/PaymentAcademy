package com.example.paymentacademy

import android.app.Activity
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.paymentacademy.data.model.Member
import com.example.paymentacademy.data.remote.SupabaseProvider
import com.example.paymentacademy.data.repository.SupabaseMemberRepository
import com.example.paymentacademy.data.repository.SupabasePaymentRepository
import com.example.paymentacademy.databinding.ActivityPaymentFormBinding
import com.example.paymentacademy.ui.MemberSelectionAdapter
import com.example.paymentacademy.ui.PaymentFormUiState
import com.example.paymentacademy.ui.PaymentFormViewModel
import com.example.paymentacademy.ui.PaymentFormViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

class PaymentFormActivity : AppCompatActivity() {
  private lateinit var binding: ActivityPaymentFormBinding
  private val viewModel: PaymentFormViewModel by viewModels {
    PaymentFormViewModelFactory(
      SupabasePaymentRepository(SupabaseProvider.client, applicationContext),
      SupabaseMemberRepository(SupabaseProvider.client, applicationContext),
    )
  }
  private val paymentId: String? by lazy { intent.getStringExtra(EXTRA_PAYMENT_ID) }
  private val lockedMemberId: String? by lazy { intent.getStringExtra(EXTRA_MEMBER_ID) }
  private var currentStep = 1
  private var allMembers: List<Member> = emptyList()
  private var selectedMember: Member? = null
  private var membersLoaded = false
  private val memberAdapter = MemberSelectionAdapter(::selectMember)

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
    binding.membersList.layoutManager = LinearLayoutManager(this)
    binding.membersList.adapter = memberAdapter
    populateExistingPayment()
    configureDropdowns()
    configureDatePickers()
    configureNavigation()
    binding.memberSearchInput.doAfterTextChanged { filterMembers(it?.toString().orEmpty()) }
    binding.cancelButton.setOnClickListener { finish() }

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect(::render)
      }
    }
    binding.stepProgress.max = visibleStepCount()
    showStep(firstStep())
  }

  private fun configureNavigation() {
    binding.previousButton.setOnClickListener {
      if (currentStep > firstStep()) showStep(currentStep - 1)
    }
    binding.nextButton.setOnClickListener {
      if (validateStep(currentStep)) showStep(currentStep + 1)
    }
    binding.savePaymentButton.setOnClickListener { savePayment() }
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        if (currentStep > firstStep()) showStep(currentStep - 1) else finish()
      }
    })
  }

  private fun showStep(step: Int) = with(binding) {
    currentStep = step.coerceIn(firstStep(), TOTAL_STEPS)
    stepMemberContainer.visibility = if (currentStep == 1) View.VISIBLE else View.GONE
    stepDetailsContainer.visibility = if (currentStep == 2) View.VISIBLE else View.GONE
    stepValidityContainer.visibility = if (currentStep == 3) View.VISIBLE else View.GONE
    stepOptionalContainer.visibility = if (currentStep == 4) View.VISIBLE else View.GONE
    previousButton.visibility = if (currentStep == firstStep()) View.INVISIBLE else View.VISIBLE
    nextButton.visibility = if (currentStep == TOTAL_STEPS) View.GONE else View.VISIBLE
    savePaymentButton.visibility = if (currentStep == TOTAL_STEPS) View.VISIBLE else View.GONE
    val visibleStep = currentStep - firstStep() + 1
    stepIndicator.text = getString(R.string.step_of, visibleStep, visibleStepCount())
    stepProgress.setProgressCompat(visibleStep, true)
    if (currentStep == TOTAL_STEPS) updateReview()
  }

  private fun validateStep(step: Int): Boolean = when (step) {
    1 -> {
      val valid = selectedMember != null
      binding.memberSearchLayout.error = if (valid) null else getString(R.string.select_member_error)
      if (!valid) binding.membersList.scrollToPosition(0)
      valid
    }
    2 -> {
      val amount = parseAmount()
      val dateValid = runCatching {
        LocalDate.parse(binding.paidOnInput.text?.toString().orEmpty())
      }.isSuccess
      binding.amountLayout.error = if (amount == null || amount <= 0) {
        getString(R.string.amount_error)
      } else null
      binding.paidOnLayout.error = if (dateValid) null else getString(R.string.date_error)
      amount != null && amount > 0 && dateValid
    }
    3 -> {
      val from = runCatching {
        LocalDate.parse(binding.validFromInput.text?.toString().orEmpty())
      }.getOrNull()
      val until = runCatching {
        LocalDate.parse(binding.validUntilInput.text?.toString().orEmpty())
      }.getOrNull()
      binding.validFromLayout.error = if (from == null) getString(R.string.valid_from_error) else null
      binding.validUntilLayout.error = when {
        until == null -> getString(R.string.valid_until_error)
        from != null && until.isBefore(from) -> getString(R.string.validity_order_error)
        else -> null
      }
      from != null && until != null && !until.isBefore(from)
    }
    else -> true
  }

  private fun savePayment() {
    if (!validateStep(3)) {
      showStep(3)
      return
    }
    viewModel.save(
      paymentId = paymentId,
      memberId = selectedMember?.id,
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

  private fun selectMember(member: Member) {
    selectedMember = member
    memberAdapter.selectedMemberId = member.id
    binding.selectedMemberLabel.text = getString(R.string.selected_member, member.fullName)
    binding.selectedMemberLabel.visibility = View.VISIBLE
    binding.detailsMemberLabel.text = member.fullName
    binding.memberSearchLayout.error = null
  }

  private fun filterMembers(query: String) {
    val normalized = query.trim().lowercase()
    val filtered = if (normalized.isBlank()) allMembers else allMembers.filter { member ->
      listOf(member.fullName, member.documentNumber, member.phone, member.email)
        .filterNotNull()
        .any { it.lowercase().contains(normalized) }
    }
    memberAdapter.submitList(filtered)
    binding.emptyMemberSearch.visibility = if (membersLoaded && filtered.isEmpty()) {
      View.VISIBLE
    } else {
      View.GONE
    }
    binding.emptyMemberSearch.text = getString(
      if (allMembers.isEmpty()) R.string.no_active_members else R.string.no_members_found,
    )
  }

  private fun populateExistingPayment() = with(binding) {
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

  private fun configureDropdowns() {
    binding.methodInput.setAdapter(
      ArrayAdapter(
        this,
        android.R.layout.simple_dropdown_item_1line,
        METHOD_VALUES.map(::paymentMethodLabel),
      ),
    )
    binding.methodInput.setText(
      paymentMethodLabel(intent.getStringExtra(EXTRA_METHOD) ?: "cash"),
      false,
    )
    binding.typeInput.setAdapter(
      ArrayAdapter(
        this,
        android.R.layout.simple_dropdown_item_1line,
        PAYMENT_TYPE_VALUES.map(::paymentTypeLabel),
      ),
    )
    binding.typeInput.setText(
      paymentTypeLabel(intent.getStringExtra(EXTRA_PAYMENT_TYPE) ?: "monthly_fee"),
      false,
    )
  }

  private fun configureDatePickers() = with(binding) {
    paidOnInput.setOnClickListener {
      showDatePicker(paidOnInput.text?.toString().orEmpty()) { date ->
        paidOnInput.setText(date.toString())
        paidOnLayout.error = null
      }
    }
    paidOnLayout.setEndIconOnClickListener { paidOnInput.performClick() }
    validFromInput.setOnClickListener {
      showDatePicker(validFromInput.text?.toString().orEmpty()) { date ->
        validFromInput.setText(date.toString())
        validUntilInput.setText(date.plusMonths(1).toString())
        validFromLayout.error = null
        validUntilLayout.error = null
      }
    }
    validFromLayout.setEndIconOnClickListener { validFromInput.performClick() }
    validUntilInput.setOnClickListener {
      showDatePicker(validUntilInput.text?.toString().orEmpty()) { date ->
        validUntilInput.setText(date.toString())
        validUntilLayout.error = null
      }
    }
    validUntilLayout.setEndIconOnClickListener { validUntilInput.performClick() }
  }

  private fun render(state: PaymentFormUiState) = with(binding) {
    progressIndicator.visibility = if (state.isLoading || state.isSaving) View.VISIBLE else View.GONE
    membersLoaded = !state.isLoading
    if (state.members != allMembers) {
      allMembers = state.members
      if (selectedMember == null && lockedMemberId != null) {
        state.members.firstOrNull { it.id == lockedMemberId }?.let(::selectMember)
      }
    }
    filterMembers(memberSearchInput.text?.toString().orEmpty())

    // La selección bloqueada debe resolverse antes de calcular el estado de navegación.
    val memberReady = state.members.isNotEmpty() &&
      (lockedMemberId == null || selectedMember != null)
    nextButton.isEnabled = !state.isLoading && !state.isSaving && memberReady
    savePaymentButton.isEnabled = nextButton.isEnabled
    previousButton.isEnabled = !state.isSaving
    cancelButton.isEnabled = !state.isSaving

    amountLayout.error = state.amountError
    paidOnLayout.error = state.dateError
    validFromLayout.error = state.validFromError
    validUntilLayout.error = state.validUntilError
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

  private fun updateReview() {
    val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"))
    val amount = parseAmount()?.let(currency::format) ?: binding.amountInput.text.toString()
    binding.reviewSummary.text = getString(
      R.string.payment_review,
      selectedMember?.fullName.orEmpty(),
      amount,
      paymentTypeLabel(selectedPaymentTypeValue()),
      binding.validFromInput.text?.toString().orEmpty(),
      binding.validUntilInput.text?.toString().orEmpty(),
    )
  }

  private fun parseAmount(): Double? =
    binding.amountInput.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()

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

  private fun selectedMethodValue(): String {
    val label = binding.methodInput.text?.toString().orEmpty()
    return METHOD_VALUES.firstOrNull { paymentMethodLabel(it) == label } ?: "other"
  }

  private fun selectedPaymentTypeValue(): String {
    val label = binding.typeInput.text?.toString().orEmpty()
    return PAYMENT_TYPE_VALUES.firstOrNull { paymentTypeLabel(it) == label } ?: "other"
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

  private fun firstStep(): Int = if (lockedMemberId == null) 1 else 2

  private fun visibleStepCount(): Int = TOTAL_STEPS - firstStep() + 1

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
    private const val TOTAL_STEPS = 4
    private val METHOD_VALUES = listOf("cash", "card", "transfer", "online", "other")
    private val PAYMENT_TYPE_VALUES =
      listOf("monthly_fee", "enrollment", "class_package", "personal_training", "other")
  }
}

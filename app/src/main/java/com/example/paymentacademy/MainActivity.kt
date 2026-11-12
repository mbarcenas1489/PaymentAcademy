package com.example.paymentacademy

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.paymentacademy.data.model.MemberPaymentSummary
import com.example.paymentacademy.data.model.PaymentSummary
import com.example.paymentacademy.data.remote.SupabaseProvider
import com.example.paymentacademy.data.repository.SupabaseMemberRepository
import com.example.paymentacademy.data.repository.SupabasePaymentRepository
import com.example.paymentacademy.databinding.ActivityMainBinding
import com.example.paymentacademy.domain.CurrencyFormatter
import com.example.paymentacademy.domain.SyncAuditReport
import com.example.paymentacademy.receipt.PaymentReceiptSharer
import com.example.paymentacademy.ui.MemberDialogs
import com.example.paymentacademy.ui.MemberPaymentSummaryAdapter
import com.example.paymentacademy.ui.PaymentAdapter
import com.example.paymentacademy.ui.PaymentUiState
import com.example.paymentacademy.ui.PaymentViewModel
import com.example.paymentacademy.ui.PaymentViewModelFactory
import com.example.paymentacademy.ui.SyncStatus
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private var logoutDialog: AlertDialog? = null
  private var syncAuditDialog: AlertDialog? = null
  private val viewModel: PaymentViewModel by viewModels {
    PaymentViewModelFactory(
      SupabasePaymentRepository(SupabaseProvider.client, applicationContext),
      SupabaseMemberRepository(SupabaseProvider.client, applicationContext),
    )
  }
  private val memberAdapter = MemberPaymentSummaryAdapter(
    onMemberSelected = ::openMemberPayments,
    onRegisterInstallment = ::showInstallmentForMember,
    onNewPayment = ::openNewPaymentForMember,
    onEditMember = ::showEditMemberDialog,
    onDeleteMember = ::showDeleteMemberDialog,
    canManagePayments = { viewModel.uiState.value.organizationRole.canManagePayments },
  )
  private val movementAdapter = PaymentAdapter(
    ::openPaymentForm,
    ::sharePaymentReceipt,
    ::confirmDelete,
    onRegisterInstallment = ::showInstallmentDialog,
    canManagePayments = { viewModel.uiState.value.organizationRole.canManagePayments },
  )
  private val paymentForm = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      Snackbar.make(binding.root, R.string.payment_saved, Snackbar.LENGTH_SHORT).show()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
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
    observeSynchronization()
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
    binding.logoutButton.setOnClickListener { viewModel.requestSignOut() }
    binding.syncNowButton.setOnClickListener { viewModel.synchronizeNow() }
    binding.auditSyncButton.setOnClickListener { viewModel.auditSynchronization() }

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect(::render)
      }
    }
  }

  private fun observeSynchronization() {
    WorkManager.getInstance(this)
      .getWorkInfosForUniqueWorkLiveData(SYNC_WORK_NAME)
      .observe(this) { workInfos ->
        when {
          workInfos.any { it.state == WorkInfo.State.RUNNING } ->
            viewModel.backgroundSyncStarted()
          workInfos.any {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
          } -> viewModel.backgroundSyncQueued()
          workInfos.isNotEmpty() && workInfos.all { it.state.isFinished } ->
            viewModel.refreshSyncStatus()
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
    syncNowButton.isEnabled = !state.isLoading
    auditSyncButton.isEnabled = !state.isLoading

    if (state.isAuthenticated) {
      val locale = Locale.forLanguageTag("es-CO")
      val month = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM 'de' uuuu", locale))
        .replaceFirstChar { it.titlecase(locale) }
      monthTotalLabel.text = getString(R.string.monthly_total, month)
      totalValue.text = CurrencyFormatter.formatCurrency(
        state.monthlyTotalNIO,
        CurrencyFormatter.CURRENCY_NIO,
      )
      totalValueUsd.text = CurrencyFormatter.formatCurrency(
        state.monthlyTotalUSD,
        CurrencyFormatter.CURRENCY_USD,
      )
      movementFromInput.setText(state.fromDate.toString())
      movementToInput.setText(state.toDate.toString())
      val countLabel = resources.getQuantityString(
        R.plurals.movement_count,
        state.movements.size,
        state.movements.size,
      )
      val movementsNio = state.movements
        .filter { CurrencyFormatter.normalizeCurrency(it.currency) != CurrencyFormatter.CURRENCY_USD }
        .sumOf { it.amount }
      val movementsUsd = state.movements
        .filter { CurrencyFormatter.normalizeCurrency(it.currency) == CurrencyFormatter.CURRENCY_USD }
        .sumOf { it.amount }
      movementCount.text =
        "$countLabel · ${CurrencyFormatter.formatCurrency(movementsNio, CurrencyFormatter.CURRENCY_NIO)} · ${CurrencyFormatter.formatCurrency(movementsUsd, CurrencyFormatter.CURRENCY_USD)}"
      memberAdapter.submitList(state.members)
      movementAdapter.submitList(state.movements)
      renderSyncStatus(state.syncStatus, state.pendingSyncCount)
      emptyMemberSummaries.visibility =
        if (!state.isLoading && state.members.isEmpty()) View.VISIBLE else View.GONE
      emptyMovements.visibility =
        if (!state.isLoading && state.movements.isEmpty()) View.VISIBLE else View.GONE
    }

    renderLogoutPrompt(state.pendingLogoutCount)
    renderSyncAudit(state.syncAuditReport)

    state.message?.let { message ->
      Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
      viewModel.messageShown()
    }
  }

  private fun renderSyncAudit(report: SyncAuditReport?) {
    if (report == null) {
      syncAuditDialog?.dismiss()
      syncAuditDialog = null
      return
    }
    if (syncAuditDialog?.isShowing == true) return
    val title = if (report.hasDifferences) {
      R.string.sync_audit_difference_title
    } else {
      R.string.sync_audit_clean_title
    }
    val message = if (report.hasDifferences) {
      getString(
        R.string.sync_audit_difference_message,
        report.localMemberCount,
        report.remoteMemberCount,
        report.localPaymentCount,
        report.remotePaymentCount,
        report.membersMissingLocally,
        report.paymentsMissingLocally,
        report.synchronizedMembersMissingRemotely,
        report.synchronizedPaymentsMissingRemotely,
        report.pendingChanges,
      )
    } else {
      getString(
        R.string.sync_audit_clean_message,
        report.localMemberCount,
        report.remoteMemberCount,
        report.localPaymentCount,
        report.remotePaymentCount,
        report.pendingChanges,
      )
    }
    syncAuditDialog = MaterialAlertDialogBuilder(this)
      .setTitle(title)
      .setMessage(message)
      .setPositiveButton(R.string.done) { _, _ -> viewModel.dismissSyncAudit() }
      .setOnCancelListener { viewModel.dismissSyncAudit() }
      .create()
      .also { dialog ->
        dialog.setOnDismissListener { syncAuditDialog = null }
        dialog.show()
      }
  }

  private fun renderSyncStatus(status: SyncStatus, pendingCount: Int) = with(binding) {
    syncStatusText.text = when (status) {
      SyncStatus.SYNCED -> getString(R.string.sync_status_synced)
      SyncStatus.PENDING -> resources.getQuantityString(
        R.plurals.pending_sync_count,
        pendingCount,
        pendingCount,
      )
      SyncStatus.SYNCING -> getString(R.string.sync_status_syncing)
      SyncStatus.WAITING -> getString(R.string.sync_status_waiting)
      SyncStatus.ERROR -> getString(R.string.sync_status_error)
    }
    syncStatusText.setTextColor(
      getColor(
        when (status) {
          SyncStatus.SYNCED -> R.color.status_current_text
          SyncStatus.PENDING -> R.color.status_upcoming_text
          SyncStatus.SYNCING -> R.color.text_secondary
          SyncStatus.WAITING -> R.color.status_upcoming_text
          SyncStatus.ERROR -> R.color.status_expired_text
        },
      ),
    )
    syncNowButton.visibility = if (
      status == SyncStatus.PENDING || status == SyncStatus.WAITING || status == SyncStatus.ERROR
    ) {
      View.VISIBLE
    } else {
      View.GONE
    }
  }

  private fun renderLogoutPrompt(pendingCount: Int?) {
    if (pendingCount == null) {
      logoutDialog?.dismiss()
      logoutDialog = null
      return
    }
    if (logoutDialog?.isShowing == true) return
    logoutDialog = MaterialAlertDialogBuilder(this)
      .setTitle(R.string.pending_logout_title)
      .setMessage(getString(R.string.pending_logout_message, pendingCount))
      .setNegativeButton(R.string.cancel) { _, _ -> viewModel.cancelSignOut() }
      .setPositiveButton(R.string.sync_and_sign_out) { _, _ ->
        viewModel.synchronizeAndSignOut()
      }
      .setOnCancelListener { viewModel.cancelSignOut() }
      .create()
      .also { dialog ->
        dialog.setOnDismissListener { logoutDialog = null }
        dialog.show()
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

  private fun openNewPaymentForMember(member: MemberPaymentSummary) {
    paymentForm.launch(
      Intent(this, PaymentFormActivity::class.java)
        .putExtra(PaymentFormActivity.EXTRA_MEMBER_ID, member.memberId),
    )
  }

  private fun showEditMemberDialog(member: MemberPaymentSummary) {
    MemberDialogs.showEditMemberDialog(
      context = this,
      fullName = member.fullName,
      documentNumber = member.documentNumber,
      status = member.memberStatus,
      phone = member.phone,
      email = member.email,
      notes = member.notes,
      onSave = { request -> viewModel.updateMember(member.memberId, request) },
    )
  }

  private fun showDeleteMemberDialog(member: MemberPaymentSummary) {
    MemberDialogs.showDeleteMemberDialog(
      context = this,
      memberName = member.fullName,
      onConfirmDelete = { viewModel.deleteMember(member.memberId) },
    )
  }

  private fun showInstallmentForMember(member: MemberPaymentSummary) {
    val paymentId = member.lastPaymentId ?: return
    val amount = member.lastAmount ?: return
    val pending = member.lastPendingAmount ?: 0.0
    if (pending <= 0.0) return
    val existingMovement = viewModel.uiState.value.movements.firstOrNull { it.id == paymentId }
    val paymentSummary = existingMovement ?: PaymentSummary(
      id = paymentId,
      organizationId = member.organizationId,
      memberId = member.memberId,
      memberName = member.fullName,
      memberDocument = member.documentNumber,
      amount = amount,
      pendingAmount = pending,
      currency = CurrencyFormatter.normalizeCurrency(member.lastCurrency),
      paidOn = member.lastPaidOn ?: LocalDate.now().toString(),
      paymentType = member.lastPaymentType ?: "monthly_fee",
      paymentMethod = member.lastPaymentMethod ?: "cash",
      validFrom = member.lastValidFrom,
      validUntil = member.lastValidUntil,
      createdAt = member.lastPaidOn ?: LocalDate.now().toString(),
    )
    showInstallmentDialog(paymentSummary)
  }

  private fun openPaymentForm(payment: PaymentSummary) {
    paymentForm.launch(
      Intent(this, PaymentFormActivity::class.java).apply {
        putExtra(PaymentFormActivity.EXTRA_PAYMENT_ID, payment.id)
        putExtra(PaymentFormActivity.EXTRA_MEMBER_ID, payment.memberId)
        putExtra(PaymentFormActivity.EXTRA_AMOUNT, payment.amount)
        putExtra(PaymentFormActivity.EXTRA_CURRENCY, payment.currency)
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
      com.example.paymentacademy.worker.SyncWorker.schedule(this)
    }
  }

  private companion object {
    const val SYNC_WORK_NAME = "PaymentAcademySync"
  }
}

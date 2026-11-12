package com.example.paymentacademy

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.paymentacademy.data.remote.SupabaseProvider
import com.example.paymentacademy.data.repository.SupabaseMemberRepository
import com.example.paymentacademy.data.repository.SupabasePaymentRepository
import com.example.paymentacademy.databinding.ActivityMembersBinding
import com.example.paymentacademy.domain.CurrencyFormatter
import com.example.paymentacademy.ui.MemberDialogs
import com.example.paymentacademy.ui.MembersUiState
import com.example.paymentacademy.ui.MembersViewModel
import com.example.paymentacademy.ui.MembersViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MembersActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMembersBinding
  private val viewModel: MembersViewModel by viewModels {
    MembersViewModelFactory(
      SupabaseMemberRepository(SupabaseProvider.client, applicationContext),
      SupabasePaymentRepository(SupabaseProvider.client, applicationContext),
    )
  }
  private val registerMember = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      Snackbar.make(binding.root, R.string.member_saved, Snackbar.LENGTH_SHORT).show()
      viewModel.refresh()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    binding = ActivityMembersBinding.inflate(layoutInflater)
    setContentView(binding.root)
    ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
      insets
    }

    binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    binding.addMemberButton.setOnClickListener {
      registerMember.launch(Intent(this, RegisterMemberActivity::class.java))
    }
    binding.refreshMembersButton.setOnClickListener { viewModel.refresh() }

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect(::render)
      }
    }
  }

  private fun render(state: MembersUiState) = with(binding) {
    progressIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE
    addMemberButton.isEnabled = !state.isLoading
    refreshMembersButton.isEnabled = !state.isLoading
    memberCount.text = resources.getQuantityString(
      R.plurals.member_count,
      state.members.size,
      state.members.size,
    )
    membersContainer.removeAllViews()
    if (!state.isLoading && state.members.isEmpty()) {
      membersContainer.addView(memberText(getString(R.string.empty_members)))
    } else {
      state.members.forEach { member ->
        val itemBinding = com.example.paymentacademy.databinding.ItemMemberBinding.inflate(
          layoutInflater,
          membersContainer,
          false,
        )
        itemBinding.textFullName.text = member.fullName

        val contact = listOfNotNull(member.documentNumber, member.phone, member.email)
          .filter { it.isNotBlank() }
          .joinToString(" · ")
        itemBinding.textContact.text = if (contact.isBlank()) getString(R.string.no_contact) else contact

        itemBinding.textJoinedOn.text = getString(R.string.joined_on, member.joinedOn)

        if (member.isSyncPending) {
          itemBinding.iconSyncStatus.setImageResource(R.drawable.ic_cloud_upload)
          itemBinding.iconSyncStatus.imageTintList =
            android.content.res.ColorStateList.valueOf(getColor(R.color.status_expired_text))
        } else {
          itemBinding.iconSyncStatus.setImageResource(R.drawable.ic_cloud_done)
          itemBinding.iconSyncStatus.imageTintList =
            android.content.res.ColorStateList.valueOf(getColor(R.color.status_current_text))
        }

        itemBinding.textStatus.text = statusLabel(member.status)
        when (member.status) {
          "active" -> {
            itemBinding.textStatus.setTextColor(getColor(R.color.status_current_text))
            itemBinding.textStatus.setBackgroundColor(getColor(R.color.status_current_container))
          }
          "frozen" -> {
            itemBinding.textStatus.setTextColor(getColor(R.color.status_upcoming_text))
            itemBinding.textStatus.setBackgroundColor(getColor(R.color.status_upcoming_container))
          }
          else -> {
            itemBinding.textStatus.setTextColor(getColor(R.color.status_expired_text))
            itemBinding.textStatus.setBackgroundColor(getColor(R.color.status_expired_container))
          }
        }

        val customTariff = state.memberTariffs[member.id]
        if (customTariff != null) {
          val parts = buildList {
            customTariff.AmountMonthCordoba?.takeIf { it > 0 }?.let {
              add(CurrencyFormatter.formatCurrency(it, CurrencyFormatter.CURRENCY_NIO))
            }
            customTariff.AmountMonthDollar?.takeIf { it > 0 }?.let {
              add(CurrencyFormatter.formatCurrency(it, CurrencyFormatter.CURRENCY_USD))
            }
          }
          val amountsSuffix = if (parts.isNotEmpty()) " (${parts.joinToString(" / ")})" else ""
          itemBinding.textTariffBadge.text = "Tarifa personalizada$amountsSuffix"
          itemBinding.textTariffBadge.setTextColor(getColor(R.color.partial_badge_text))
          itemBinding.cardTariffBadge.setCardBackgroundColor(getColor(R.color.partial_badge_container))
        } else {
          itemBinding.textTariffBadge.text = "Tarifa general"
          itemBinding.textTariffBadge.setTextColor(getColor(R.color.text_secondary))
          itemBinding.cardTariffBadge.setCardBackgroundColor(getColor(R.color.latest_payment_box))
        }

        itemBinding.buttonTariff.setOnClickListener {
          MemberDialogs.showMemberTariffDialog(
            context = this@MembersActivity,
            memberName = member.fullName,
            currentTariff = customTariff,
            globalSetting = state.paymentSetting,
            onSaveCustom = { value -> viewModel.saveMemberTariff(member.id, value) },
            onResetToGlobal = { viewModel.deleteMemberTariff(member.id) },
          )
        }

        itemBinding.buttonEditMember.setOnClickListener {
          MemberDialogs.showEditMemberDialog(
            context = this@MembersActivity,
            fullName = member.fullName,
            documentNumber = member.documentNumber,
            status = member.status,
            phone = member.phone,
            email = member.email,
            notes = member.notes,
            onSave = { request -> viewModel.updateMember(member.id, request) },
          )
        }

        itemBinding.buttonDeleteMember.setOnClickListener {
          MemberDialogs.showDeleteMemberDialog(
            context = this@MembersActivity,
            memberName = member.fullName,
            onConfirmDelete = { viewModel.deleteMember(member.id) },
          )
        }

        membersContainer.addView(itemBinding.root)
      }
    }
    state.message?.let { message ->
      Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
      viewModel.messageShown()
    }
  }

  private fun statusLabel(status: String): String = when (status) {
    "active" -> getString(R.string.status_active)
    "frozen" -> getString(R.string.status_frozen)
    else -> getString(R.string.status_inactive)
  }

  private fun memberText(value: String) = TextView(this).apply {
    text = value
    textSize = 16f
    setTextColor(getColor(R.color.text_primary))
    setBackgroundResource(R.drawable.payment_item_background)
    setPadding(40, 28, 40, 28)
    val margin = resources.getDimensionPixelSize(R.dimen.payment_item_margin)
    layoutParams = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { setMargins(0, 0, 0, margin) }
  }
}

package com.example.paymentacademy

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.paymentacademy.data.remote.SupabaseProvider
import com.example.paymentacademy.data.repository.SupabaseMemberRepository
import com.example.paymentacademy.databinding.ActivityRegisterMemberBinding
import com.example.paymentacademy.ui.RegisterMemberUiState
import com.example.paymentacademy.ui.RegisterMemberViewModel
import com.example.paymentacademy.ui.RegisterMemberViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class RegisterMemberActivity : AppCompatActivity() {
  private lateinit var binding: ActivityRegisterMemberBinding
  private val viewModel: RegisterMemberViewModel by viewModels {
    RegisterMemberViewModelFactory(SupabaseMemberRepository(SupabaseProvider.client, applicationContext))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    binding = ActivityRegisterMemberBinding.inflate(layoutInflater)
    setContentView(binding.root)
    ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
      insets
    }

    binding.cancelButton.setOnClickListener { finish() }
    binding.saveMemberButton.setOnClickListener {
      viewModel.save(
        fullName = binding.fullNameInput.text?.toString().orEmpty(),
        documentNumber = binding.documentInput.text?.toString().orEmpty(),
        email = binding.memberEmailInput.text?.toString().orEmpty(),
        phone = binding.phoneInput.text?.toString().orEmpty(),
        notes = binding.notesInput.text?.toString().orEmpty(),
      )
    }

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect(::render)
      }
    }
  }

  private fun render(state: RegisterMemberUiState) = with(binding) {
    progressIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE
    saveMemberButton.isEnabled = !state.isLoading
    cancelButton.isEnabled = !state.isLoading
    fullNameLayout.error = state.fullNameError
    memberEmailLayout.error = state.emailError

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
}

package com.example.paymentacademy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.paymentacademy.data.model.Member
import com.example.paymentacademy.databinding.ItemSelectableMemberBinding

class MemberSelectionAdapter(
  private val onSelected: (Member) -> Unit,
) : ListAdapter<Member, MemberSelectionAdapter.MemberViewHolder>(DiffCallback) {
  var selectedMemberId: String? = null
    set(value) {
      val oldPosition = currentList.indexOfFirst { it.id == field }
      field = value
      val newPosition = currentList.indexOfFirst { it.id == value }
      if (oldPosition >= 0) notifyItemChanged(oldPosition)
      if (newPosition >= 0) notifyItemChanged(newPosition)
    }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder =
    MemberViewHolder(
      ItemSelectableMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

  override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
    holder.bind(getItem(position), getItem(position).id == selectedMemberId)
  }

  inner class MemberViewHolder(
    private val binding: ItemSelectableMemberBinding,
  ) : RecyclerView.ViewHolder(binding.root) {
    fun bind(member: Member, selected: Boolean) = with(binding) {
      memberName.text = member.fullName
      memberDetail.text = listOfNotNull(member.documentNumber, member.phone, member.email)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { root.context.getString(com.example.paymentacademy.R.string.no_contact) }
      selectedIcon.visibility = if (selected) android.view.View.VISIBLE else android.view.View.GONE
      memberCard.strokeWidth = if (selected) 3 else 1
      memberCard.setOnClickListener {
        selectedMemberId = member.id
        onSelected(member)
      }
    }
  }

  private object DiffCallback : DiffUtil.ItemCallback<Member>() {
    override fun areItemsTheSame(oldItem: Member, newItem: Member) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Member, newItem: Member) = oldItem == newItem
  }
}

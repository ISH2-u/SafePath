package com.safepath.ai.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.safepath.ai.R;
import com.safepath.ai.network.dto.ContactResponse;

/**
 * EmergencyContactsAdapter — RecyclerView adapter for the emergency contacts list.
 *
 * STRICT POLICY: This adapter only displays contacts explicitly added by the user.
 * No police, authorities, or strangers appear here.
 */
public class EmergencyContactsAdapter
        extends ListAdapter<ContactResponse, EmergencyContactsAdapter.ContactViewHolder> {

    /** Callbacks fired from the ViewHolder button clicks. */
    public interface ContactActionListener {
        void onEdit(ContactResponse contact);
        void onDelete(ContactResponse contact);
        void onVerify(ContactResponse contact);
        void onToggleSos(ContactResponse contact, boolean enabled);
    }

    private final ContactActionListener listener;

    public EmergencyContactsAdapter(@NonNull ContactActionListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    // ── DiffUtil ──────────────────────────────────────────────────────────────

    private static final DiffUtil.ItemCallback<ContactResponse> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ContactResponse>() {
                @Override
                public boolean areItemsTheSame(@NonNull ContactResponse a,
                                               @NonNull ContactResponse b) {
                    if (a.id != null && b.id != null) return a.id.equals(b.id);
                    return a.phone != null && a.phone.equals(b.phone);
                }

                @Override
                public boolean areContentsTheSame(@NonNull ContactResponse a,
                                                  @NonNull ContactResponse b) {
                    boolean sameName     = safeEq(a.name, b.name);
                    boolean samePhone    = safeEq(a.phone, b.phone);
                    boolean sameVerified = a.isVerified == b.isVerified;
                    boolean sameSos      = a.receiveSosAlerts == b.receiveSosAlerts;
                    return sameName && samePhone && sameVerified && sameSos;
                }

                private boolean safeEq(String x, String y) {
                    if (x == null && y == null) return true;
                    if (x == null || y == null) return false;
                    return x.equals(y);
                }
            };

    // ── Adapter overrides ─────────────────────────────────────────────────────

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_emergency_contact, parent, false);
        return new ContactViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class ContactViewHolder extends RecyclerView.ViewHolder {

        private final TextView      tvAvatar;
        private final TextView      tvName;
        private final TextView      tvRelationship;
        private final TextView      tvPhone;
        private final TextView      tvVerifiedBadge;
        private final SwitchCompat  switchSos;
        private final MaterialButton btnVerify;
        private final MaterialButton btnEdit;
        private final MaterialButton btnDelete;

        ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar        = itemView.findViewById(R.id.tvContactAvatar);
            tvName          = itemView.findViewById(R.id.tvContactName);
            tvRelationship  = itemView.findViewById(R.id.tvContactRelationship);
            tvPhone         = itemView.findViewById(R.id.tvContactPhone);
            tvVerifiedBadge = itemView.findViewById(R.id.tvVerifiedBadge);
            switchSos       = itemView.findViewById(R.id.switchSosAlerts);
            btnVerify       = itemView.findViewById(R.id.btnVerifyContact);
            btnEdit         = itemView.findViewById(R.id.btnEditContact);
            btnDelete       = itemView.findViewById(R.id.btnDeleteContact);
        }

        void bind(@NonNull ContactResponse contact,
                  @NonNull ContactActionListener listener) {

            // Avatar initial
            if (contact.name != null && !contact.name.isEmpty()) {
                tvAvatar.setText(String.valueOf(contact.name.charAt(0)).toUpperCase());
            } else {
                tvAvatar.setText("?");
            }

            // Basic fields
            tvName.setText(contact.name != null ? contact.name : "Unknown");
            tvRelationship.setText(
                    contact.relationshipType != null ? contact.relationshipType : "Contact");
            tvPhone.setText(contact.phone != null ? contact.phone : "");

            // Verified badge
            if (contact.isVerified) {
                tvVerifiedBadge.setText("VERIFIED");
                tvVerifiedBadge.setBackgroundResource(R.drawable.bg_badge_verified);
                btnVerify.setVisibility(View.GONE);

                // SOS switch — only meaningful for verified contacts
                switchSos.setEnabled(true);
                switchSos.setAlpha(1.0f);
            } else {
                tvVerifiedBadge.setText("PENDING");
                tvVerifiedBadge.setBackgroundResource(R.drawable.bg_badge_pending);
                btnVerify.setVisibility(View.VISIBLE);

                // SOS switch disabled for unverified contacts
                switchSos.setEnabled(false);
                switchSos.setAlpha(0.4f);
            }

            // Prevent listener firing during rebind
            switchSos.setOnCheckedChangeListener(null);
            switchSos.setChecked(contact.receiveSosAlerts);
            switchSos.setOnCheckedChangeListener((btn, isChecked) -> {
                if (contact.isVerified) {
                    listener.onToggleSos(contact, isChecked);
                } else {
                    // Revert — unverified contacts cannot receive SOS
                    btn.setChecked(false);
                }
            });

            // Button callbacks
            btnVerify.setOnClickListener(v -> listener.onVerify(contact));
            btnEdit.setOnClickListener(v -> listener.onEdit(contact));
            btnDelete.setOnClickListener(v -> listener.onDelete(contact));
        }
    }
}


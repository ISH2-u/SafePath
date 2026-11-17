package com.safepath.ai.fragments;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.safepath.ai.R;
import com.safepath.ai.adapters.EmergencyContactsAdapter;
import com.safepath.ai.network.dto.ContactRequest;
import com.safepath.ai.network.dto.ContactResponse;
import com.safepath.ai.network.dto.VerificationResponse;
import com.safepath.ai.utils.PhoneUtils;
import com.safepath.ai.viewmodels.ContactsViewModel;
import java.util.Locale;

/**
 * EmergencyContactsFragment — Full CRUD management for user emergency contacts.
 *
 * STRICT POLICY (enforced here and on the backend):
 *   - Only contacts explicitly added by the user appear here.
 *   - Only VERIFIED contacts with receive_sos_alerts=true will ever receive an alert.
 *   - Police / authorities / strangers are NEVER added automatically.
 *   - Unverified contacts cannot have SOS alerts enabled.
 */
public class EmergencyContactsFragment extends Fragment
        implements EmergencyContactsAdapter.ContactActionListener {

    // ── Relationship type options ──────────────────────────────────────────────
    private static final String[] RELATIONSHIP_OPTIONS = {
            "Family", "Spouse / Partner", "Friend", "Colleague",
            "Neighbour", "Doctor", "Other"
    };

    // ── UI ────────────────────────────────────────────────────────────────────
    private RecyclerView             recyclerContacts;
    private View                     layoutEmpty;
    private View                     progressContacts;
    private FloatingActionButton     fabAddContact;
    private EmergencyContactsAdapter adapter;

    // ── ViewModel ─────────────────────────────────────────────────────────────
    private ContactsViewModel viewModel;

    // ── Active selected contact (for edit / verify / delete) ─────────────────
    private ContactResponse selectedContact;

    // ── OTP resend timer ─────────────────────────────────────────────────────
    private CountDownTimer otpTimer;

    // ─────────────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_contacts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Wire UI
        recyclerContacts = view.findViewById(R.id.recyclerContacts);
        layoutEmpty      = view.findViewById(R.id.layoutEmptyContacts);
        progressContacts = view.findViewById(R.id.progressContacts);
        fabAddContact    = view.findViewById(R.id.fabAddContact);

        // RecyclerView
        adapter = new EmergencyContactsAdapter(this);
        recyclerContacts.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerContacts.setAdapter(adapter);

        // ViewModel
        viewModel = new ViewModelProvider(this).get(ContactsViewModel.class);

        // Observe contacts list
        viewModel.getContacts().observe(getViewLifecycleOwner(), contacts -> {
            if (contacts == null || contacts.isEmpty()) {
                layoutEmpty.setVisibility(View.VISIBLE);
                recyclerContacts.setVisibility(View.GONE);
            } else {
                layoutEmpty.setVisibility(View.GONE);
                recyclerContacts.setVisibility(View.VISIBLE);
                adapter.submitList(contacts);
            }
        });

        // Observe loading
        viewModel.isLoading().observe(getViewLifecycleOwner(), loading -> {
            if (progressContacts != null) {
                progressContacts.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE);
            }
        });

        // Observe errors
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });

        // Observe operation success
        viewModel.getOperationSuccess().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });

        // FAB — add new contact
        if (fabAddContact != null) {
            fabAddContact.setOnClickListener(v -> showAddContactDialog());
        }

        // Initial load
        viewModel.loadContacts();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.loadContacts();
    }

    // ── ContactActionListener callbacks ───────────────────────────────────────

    @Override
    public void onEdit(@NonNull ContactResponse contact) {
        selectedContact = contact;
        showEditContactDialog(contact);
    }

    @Override
    public void onDelete(@NonNull ContactResponse contact) {
        selectedContact = contact;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Remove Contact")
                .setMessage("Remove " + contact.name + " from your emergency contacts?\n\n"
                        + "They will no longer receive your emergency alerts.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, w) -> {
                    if (contact.id != null) {
                        viewModel.deleteContact(contact.id);
                    }
                })
                .show();
    }

    @Override
    public void onVerify(@NonNull ContactResponse contact) {
        selectedContact = contact;
        // First send the verification code, then show OTP dialog
        if (contact.id != null) {
            viewModel.sendVerificationCode(contact.id);
            showVerifyContactDialog(contact);
        }
    }

    @Override
    public void onToggleSos(@NonNull ContactResponse contact, boolean enabled) {
        if (!contact.isVerified) {
            Toast.makeText(requireContext(),
                    "Contact must be verified before receiving SOS alerts.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (contact.id != null) {
            viewModel.toggleSosAlerts(contact.id, enabled);
        }
    }

    // ── Add Contact dialog ────────────────────────────────────────────────────

    private void showAddContactDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_contact, null);

        TextInputLayout   tilName    = dialogView.findViewById(R.id.tilContactName);
        TextInputLayout   tilPhone   = dialogView.findViewById(R.id.tilContactPhone);
        TextInputLayout   tilEmail   = dialogView.findViewById(R.id.tilContactEmail);
        TextInputLayout   tilRel     = dialogView.findViewById(R.id.tilRelationship);
        TextInputEditText etName     = dialogView.findViewById(R.id.etContactName);
        TextInputEditText etPhone    = dialogView.findViewById(R.id.etContactPhone);
        TextInputEditText etEmail    = dialogView.findViewById(R.id.etContactEmail);
        AutoCompleteTextView actvRel = dialogView.findViewById(R.id.actvRelationship);
        SwitchCompat switchSos       = dialogView.findViewById(R.id.switchAddSosAlerts);
        MaterialButton btnCancel     = dialogView.findViewById(R.id.btnCancelAddContact);
        MaterialButton btnSave       = dialogView.findViewById(R.id.btnSaveContact);

        // Populate relationship dropdown
        ArrayAdapter<String> relAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, RELATIONSHIP_OPTIONS);
        if (actvRel != null) {
            actvRel.setAdapter(relAdapter);
            actvRel.setText(RELATIONSHIP_OPTIONS[0], false);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                String name     = etName  != null && etName.getText()  != null
                        ? etName.getText().toString().trim()  : "";
                String rawPhone = etPhone != null && etPhone.getText() != null
                        ? etPhone.getText().toString().trim() : "";
                String email    = etEmail != null && etEmail.getText() != null
                        ? etEmail.getText().toString().trim() : "";
                String rel      = actvRel != null && actvRel.getText() != null
                        ? actvRel.getText().toString().trim() : RELATIONSHIP_OPTIONS[0];

                String normPhone = PhoneUtils.normalize(rawPhone);

                boolean valid = true;
                if (TextUtils.isEmpty(name)) {
                    if (tilName != null) tilName.setError("Name is required");
                    valid = false;
                } else {
                    if (tilName != null) tilName.setError(null);
                }

                if (TextUtils.isEmpty(rawPhone)) {
                    if (tilPhone != null) tilPhone.setError("Phone number is required");
                    valid = false;
                } else if (!PhoneUtils.isValid(normPhone)) {
                    if (tilPhone != null) tilPhone.setError("Enter a valid phone number (7-15 digits)");
                    valid = false;
                } else {
                    if (tilPhone != null) tilPhone.setError(null);
                }

                if (!valid) return;

                ContactRequest req = new ContactRequest();
                req.name             = name;
                req.phone            = normPhone;
                req.email            = email.isEmpty() ? null : email;
                req.relationshipType = rel;
                req.receiveSosAlerts = switchSos != null && switchSos.isChecked();

                viewModel.addContact(req);
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    // ── Edit Contact dialog ───────────────────────────────────────────────────

    private void showEditContactDialog(@NonNull ContactResponse contact) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_contact, null);

        TextInputLayout   tilName      = dialogView.findViewById(R.id.tilEditContactName);
        TextInputLayout   tilPhone     = dialogView.findViewById(R.id.tilEditContactPhone);
        TextInputLayout   tilRel       = dialogView.findViewById(R.id.tilEditRelationship);
        TextInputEditText etName       = dialogView.findViewById(R.id.etEditContactName);
        TextInputEditText etPhone      = dialogView.findViewById(R.id.etEditContactPhone);
        TextInputEditText etEmail      = dialogView.findViewById(R.id.etEditContactEmail);
        AutoCompleteTextView actvRel   = dialogView.findViewById(R.id.actvEditRelationship);
        SwitchCompat switchSos         = dialogView.findViewById(R.id.switchEditSosAlerts);
        TextView       tvEditSosHelp   = dialogView.findViewById(R.id.tvEditSosHelp);
        MaterialButton btnCancel       = dialogView.findViewById(R.id.btnCancelEditContact);
        MaterialButton btnUpdate       = dialogView.findViewById(R.id.btnUpdateContact);

        // Pre-populate
        if (etName  != null) etName.setText(contact.name);
        if (etPhone != null) etPhone.setText(contact.phone);
        if (etEmail != null) etEmail.setText(contact.email != null ? contact.email : "");

        if (switchSos != null) {
            if (contact.isVerified) {
                switchSos.setEnabled(true);
                switchSos.setChecked(contact.receiveSosAlerts);
                if (tvEditSosHelp != null) {
                    tvEditSosHelp.setText("Dispatches emergency alerts to this contact.");
                }
            } else {
                switchSos.setEnabled(false);
                switchSos.setChecked(false);
                if (tvEditSosHelp != null) {
                    tvEditSosHelp.setText("Verification required before SOS alerts can be enabled.");
                }
            }
        }

        ArrayAdapter<String> relAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, RELATIONSHIP_OPTIONS);
        if (actvRel != null) {
            actvRel.setAdapter(relAdapter);
            String rel = contact.relationshipType != null
                    ? contact.relationshipType : RELATIONSHIP_OPTIONS[0];
            actvRel.setText(rel, false);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());

        if (btnUpdate != null) {
            btnUpdate.setOnClickListener(v -> {
                String name     = etName  != null && etName.getText()  != null
                        ? etName.getText().toString().trim()  : "";
                String rawPhone = etPhone != null && etPhone.getText() != null
                        ? etPhone.getText().toString().trim() : "";
                String email    = etEmail != null && etEmail.getText() != null
                        ? etEmail.getText().toString().trim() : "";
                String rel      = actvRel != null && actvRel.getText() != null
                        ? actvRel.getText().toString().trim() : RELATIONSHIP_OPTIONS[0];

                String normPhone = PhoneUtils.normalize(rawPhone);

                boolean valid = true;
                if (TextUtils.isEmpty(name)) {
                    if (tilName != null) tilName.setError("Name is required");
                    valid = false;
                } else {
                    if (tilName != null) tilName.setError(null);
                }
                if (TextUtils.isEmpty(rawPhone)) {
                    if (tilPhone != null) tilPhone.setError("Phone is required");
                    valid = false;
                } else if (!PhoneUtils.isValid(normPhone)) {
                    if (tilPhone != null) tilPhone.setError("Enter a valid phone number (7-15 digits)");
                    valid = false;
                } else {
                    if (tilPhone != null) tilPhone.setError(null);
                }
                if (!valid) return;

                ContactRequest req = new ContactRequest();
                req.name             = name;
                req.phone            = normPhone;
                req.email            = email.isEmpty() ? null : email;
                req.relationshipType = rel;
                // Strict safety invariant: never send receiveSosAlerts=true if unverified
                req.receiveSosAlerts = contact.isVerified && switchSos != null && switchSos.isChecked();

                if (contact.id != null) {
                    viewModel.editContact(contact.id, req);
                }
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    // ── Verify Contact dialog ─────────────────────────────────────────────────

    private void showVerifyContactDialog(@NonNull ContactResponse contact) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_verify_contact, null);

        TextInputEditText etOtp        = dialogView.findViewById(R.id.etOtpCode);
        TextInputLayout   tilOtp       = dialogView.findViewById(R.id.tilOtpCode);
        TextView          tvResend     = dialogView.findViewById(R.id.tvResendCode);
        TextView          tvTimer      = dialogView.findViewById(R.id.tvResendTimer);
        View              cardDemoOtp  = dialogView.findViewById(R.id.cardDemoOtpHint);
        TextView          tvDemoCode   = dialogView.findViewById(R.id.tvDemoOtpCode);
        MaterialButton    btnAutofill  = dialogView.findViewById(R.id.btnAutofillOtp);
        MaterialButton    btnCancel    = dialogView.findViewById(R.id.btnCancelVerify);
        MaterialButton    btnSubmit    = dialogView.findViewById(R.id.btnSubmitOtp);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        // Observe verification dispatch to populate demo OTP banner
        Observer<VerificationResponse> otpObserver = res -> {
            if (res != null && contact.id != null && contact.id.equals(res.contactId)) {
                if (res.devVerificationCode != null && !res.devVerificationCode.isEmpty()) {
                    if (cardDemoOtp != null) cardDemoOtp.setVisibility(View.VISIBLE);
                    if (tvDemoCode != null) tvDemoCode.setText("Code: " + res.devVerificationCode);
                    if (btnAutofill != null) {
                        btnAutofill.setOnClickListener(v -> {
                            if (etOtp != null) {
                                etOtp.setText(res.devVerificationCode);
                                etOtp.setSelection(res.devVerificationCode.length());
                            }
                        });
                    }
                }
            }
        };
        viewModel.getVerificationDispatch().observe(getViewLifecycleOwner(), otpObserver);

        dialog.setOnDismissListener(d -> {
            cancelOtpCountdown();
            viewModel.getVerificationDispatch().removeObserver(otpObserver);
        });

        // Start 60-second resend countdown
        startOtpCountdown(tvResend, tvTimer);

        if (tvResend != null) {
            tvResend.setOnClickListener(v -> {
                if (contact.id != null) {
                    viewModel.sendVerificationCode(contact.id);
                    startOtpCountdown(tvResend, tvTimer);
                    Toast.makeText(requireContext(),
                            "Verification code re-sent.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                cancelOtpCountdown();
                dialog.dismiss();
            });
        }

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                String code = etOtp != null && etOtp.getText() != null
                        ? etOtp.getText().toString().trim() : "";
                if (code.length() != 6) {
                    if (tilOtp != null) tilOtp.setError("Enter the 6-digit code");
                    return;
                }
                if (tilOtp != null) tilOtp.setError(null);
                if (contact.id != null) {
                    viewModel.verifyContact(contact.id, code);
                }
                cancelOtpCountdown();
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    // ── OTP countdown helpers ─────────────────────────────────────────────────

    private void startOtpCountdown(TextView tvResend, TextView tvTimer) {
        cancelOtpCountdown();
        if (tvResend != null) tvResend.setEnabled(false);
        if (tvTimer  != null) tvTimer.setVisibility(View.VISIBLE);

        otpTimer = new CountDownTimer(60_000, 1_000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (tvTimer != null) {
                    tvTimer.setText(String.format(Locale.getDefault(),
                            "Resend available in %ds",
                            millisUntilFinished / 1000));
                }
            }

            @Override
            public void onFinish() {
                if (tvResend != null) tvResend.setEnabled(true);
                if (tvTimer  != null) tvTimer.setVisibility(View.GONE);
            }
        }.start();
    }

    private void cancelOtpCountdown() {
        if (otpTimer != null) {
            otpTimer.cancel();
            otpTimer = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelOtpCountdown();
    }
}

package com.safepath.ai.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.safepath.ai.network.dto.ContactRequest;
import com.safepath.ai.network.dto.ContactResponse;
import com.safepath.ai.network.dto.VerificationResponse;
import com.safepath.ai.repositories.ContactsRepository;
import java.util.ArrayList;
import java.util.List;

/**
 * ContactsViewModel — Manages the emergency contacts list.
 * Method signatures aligned with EmergencyContactsFragment.
 * No userId parameter needed — backend derives user from JWT token.
 */
public class ContactsViewModel extends AndroidViewModel {

    private final ContactsRepository repository;

    private final MutableLiveData<Boolean>           loading              = new MutableLiveData<>(false);
    private final MutableLiveData<String>            errorMessage         = new MutableLiveData<>(null);
    private final MutableLiveData<String>            operationSuccess     = new MutableLiveData<>(null);
    private final MutableLiveData<List<ContactResponse>> contacts         = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<VerificationResponse> verificationDispatch = new MutableLiveData<>(null);

    public ContactsViewModel(@NonNull Application application) {
        super(application);
        this.repository = new ContactsRepository(application.getApplicationContext());
    }

    // ── Accessors ─────────────────────────────────────────────────────────────
    public LiveData<Boolean>              isLoading()               { return loading; }
    public LiveData<Boolean>              getIsLoading()            { return loading; }
    public LiveData<String>               getErrorMessage()         { return errorMessage; }
    public LiveData<String>               getOperationSuccess()     { return operationSuccess; }
    public LiveData<List<ContactResponse>> getContacts()            { return contacts; }
    public LiveData<VerificationResponse> getVerificationDispatch() { return verificationDispatch; }

    // ── Load ──────────────────────────────────────────────────────────────────

    public void loadContacts() {
        loading.setValue(true);
        errorMessage.setValue(null);
        repository.syncContacts(null, new ContactsRepository.ContactsCallback<List<ContactResponse>>() {
            @Override public void onSuccess(List<ContactResponse> result) {
                loading.postValue(false);
                contacts.postValue(result != null ? result : new ArrayList<>());
            }
            @Override public void onError(String error) {
                loading.postValue(false);
                errorMessage.postValue(error);
            }
        });
    }

    // ── Add ───────────────────────────────────────────────────────────────────

    public void addContact(ContactRequest req) {
        loading.setValue(true);
        errorMessage.setValue(null);
        repository.addContact(null, req, new ContactsRepository.ContactsCallback<ContactResponse>() {
            @Override public void onSuccess(ContactResponse result) {
                operationSuccess.postValue(
                        "Contact '" + result.name + "' added. Send them the verification code.");
                loadContacts();
            }
            @Override public void onError(String error) {
                loading.postValue(false);
                errorMessage.postValue(error);
            }
        });
    }

    // ── Edit ──────────────────────────────────────────────────────────────────

    public void editContact(String contactId, ContactRequest req) {
        loading.setValue(true);
        errorMessage.setValue(null);
        repository.editContact(contactId, req, new ContactsRepository.ContactsCallback<ContactResponse>() {
            @Override public void onSuccess(ContactResponse result) {
                operationSuccess.postValue("Contact updated successfully.");
                loadContacts();
            }
            @Override public void onError(String error) {
                loading.postValue(false);
                errorMessage.postValue(error);
            }
        });
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void deleteContact(String contactId) {
        loading.setValue(true);
        errorMessage.setValue(null);
        repository.deleteContact(contactId, new ContactsRepository.ContactsCallback<String>() {
            @Override public void onSuccess(String result) {
                operationSuccess.postValue("Contact removed.");
                loadContacts();
            }
            @Override public void onError(String error) {
                loading.postValue(false);
                errorMessage.postValue(error);
            }
        });
    }

    // ── Send verification code ────────────────────────────────────────────────

    public void sendVerificationCode(String contactId) {
        repository.sendVerificationCode(contactId,
                new ContactsRepository.ContactsCallback<VerificationResponse>() {
            @Override public void onSuccess(VerificationResponse result) {
                verificationDispatch.postValue(result);
                String msg = (result != null && result.message != null)
                        ? result.message
                        : "Verification code sent to contact.";
                operationSuccess.postValue(msg);
            }
            @Override public void onError(String error) {
                errorMessage.postValue("Could not send code: " + error);
            }
        });
    }

    // ── Verify contact ────────────────────────────────────────────────────────

    public void verifyContact(String contactId, String code) {
        loading.setValue(true);
        errorMessage.setValue(null);
        repository.verifyContact(null, contactId, code,
                new ContactsRepository.ContactsCallback<ContactResponse>() {
            @Override public void onSuccess(ContactResponse result) {
                operationSuccess.postValue(
                        result.name + " is now verified and eligible for SOS alerts.");
                loadContacts();
            }
            @Override public void onError(String error) {
                loading.postValue(false);
                errorMessage.postValue(error);
            }
        });
    }

    // ── Toggle SOS alerts ─────────────────────────────────────────────────────

    public void toggleSosAlerts(String contactId, boolean enabled) {
        loading.setValue(true);
        errorMessage.setValue(null);
        repository.toggleSosDelivery(null, contactId, enabled,
                new ContactsRepository.ContactsCallback<ContactResponse>() {
            @Override public void onSuccess(ContactResponse result) {
                operationSuccess.postValue(
                        "SOS alerts " + (enabled ? "enabled" : "disabled") + " for " + result.name);
                loadContacts();
            }
            @Override public void onError(String error) {
                loading.postValue(false);
                errorMessage.postValue(error);
            }
        });
    }
}

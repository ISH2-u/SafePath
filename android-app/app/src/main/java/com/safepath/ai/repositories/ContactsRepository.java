package com.safepath.ai.repositories;

import android.content.Context;
import androidx.lifecycle.LiveData;
import com.safepath.ai.database.SafePathDatabase;
import com.safepath.ai.database.dao.EmergencyContactDao;
import com.safepath.ai.models.EmergencyContact;
import com.safepath.ai.network.ContactsApiService;
import com.safepath.ai.network.RetrofitClient;
import com.safepath.ai.network.dto.ContactRequest;
import com.safepath.ai.network.dto.ContactResponse;
import com.safepath.ai.network.dto.ToggleSosRequest;
import com.safepath.ai.network.dto.VerificationResponse;
import com.safepath.ai.network.dto.VerifyContactRequest;
import com.safepath.ai.utils.NetworkErrorParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ContactsRepository {

    private final ContactsApiService apiService;
    private final EmergencyContactDao contactDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface ContactsCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    public ContactsRepository(Context context) {
        this.apiService = RetrofitClient.getInstance().create(ContactsApiService.class);
        this.contactDao = SafePathDatabase.getInstance(context).emergencyContactDao();
    }

    public LiveData<List<EmergencyContact>> getLocalContacts(String userId) {
        return contactDao.getForUser(userId);
    }

    public void syncContacts(String userId, ContactsCallback<List<ContactResponse>> callback) {
        apiService.listContacts().enqueue(new Callback<List<ContactResponse>>() {
            @Override
            public void onResponse(Call<List<ContactResponse>> call, Response<List<ContactResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ContactResponse> remoteList = response.body();
                    updateLocalCache(userId, remoteList);
                    callback.onSuccess(remoteList);
                } else {
                    callback.onError(NetworkErrorParser.parseHttpError(response, "Failed to load emergency contacts"));
                }
            }

            @Override
            public void onFailure(Call<List<ContactResponse>> call, Throwable t) {
                callback.onError(NetworkErrorParser.parseFailure(t));
            }
        });
    }

    public void addContact(String userId, ContactRequest request, ContactsCallback<ContactResponse> callback) {
        apiService.addContact(request).enqueue(new Callback<ContactResponse>() {
            @Override
            public void onResponse(Call<ContactResponse> call, Response<ContactResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    saveSingleContactLocally(userId, response.body());
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Failed to add contact: " + parseError(response));
                }
            }

            @Override
            public void onFailure(Call<ContactResponse> call, Throwable t) {
                callback.onError("Network error adding contact: " + t.getMessage());
            }
        });
    }

    /** Convenience overload — no userId needed when backend derives it from JWT. */
    public void editContact(String contactId, ContactRequest request, ContactsCallback<ContactResponse> callback) {
        editContact(null, contactId, request, callback);
    }

    public void editContact(String userId, String contactId, ContactRequest request, ContactsCallback<ContactResponse> callback) {
        apiService.updateContact(contactId, request).enqueue(new Callback<ContactResponse>() {
            @Override
            public void onResponse(Call<ContactResponse> call, Response<ContactResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    saveSingleContactLocally(userId, response.body());
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Failed to update contact: " + parseError(response));
                }
            }

            @Override
            public void onFailure(Call<ContactResponse> call, Throwable t) {
                callback.onError("Network error updating contact: " + t.getMessage());
            }
        });
    }

    public void deleteContact(String contactId, ContactsCallback<String> callback) {
        apiService.deleteContact(contactId).enqueue(new Callback<Map<String, String>>() {
            @Override
            public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                if (response.isSuccessful()) {
                    executor.execute(() -> contactDao.deleteByRemoteId(contactId));
                    callback.onSuccess("Contact successfully deleted.");
                } else {
                    callback.onError("Failed to delete contact: " + parseError(response));
                }
            }

            @Override
            public void onFailure(Call<Map<String, String>> call, Throwable t) {
                callback.onError("Network error deleting contact: " + t.getMessage());
            }
        });
    }

    public void sendVerificationCode(String contactId, ContactsCallback<VerificationResponse> callback) {
        apiService.sendVerification(contactId).enqueue(new Callback<VerificationResponse>() {
            @Override
            public void onResponse(Call<VerificationResponse> call, Response<VerificationResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Failed to send verification code: " + parseError(response));
                }
            }

            @Override
            public void onFailure(Call<VerificationResponse> call, Throwable t) {
                callback.onError("Network error sending verification: " + t.getMessage());
            }
        });
    }

    public void verifyContact(String userId, String contactId, String code, ContactsCallback<ContactResponse> callback) {
        VerifyContactRequest req = new VerifyContactRequest(code);
        apiService.verifyContact(contactId, req).enqueue(new Callback<ContactResponse>() {
            @Override
            public void onResponse(Call<ContactResponse> call, Response<ContactResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    saveSingleContactLocally(userId, response.body());
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Verification failed: " + parseError(response));
                }
            }

            @Override
            public void onFailure(Call<ContactResponse> call, Throwable t) {
                callback.onError("Network error during verification: " + t.getMessage());
            }
        });
    }

    public void toggleSosDelivery(String userId, String contactId, boolean receiveSos, ContactsCallback<ContactResponse> callback) {
        ToggleSosRequest req = new ToggleSosRequest(receiveSos);
        apiService.toggleSosDelivery(contactId, req).enqueue(new Callback<ContactResponse>() {
            @Override
            public void onResponse(Call<ContactResponse> call, Response<ContactResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    saveSingleContactLocally(userId, response.body());
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Could not toggle SOS delivery: " + parseError(response));
                }
            }

            @Override
            public void onFailure(Call<ContactResponse> call, Throwable t) {
                callback.onError("Network error toggling SOS delivery: " + t.getMessage());
            }
        });
    }

    private void updateLocalCache(String userId, List<ContactResponse> remoteList) {
        executor.execute(() -> {
            String effectiveUserId = (userId != null && !userId.isEmpty())
                    ? userId
                    : (remoteList != null && !remoteList.isEmpty() ? remoteList.get(0).userId : null);

            if (effectiveUserId != null) {
                contactDao.deleteAllForUser(effectiveUserId);
            }
            if (remoteList == null || remoteList.isEmpty()) {
                return;
            }
            List<EmergencyContact> localList = new ArrayList<>();
            for (ContactResponse r : remoteList) {
                EmergencyContact ec = new EmergencyContact();
                ec.remoteId = r.id;
                ec.userId = (r.userId != null && !r.userId.isEmpty()) ? r.userId : effectiveUserId;
                ec.name = r.name;
                ec.phone = r.phone;
                ec.email = r.email;
                ec.relationshipType = r.relationshipType;
                ec.isVerified = r.isVerified;
                ec.receiveSosAlerts = r.receiveSosAlerts;
                ec.addedAt = System.currentTimeMillis();
                localList.add(ec);
            }
            contactDao.insertAll(localList);
        });
    }

    private void saveSingleContactLocally(String userId, ContactResponse r) {
        if (r == null) return;
        executor.execute(() -> {
            EmergencyContact ec = new EmergencyContact();
            ec.remoteId = r.id;
            ec.userId = (userId != null && !userId.isEmpty()) ? userId : r.userId;
            ec.name = r.name;
            ec.phone = r.phone;
            ec.email = r.email;
            ec.relationshipType = r.relationshipType;
            ec.isVerified = r.isVerified;
            ec.receiveSosAlerts = r.receiveSosAlerts;
            ec.addedAt = System.currentTimeMillis();
            contactDao.insert(ec);
        });
    }

    private String parseError(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (Exception ignored) {}
        return "HTTP " + response.code();
    }
}

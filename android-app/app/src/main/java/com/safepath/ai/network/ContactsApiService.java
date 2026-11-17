package com.safepath.ai.network;

import com.safepath.ai.network.dto.ContactRequest;
import com.safepath.ai.network.dto.ContactResponse;
import com.safepath.ai.network.dto.ToggleSosRequest;
import com.safepath.ai.network.dto.VerificationResponse;
import com.safepath.ai.network.dto.VerifyContactRequest;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface ContactsApiService {

    @GET("contacts")
    Call<List<ContactResponse>> listContacts();

    @POST("contacts")
    Call<ContactResponse> addContact(@Body ContactRequest request);

    @PUT("contacts/{id}")
    Call<ContactResponse> updateContact(@Path("id") String contactId, @Body ContactRequest request);

    @DELETE("contacts/{id}")
    Call<Map<String, String>> deleteContact(@Path("id") String contactId);

    @POST("contacts/{id}/send-verification")
    Call<VerificationResponse> sendVerification(@Path("id") String contactId);

    @POST("contacts/{id}/verify")
    Call<ContactResponse> verifyContact(@Path("id") String contactId, @Body VerifyContactRequest request);

    @PUT("contacts/{id}/toggle-sos")
    Call<ContactResponse> toggleSosDelivery(@Path("id") String contactId, @Body ToggleSosRequest request);

    @GET("contacts/authorized-recipients")
    Call<Map<String, Object>> getAuthorizedSosRecipients();
}

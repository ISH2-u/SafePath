package com.safepath.ai.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.safepath.ai.network.dto.AssistantDto;
import com.safepath.ai.repositories.AssistantRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AssistantViewModel — Exposes state for the Safety Assistant chat conversation.
 */
public class AssistantViewModel extends ViewModel {

    public static class MessageItem {
        public final String text;
        public final boolean isUser;
        public final List<AssistantDto.ChatSourceCitation> citations;

        public MessageItem(String text, boolean isUser, List<AssistantDto.ChatSourceCitation> citations) {
            this.text = text;
            this.isUser = isUser;
            this.citations = citations;
        }
    }

    private final AssistantRepository repository;
    private final String sessionId = UUID.randomUUID().toString();

    private final MutableLiveData<List<MessageItem>> messagesLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoadingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<List<String>> suggestedPromptsLiveData = new MutableLiveData<>(new ArrayList<>());

    public AssistantViewModel() {
        this.repository = new AssistantRepository();
        // Initial greeting
        List<MessageItem> initial = new ArrayList<>();
        initial.add(new MessageItem(
                "Hello! I am your SafePath AI Safety Assistant. I can help answer questions about using the app, how area-risk is calculated, travel safety tips, and configuring verified emergency contacts.",
                false,
                null
        ));
        messagesLiveData.setValue(initial);

        List<String> defaultPrompts = new ArrayList<>();
        defaultPrompts.add("How is area risk calculated?");
        defaultPrompts.add("What should I do if walking alone at night?");
        defaultPrompts.add("How does the voice codeword work?");
        suggestedPromptsLiveData.setValue(defaultPrompts);
    }

    public LiveData<List<MessageItem>> getMessages() {
        return messagesLiveData;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoadingLiveData;
    }

    public LiveData<Boolean> isLoading() {
        return isLoadingLiveData;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<String> getErrorMessage() {
        return errorLiveData;
    }

    public LiveData<List<String>> getSuggestedPrompts() {
        return suggestedPromptsLiveData;
    }

    public void sendMessage(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) return;

        List<MessageItem> current = new ArrayList<>(messagesLiveData.getValue() != null ? messagesLiveData.getValue() : new ArrayList<>());
        current.add(new MessageItem(userMessage.trim(), true, null));
        messagesLiveData.setValue(current);

        isLoadingLiveData.setValue(true);
        errorLiveData.setValue(null);

        repository.askAssistant(userMessage.trim(), sessionId, new AssistantRepository.AssistantCallback() {
            @Override
            public void onSuccess(AssistantDto.ChatMessageResponse response) {
                isLoadingLiveData.postValue(false);
                List<MessageItem> updated = new ArrayList<>(messagesLiveData.getValue() != null ? messagesLiveData.getValue() : new ArrayList<>());
                updated.add(new MessageItem(response.reply, false, response.citations));
                messagesLiveData.postValue(updated);

                if (response.suggestedPrompts != null && !response.suggestedPrompts.isEmpty()) {
                    suggestedPromptsLiveData.postValue(response.suggestedPrompts);
                }
            }

            @Override
            public void onError(String errorMessage) {
                isLoadingLiveData.postValue(false);
                errorLiveData.postValue(errorMessage);
                List<MessageItem> updated = new ArrayList<>(messagesLiveData.getValue() != null ? messagesLiveData.getValue() : new ArrayList<>());
                updated.add(new MessageItem("I'm having trouble connecting right now: " + errorMessage + ". Please note you can always trigger emergency SOS manually at any time.", false, null));
                messagesLiveData.postValue(updated);
            }
        });
    }
}


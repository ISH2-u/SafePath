package com.safepath.ai.network.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AssistantDto {

    public static class ChatMessageRequest {
        @SerializedName("message")
        public String message;

        @SerializedName("session_id")
        public String sessionId;

        public ChatMessageRequest() {}

        public ChatMessageRequest(String message, String sessionId) {
            this.message = message;
            this.sessionId = sessionId;
        }
    }

    public static class ChatSourceCitation {
        @SerializedName("topic")
        public String topic;

        @SerializedName("title")
        public String title;

        @SerializedName("source_type")
        public String sourceType;
    }

    public static class ChatMessageResponse {
        @SerializedName("reply")
        public String reply;

        @SerializedName("citations")
        public List<ChatSourceCitation> citations;

        @SerializedName("suggested_prompts")
        public List<String> suggestedPrompts;

        @SerializedName("disclaimer")
        public String disclaimer;
    }
}


package com.safepath.ai.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.safepath.ai.R;
import com.safepath.ai.network.dto.AssistantDto;
import com.safepath.ai.viewmodels.AssistantViewModel;
import java.util.List;

/**
 * SafetyAssistantFragment — Interactive AI safety assistant powered by RAG knowledge base.
 *
 * CRITICAL REQUIREMENTS ENFORCED:
 * 1. Backed by FastAPI POST /api/v1/assistant/chat.
 * 2. Uses trusted personal safety knowledge base.
 * 3. Shows source citations to distinguish factual info from general suggestions.
 * 4. Strictly isolated from the emergency SOS trigger/cancel workflow.
 * 5. Cannot modify user contacts or access other users' data.
 */
public class SafetyAssistantFragment extends Fragment {

    private AssistantViewModel viewModel;
    private LinearLayout llChatContainer;
    private LinearLayout llSuggestedChipsContainer;
    private NestedScrollView scrollChat;
    private EditText etAssistantInput;
    private ImageButton btnSendAssistant;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_assistant, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AssistantViewModel.class);

        llChatContainer = view.findViewById(R.id.llChatContainer);
        llSuggestedChipsContainer = view.findViewById(R.id.llSuggestedChipsContainer);
        scrollChat = view.findViewById(R.id.scrollChat);
        etAssistantInput = view.findViewById(R.id.etAssistantInput);
        btnSendAssistant = view.findViewById(R.id.btnSendAssistant);

        if (btnSendAssistant != null) {
            btnSendAssistant.setOnClickListener(v -> submitMessage());
        }

        viewModel.getMessages().observe(getViewLifecycleOwner(), this::renderMessages);
        viewModel.getSuggestedPrompts().observe(getViewLifecycleOwner(), this::renderSuggestedChips);
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (btnSendAssistant != null) {
                btnSendAssistant.setEnabled(!isLoading);
                btnSendAssistant.setAlpha(isLoading ? 0.5f : 1.0f);
            }
        });
    }

    private void submitMessage() {
        if (etAssistantInput == null) return;
        String query = etAssistantInput.getText().toString().trim();
        if (TextUtils.isEmpty(query)) return;

        viewModel.sendMessage(query);
        etAssistantInput.setText("");
    }

    private void renderMessages(List<AssistantViewModel.MessageItem> messages) {
        if (llChatContainer == null || getContext() == null) return;
        llChatContainer.removeAllViews();

        for (AssistantViewModel.MessageItem msg : messages) {
            MaterialCardView card = new MaterialCardView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.bottomMargin = dpToPx(12);

            if (msg.isUser) {
                lp.gravity = Gravity.END;
                lp.leftMargin = dpToPx(48);
                card.setLayoutParams(lp);
                card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_primary));
                card.setRadius(dpToPx(16));
                card.setCardElevation(dpToPx(1));
            } else {
                lp.gravity = Gravity.START;
                lp.rightMargin = dpToPx(48);
                card.setLayoutParams(lp);
                card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_surface));
                card.setRadius(dpToPx(16));
                card.setCardElevation(dpToPx(2));
            }

            LinearLayout contentLayout = new LinearLayout(requireContext());
            contentLayout.setOrientation(LinearLayout.VERTICAL);
            contentLayout.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

            TextView tvMessage = new TextView(requireContext());
            tvMessage.setText(msg.text);
            tvMessage.setTextSize(14f);
            tvMessage.setTextColor(ContextCompat.getColor(requireContext(),
                    msg.isUser ? android.R.color.white : R.color.color_on_surface));
            contentLayout.addView(tvMessage);

            // If assistant message and has citations, display source badge
            if (!msg.isUser && msg.citations != null && !msg.citations.isEmpty()) {
                TextView tvCitations = new TextView(requireContext());
                StringBuilder sb = new StringBuilder("📚 Sources:\n");
                for (AssistantDto.ChatSourceCitation c : msg.citations) {
                    sb.append("• ").append(c.title).append(" (").append(c.sourceType).append(")\n");
                }
                tvCitations.setText(sb.toString().trim());
                tvCitations.setTextSize(11f);
                tvCitations.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_on_surface_variant));
                tvCitations.setPadding(0, dpToPx(6), 0, 0);
                contentLayout.addView(tvCitations);
            }

            card.addView(contentLayout);
            llChatContainer.addView(card);
        }

        if (scrollChat != null) {
            scrollChat.post(() -> scrollChat.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void renderSuggestedChips(List<String> prompts) {
        if (llSuggestedChipsContainer == null || getContext() == null) return;
        llSuggestedChipsContainer.removeAllViews();

        for (String prompt : prompts) {
            MaterialButton chip = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            chip.setText(prompt);
            chip.setTextSize(12f);
            chip.setCornerRadius(dpToPx(16));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dpToPx(44)
            );
            lp.rightMargin = dpToPx(8);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> viewModel.sendMessage(prompt));

            llSuggestedChipsContainer.addView(chip);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}

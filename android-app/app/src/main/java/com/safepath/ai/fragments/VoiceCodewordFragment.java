package com.safepath.ai.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.safepath.ai.R;
import com.safepath.ai.ai.CodewordDetector;
import com.safepath.ai.utils.VoicePreferenceManager;
import java.util.ArrayList;
import java.util.Locale;

/**
 * VoiceCodewordFragment — UI for personal voice codeword configuration and
 * in-app acoustic calibration/testing.
 *
 * NOTE: Continuous background microphone monitoring has been removed to preserve
 * privacy and battery life. Voice input is exclusively processed on-demand when
 * the user explicitly taps "Test Phrase Recognition".
 */
public class VoiceCodewordFragment extends Fragment {

    private VoicePreferenceManager prefManager;
    private EditText etCodeword;
    private TextInputLayout tilCodeword;
    private RadioGroup rgSensitivity;
    private RadioButton rbBalanced;
    private RadioButton rbHighSensitivity;
    private MaterialCardView cardTestResult;
    private TextView tvTestStatus;
    private TextView tvTestMatchScore;
    private MaterialButton btnTestVoice;
    private MaterialButton btnSaveVoiceCodeword;

    private SpeechRecognizer testRecognizer;
    private boolean isTesting = false;

    private final ActivityResultLauncher<String> recordAudioLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    beginAcousticTest();
                } else {
                    isTesting = false;
                    Toast.makeText(requireContext(),
                            "Microphone permission is required for in-app voice testing.",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_voice_codeword, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefManager = VoicePreferenceManager.getInstance(requireContext());

        initViews(view);
        loadCurrentPreferences();
        setupListeners();
    }

    private void initViews(View view) {
        etCodeword = view.findViewById(R.id.etCodeword);
        tilCodeword = view.findViewById(R.id.tilCodeword);
        rgSensitivity = view.findViewById(R.id.rgSensitivity);
        rbBalanced = view.findViewById(R.id.rbBalanced);
        rbHighSensitivity = view.findViewById(R.id.rbHighSensitivity);
        cardTestResult = view.findViewById(R.id.cardTestResult);
        tvTestStatus = view.findViewById(R.id.tvTestStatus);
        tvTestMatchScore = view.findViewById(R.id.tvTestMatchScore);
        btnTestVoice = view.findViewById(R.id.btnTestVoice);
        btnSaveVoiceCodeword = view.findViewById(R.id.btnSaveVoiceCodeword);
    }

    private void loadCurrentPreferences() {
        String currentCodeword = prefManager.getCodewordPhrase();
        if (TextUtils.isEmpty(currentCodeword)) {
            currentCodeword = "Blue Moon";
        }
        if (etCodeword != null) {
            etCodeword.setText(currentCodeword);
        }

        float threshold = prefManager.getRecognitionThreshold();
        if (threshold <= 0.65f) {
            if (rbHighSensitivity != null) rbHighSensitivity.setChecked(true);
        } else {
            if (rbBalanced != null) rbBalanced.setChecked(true);
        }
    }

    private void setupListeners() {
        if (btnSaveVoiceCodeword != null) {
            btnSaveVoiceCodeword.setOnClickListener(v -> savePreferences());
        }

        if (btnTestVoice != null) {
            btnTestVoice.setOnClickListener(v -> {
                isTesting = true;
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO);
                } else {
                    beginAcousticTest();
                }
            });
        }
    }

    private void savePreferences() {
        String phrase = etCodeword != null && etCodeword.getText() != null
                ? etCodeword.getText().toString().trim()
                : "";

        if (TextUtils.isEmpty(phrase)) {
            if (tilCodeword != null) tilCodeword.setError("Codeword phrase cannot be empty");
            return;
        }

        if (phrase.split("\\s+").length < 2) {
            if (tilCodeword != null) {
                tilCodeword.setError("Use at least a two-word phrase (e.g. 'Blue Moon') to avoid accidental triggers");
            }
            return;
        }

        if (tilCodeword != null) tilCodeword.setError(null);

        float threshold = 0.70f;
        if (rbHighSensitivity != null && rbHighSensitivity.isChecked()) {
            threshold = 0.60f;
        }

        prefManager.saveCodeword(phrase, threshold);
        Toast.makeText(requireContext(), "Codeword configuration updated securely.", Toast.LENGTH_SHORT).show();
    }

    private void beginAcousticTest() {
        if (cardTestResult != null) {
            cardTestResult.setVisibility(View.VISIBLE);
        }
        if (tvTestStatus != null) {
            tvTestStatus.setText("Listening... Say your phrase clearly.");
            tvTestStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_primary));
        }
        if (tvTestMatchScore != null) {
            tvTestMatchScore.setText("Match Confidence: Calculating...");
        }

        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            if (tvTestStatus != null) {
                tvTestStatus.setText("Speech recognition unavailable on device.");
            }
            return;
        }

        if (testRecognizer != null) {
            testRecognizer.destroy();
        }

        testRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext());
        testRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                if (tvTestStatus != null) {
                    tvTestStatus.setText("No speech recognized. Try speaking closer to mic.");
                    tvTestStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_high_risk));
                }
                isTesting = false;
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    evaluateTestMatches(matches);
                } else {
                    if (tvTestStatus != null) {
                        tvTestStatus.setText("Could not recognize spoken phrase.");
                        tvTestStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_high_risk));
                    }
                }
                isTesting = false;
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        testRecognizer.startListening(intent);
    }

    private void evaluateTestMatches(ArrayList<String> matches) {
        String configuredPhrase = prefManager.getCodewordPhrase();
        if (TextUtils.isEmpty(configuredPhrase)) configuredPhrase = "Blue Moon";

        float bestSimilarity = 0f;
        String bestMatch = "";

        for (String spoken : matches) {
            float sim = CodewordDetector.calculateSimilarity(spoken, configuredPhrase);
            if (sim > bestSimilarity) {
                bestSimilarity = sim;
                bestMatch = spoken;
            }
        }

        int scorePercent = Math.round(bestSimilarity * 100f);
        float threshold = prefManager.getRecognitionThreshold();
        boolean wouldTrigger = bestSimilarity >= threshold;

        if (tvTestStatus != null) {
            if (wouldTrigger) {
                tvTestStatus.setText("SUCCESS: Phrase Recognized! (Test Only - SOS NOT Triggered)");
                tvTestStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_safe_green));
            } else {
                tvTestStatus.setText("Below threshold (" + Math.round(threshold * 100f) + "% required). Speak clearly or adjust sensitivity.");
                tvTestStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_high_risk));
            }
        }

        if (tvTestMatchScore != null) {
            tvTestMatchScore.setText("Spoken: \"" + bestMatch + "\" | Confidence: " + scorePercent + "%");
        }
    }

    @Override
    public void onDestroyView() {
        if (testRecognizer != null) {
            testRecognizer.destroy();
            testRecognizer = null;
        }
        super.onDestroyView();
    }
}

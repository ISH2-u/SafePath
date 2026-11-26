package com.safepath.ai.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
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
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputLayout;
import com.safepath.ai.R;
import com.safepath.ai.services.VoiceRecognitionService;
import com.safepath.ai.utils.VoicePreferenceManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * VoiceCodewordFragment — UI for personal voice codeword management,
 * sensitivity calibration, safe testing, and foreground listener control.
 */
public class VoiceCodewordFragment extends Fragment {

    private VoicePreferenceManager prefManager;
    private SwitchMaterial switchVoiceMonitoring;
    private TextView tvMonitoringStatusSubtitle;
    private ImageView ivServiceStatusIcon;
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

    private final ActivityResultLauncher<String[]> permissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean audioGranted = Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO));
                if (audioGranted) {
                    if (switchVoiceMonitoring != null && switchVoiceMonitoring.isChecked()) {
                        startVoiceService();
                    } else if (isTesting) {
                        beginAcousticTest();
                    }
                } else {
                    if (switchVoiceMonitoring != null) {
                        switchVoiceMonitoring.setChecked(false);
                    }
                    Toast.makeText(requireContext(),
                            "Microphone permission is required to detect your emergency codeword.",
                            Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<String> recordAudioLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    if (switchVoiceMonitoring != null && switchVoiceMonitoring.isChecked()) {
                        startVoiceService();
                    } else if (isTesting) {
                        beginAcousticTest();
                    }
                } else {
                    if (switchVoiceMonitoring != null) {
                        switchVoiceMonitoring.setChecked(false);
                    }
                    Toast.makeText(requireContext(),
                            "Microphone permission is required to detect your emergency codeword.",
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
        switchVoiceMonitoring = view.findViewById(R.id.switchVoiceMonitoring);
        tvMonitoringStatusSubtitle = view.findViewById(R.id.tvMonitoringStatusSubtitle);
        ivServiceStatusIcon = view.findViewById(R.id.ivServiceStatusIcon);
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

        boolean isActive = prefManager.isMonitoringActive();
        updateMonitoringStateUI(isActive);
    }

    private void setupListeners() {
        if (switchVoiceMonitoring != null) {
            switchVoiceMonitoring.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    List<String> needed = new ArrayList<>();
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                            != PackageManager.PERMISSION_GRANTED) {
                        needed.add(Manifest.permission.RECORD_AUDIO);
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                                    != PackageManager.PERMISSION_GRANTED) {
                        needed.add(Manifest.permission.POST_NOTIFICATIONS);
                    }

                    if (!needed.isEmpty()) {
                        permissionsLauncher.launch(needed.toArray(new String[0]));
                    } else {
                        startVoiceService();
                    }
                } else {
                    stopVoiceService();
                }
            });
        }

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

    private void startVoiceService() {
        VoiceRecognitionService.start(requireContext());
        prefManager.setMonitoringActive(true);
        updateMonitoringStateUI(true);
        Toast.makeText(requireContext(), "On-device codeword listening active.", Toast.LENGTH_SHORT).show();
    }

    private void stopVoiceService() {
        VoiceRecognitionService.stop(requireContext());
        prefManager.setMonitoringActive(false);
        updateMonitoringStateUI(false);
        Toast.makeText(requireContext(), "Voice codeword listening paused.", Toast.LENGTH_SHORT).show();
    }

    private void updateMonitoringStateUI(boolean isActive) {
        if (switchVoiceMonitoring != null) {
            switchVoiceMonitoring.setChecked(isActive);
        }
        if (tvMonitoringStatusSubtitle != null) {
            tvMonitoringStatusSubtitle.setText(isActive ? "Active (On-Device Listening)" : "Currently Inactive");
            tvMonitoringStatusSubtitle.setTextColor(ContextCompat.getColor(requireContext(),
                    isActive ? R.color.color_safe_green : R.color.color_on_surface_variant));
        }
        if (ivServiceStatusIcon != null) {
            ivServiceStatusIcon.setImageResource(isActive ? R.drawable.ic_mic : R.drawable.ic_mic_off);
            ivServiceStatusIcon.setColorFilter(ContextCompat.getColor(requireContext(),
                    isActive ? R.color.color_safe_green : R.color.color_on_surface_variant));
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
            float sim = calculateSimilarity(configuredPhrase.toLowerCase(Locale.ROOT),
                    spoken.toLowerCase(Locale.ROOT));
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

    private float calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0f;
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0f;
        int distance = computeLevenshteinDistance(s1, s2);
        return 1.0f - ((float) distance / (float) maxLen);
    }

    private int computeLevenshteinDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
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

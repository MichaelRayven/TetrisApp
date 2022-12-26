package com.example.tetrisapp.ui.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.tetrisapp.R;
import com.example.tetrisapp.databinding.FragmentSigninBinding;
import com.example.tetrisapp.ui.activity.MainActivity;
import com.example.tetrisapp.util.OnClickListener;
import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class SignInFragment extends Fragment {
    private static final String TAG = "SignInFragment";
    private FragmentSigninBinding binding;

    private SignInClient oneTapClient;
    private BeginSignInRequest signInRequest;

    private FirebaseAuth mAuth;
    private final ActivityResultLauncher<IntentSenderRequest> activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
        try {
            SignInCredential credential = oneTapClient.getSignInCredentialFromIntent(result.getData());
            String idToken = credential.getGoogleIdToken();
            if (idToken !=  null) {
                AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(idToken, null);
                mAuth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener(requireActivity(), task -> {
                                if (task.isSuccessful()) {
                                    Log.d(TAG, "signInWithCredential:success");
                                    FirebaseUser user = mAuth.getCurrentUser();
                                    if (user != null) {
                                        Navigation.findNavController(binding.getRoot()).navigate(R.id.action_signInFragment_to_profileFragment);
                                    }
                                } else {
                                    Log.w(TAG, "signInWithCredential:failure", task.getException());
                                }
                            }
                        );
                Log.d(TAG, "Got ID token.");
            }
        } catch (ApiException e) {
            Log.d(TAG, e.getLocalizedMessage());
        }
    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSigninBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        oneTapClient = Identity.getSignInClient(requireActivity());
        signInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        .setServerClientId(getString(R.string.default_web_client_id))
                        .setFilterByAuthorizedAccounts(true)
                        .build())
                .setAutoSelectEnabled(true)
                .build();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initOnClickListeners();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initOnClickListeners() {
        binding.btnSignIn.setOnTouchListener(new OnClickListener((MainActivity) requireActivity()));
        binding.btnSignIn.setOnClickListener(v -> {
            String email = binding.etEmail.getText().toString();
            String password = binding.etPassword.getText().toString();

            resetFields();
            signIn(email, password);
        });

        binding.btnSignInGoogle.setOnTouchListener(new OnClickListener((MainActivity) requireActivity()));
        binding.btnSignInGoogle.setOnClickListener(v -> oneTapClient.beginSignIn(signInRequest)
                .addOnCompleteListener(requireActivity(), result -> {
                    try {
                        activityResultLauncher.launch(new IntentSenderRequest.Builder(result.getResult().getPendingIntent().getIntentSender()).build());
                    } catch (Throwable e) {
                        if (e instanceof ApiException) {
                            Log.d(TAG, "The user doesn't have a signed-in account");
                        } else {
                            Log.e(TAG, e.getLocalizedMessage());
                        }
                    }
                })
                .addOnFailureListener(requireActivity(), e -> Log.d(TAG, e.getLocalizedMessage()))
        );

        binding.btnSwitchToSignUp.setOnTouchListener(new OnClickListener((MainActivity) requireActivity()));
        binding.btnSwitchToSignUp.setOnClickListener(v -> Navigation.findNavController(binding.getRoot()).navigate(R.id.action_signInFragment_to_signUpFragment));
    }

    private void resetFields() {
        binding.etEmail.setText("");
        binding.etPassword.setText("");
    }

    private void signIn(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity(), task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            Navigation.findNavController(binding.getRoot()).navigate(R.id.action_signInFragment_to_profileFragment);
                        }
                    } else {
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        Toast.makeText(requireContext(), "Authentication failed.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}

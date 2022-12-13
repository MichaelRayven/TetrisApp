package com.example.tetrisapp.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.tetrisapp.R;
import com.example.tetrisapp.databinding.GameOverFragmentBinding;
import com.example.tetrisapp.databinding.PauseFragmentBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class PauseFragment extends Fragment {
    private PauseFragmentBinding binding;
    private boolean dialogOpen = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = PauseFragmentBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        binding.btnResume.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());
        binding.btnLeave.setOnClickListener(v -> confirmExit());

        super.onViewCreated(view, savedInstanceState);
    }

    private void confirmExit() {
        if (!dialogOpen) {
            dialogOpen = true;
            new MaterialAlertDialogBuilder(requireContext(), R.style.LightDialogTheme)
                    .setTitle(getString(R.string.exit_dialog_title))
                    .setMessage(getString(R.string.exit_dialog_description))
                    .setNegativeButton(getString(R.string.disagree), (dialog, which) -> {
                        dialogOpen = false;
                    })
                    .setPositiveButton(getString(R.string.agree), (dialog, which) -> {
                        Navigation.findNavController(binding.getRoot()).navigate(R.id.action_pauseFragment_to_mainMenuFragment);
                    })
                    .show();
        }
    }
}
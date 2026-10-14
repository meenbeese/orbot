package org.torproject.android.ui.v3onionservice;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import org.torproject.android.R;
import org.torproject.android.service.db.OnionServiceColumns;

public class OnionServiceCreateDialogFragment extends DialogFragment {

    private EditText etServer, etLocalPort, etOnionPort;
    private TextWatcher inputValidator;

    public static void show(FragmentManager fragmentManager) {
        new OnionServiceCreateDialogFragment().show(fragmentManager, OnionServiceCreateDialogFragment.class.getSimpleName());
    }

    @Override
    public void onStart() {
        super.onStart();
        inputValidator.afterTextChanged(null); // initially disable positive button
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final var dialogView = requireActivity().getLayoutInflater().inflate(R.layout.layout_hs_data_dialog, null);
        etServer = dialogView.findViewById(R.id.hsName);
        etLocalPort = dialogView.findViewById(R.id.hsLocalPort);
        etOnionPort = dialogView.findViewById(R.id.hsOnionPort);

        var alertDialog = new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.hidden_services)
                .setNegativeButton(android.R.string.cancel, (dialog, _) -> dialog.cancel())
                .setPositiveButton(R.string.save, (_, _) -> doSave(requireActivity()))
                .setView(dialogView)
                .create();


        inputValidator = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { // no-op
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { //no-op
            }

            @Override
            public void afterTextChanged(Editable s) {
                Button btn = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                try {
                    int localPort = Integer.parseInt(etLocalPort.getText().toString());
                    int onionPort = Integer.parseInt(etOnionPort.getText().toString());
                    btn.setEnabled(checkInput(localPort, onionPort));
                } catch (NumberFormatException nfe) {
                    btn.setEnabled(false);
                }
            }
        };

        etServer.addTextChangedListener(inputValidator);
        etLocalPort.addTextChangedListener(inputValidator);
        etOnionPort.addTextChangedListener(inputValidator);
        return alertDialog;
    }

    private boolean checkInput(int local, int remote) {
        if ((local < 1 || local > 65535) || (remote < 1 || remote > 65535)) return false;
        return !TextUtils.isEmpty(etServer.getText().toString().trim());
    }

    private void doSave(Context context) {
        var serverName = etServer.getText().toString().trim();
        var localPort = Integer.parseInt(etLocalPort.getText().toString());
        var onionPort = Integer.parseInt(etOnionPort.getText().toString());
        var fields = new ContentValues();
        fields.put(OnionServiceColumns.NAME, serverName);
        fields.put(OnionServiceColumns.PORT, localPort);
        fields.put(OnionServiceColumns.ONION_PORT, onionPort);
        fields.put(OnionServiceColumns.CREATED_BY_USER, 1);
        context.getContentResolver().insert(OnionServiceContentProvider.CONTENT_URI, fields);
        Toast.makeText(context, R.string.please_restart_Orbot_to_enable_the_changes, Toast.LENGTH_SHORT).show();
        ((OnionServiceActivity) requireActivity()).showBatteryOptimizationsMessageIfAppropriate();
    }

}
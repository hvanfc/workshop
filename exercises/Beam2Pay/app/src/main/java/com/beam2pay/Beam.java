/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.beam2pay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.provider.Settings;
import android.text.InputFilter;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.DecimalFormat;


public class Beam extends Activity implements CreateNdefMessageCallback,
        OnNdefPushCompleteCallback {
    public static final String BEAM_2_PAY_PREFERENCES = "Beam2PayPreferences";
    public static final String CURRENT_AMOUNT = "CurrentAmount";
    public static final float DEFAULT_CURRENT_AMOUNT = 5000f;

    private NfcAdapter mNfcAdapter;

    private TextView mBalanceText;
    private EditText mAmounToSendText;

    private static final int MESSAGE_SENT = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beam);

        mBalanceText = (TextView) findViewById(R.id.textView);
        mAmounToSendText = (EditText) findViewById(R.id.editAmountText);

        mAmounToSendText.setFilters(new InputFilter[]{new DecimalDigitsInputFilter(7, 2)});

        float currentAmount = getCurrentAmount();
        setCurrentAmount(currentAmount);
        mBalanceText.setText(formatFloat(currentAmount));

        // Check for available NFC Adapter
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null) {
            mBalanceText = (TextView) findViewById(R.id.textView);
            mBalanceText.setText("NFC is not available on this device.");
        }
        // Register callback to set NDEF message
        // TODO: Register this instance to listen to create NDEF messages
        // Register callback to listen for message-sent success
        // TODO: Register this instance to listen to push complete messages
    }


    /**
     * Implementation for the CreateNdefMessageCallback interface
     */
    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        EditText amountText = (EditText) findViewById(R.id.editAmountText);
        String amount = amountText.getText().toString();
        byte[] bytes = toByteArray(Float.valueOf(amount));
        NdefRecord[] ndefRecords = {
            // TODO: create NDEF record for mimetype application/com.example.android.beam
        };
        return  new NdefMessage(ndefRecords);
    }

    /**
     * Implementation for the OnNdefPushCompleteCallback interface
     */
    @Override
    public void onNdefPushComplete(NfcEvent arg0) {
        // A handler is needed to send messages to the activity when this
        // callback occurs, because it happens from a binder thread
        mHandler.obtainMessage(MESSAGE_SENT).sendToTarget();
    }

    /** This handler receives a message from onNdefPushComplete */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_SENT:
                    String amountToSendText = mAmounToSendText.getText().toString();
                    float amountToSend = Float.valueOf(amountToSendText);
                    float currentAmount = getCurrentAmount();
                    currentAmount  -= amountToSend;
                    setCurrentAmount(currentAmount);
                    mBalanceText.setText(formatFloat(currentAmount));
                    Toast.makeText(Beam.this, "Sent €" + formatFloat(amountToSend), Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private String formatFloat(float amount) {
        DecimalFormat formatter = new DecimalFormat("#0.00");
        return  formatter.format(amount);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Check to see that the Activity started due to an Android Beam
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        // onResume gets called after this to handle the intent
        setIntent(intent);
    }

    /**
     * Parses the NDEF Message from the intent and prints to the TextView
     */
    void processIntent(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);
        // only one message sent during the beam
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        // record 0 contains the MIME type, record 1 is the AAR, if present
        NdefRecord[] records = msg.getRecords();
        byte[] payload = records[0].getPayload();
        float incomingAmount = toFloat(payload);
        float currentAmount = getCurrentAmount();
        currentAmount += incomingAmount;
        setCurrentAmount(currentAmount);
        mBalanceText.setText(formatFloat(currentAmount));
        Toast.makeText(this, "Received €" + formatFloat(incomingAmount), Toast.LENGTH_SHORT).show();
    }

    public static byte[] toByteArray(float value) {
        byte[] bytes = new byte[8];
        ByteBuffer.wrap(bytes).putFloat(value);
        return bytes;
    }

    private float getCurrentAmount() {
        SharedPreferences sharedPreferences = getSharedPreferences(BEAM_2_PAY_PREFERENCES, MODE_PRIVATE);
        return sharedPreferences.getFloat(CURRENT_AMOUNT, DEFAULT_CURRENT_AMOUNT);
    }

    private void setCurrentAmount(float currentAmount) {
        SharedPreferences sharedPreferences = getSharedPreferences(BEAM_2_PAY_PREFERENCES, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(CURRENT_AMOUNT,currentAmount);
        editor.apply();
    }

    public static float toFloat(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getFloat();
    }

}

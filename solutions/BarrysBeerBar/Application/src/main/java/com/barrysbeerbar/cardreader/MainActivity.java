/*
* Copyright 2013 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.barrysbeerbar.cardreader;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareUltralight;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.android.cardreader.R;

import java.io.IOException;
import java.nio.charset.Charset;

public class MainActivity extends Activity  implements NfcAdapter.ReaderCallback{

    public static final String BARRYS_BEER_BAR_PREFERENCES = "BarrysBeerBarPreferences";

    public static final String TAG = "MainActivity";
    public static final int PAGE_OFFSET = 4;

    public static int READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;

    private String customerToRegister = null;

    private boolean takingOrders = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getActionBar();
        if(actionBar !=null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setLogo(R.drawable.ic_launcher);
        }

        setContentView(R.layout.activity_main);

        NfcAdapter nfc = NfcAdapter.getDefaultAdapter(this);
        if (nfc != null) {
            nfc.enableReaderMode(this, this, READER_FLAGS, null);
        }
    }

    public void registerCustomer(View view) {
        takingOrders = false;
        showInactive((Button) findViewById(R.id.takeOrder));
        showActive((Button) findViewById(R.id.register));

        final EditText input = new EditText(this);
        input.setFilters(new InputFilter[]{new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                if (!source.toString().matches("^\\p{ASCII}*$"))
                    return "";
                return null;
            }
        }});
        new AlertDialog.Builder(this)
            .setTitle("Register customer")
            .setMessage("Fill in customer name")
            .setView(input)
            .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    customerToRegister = input.getText().toString();
                }
            }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    showInactive((Button) findViewById(R.id.register));
                }
            }).show();
    }

    public void takeOrder(View view) {
        takingOrders = true;
        customerToRegister = null;

        showInactive((Button) findViewById(R.id.register));
        showActive((Button) findViewById(R.id.takeOrder));
    }

    private void showActive(final Button button) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                button.getBackground().setColorFilter(getResources().getColor(R.color.activeButton), PorterDuff.Mode.MULTIPLY);
            }
        });
    }

    private void showInactive(final Button button) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                button.getBackground().setColorFilter(getResources().getColor(R.color.inactiveButton), PorterDuff.Mode.MULTIPLY);
            }
        });
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if(customerToRegister != null) {
            final String customerName = writeTag(tag, padRight(customerToRegister, 16)).trim();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, customerName + " registered. ", Toast.LENGTH_SHORT).show();
                }
            });
            customerToRegister=null;
            showInactive((Button) findViewById(R.id.register));
        }
        if(takingOrders) {
            final String customerName = readTag(tag).trim();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int orders = getOrders(customerName);
                    orders = ++orders;
                    setOrders(customerName, orders);
                    String message = String.format("%s wants a beer! %s now has %d orders.", customerName, customerName, orders);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
            takingOrders = false;
            showInactive((Button) findViewById(R.id.takeOrder));
        }
    }

    public static String padRight(String s, int n) {
        return String.format("%1$-" + n + "s", s);
    }


    private String writeTag(Tag tag, String tagText) {
        MifareUltralight mifare = MifareUltralight.get(tag);
        if(mifare!=null) {
            writeMifareTag(mifare, tagText);
        }
        return tagText;
    }

    private String readTag(Tag tag) {
        MifareUltralight mifare = MifareUltralight.get(tag);
        if(mifare!=null) {
            return readMifareTag(mifare);
        }
        return null;
    }

    private String writeMifareTag(MifareUltralight mifare, String tagText) {
        try {
            mifare.connect();
            for (int i = 0; i < 4; i++) {
                int stringIndex = i * 4;
                String pageString = tagText.substring(stringIndex, stringIndex + 4);
                mifare.writePage(PAGE_OFFSET + i, pageString.getBytes(Charset.forName("US-ASCII")));
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException while closing MifareUltralight...", e);
        } finally {
            try {
                mifare.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException while closing MifareUltralight...", e);
            }
        }
        return tagText;
    }


    private String readMifareTag(MifareUltralight mifare) {
        try {
            mifare.connect();
            byte[] payload = mifare.readPages(PAGE_OFFSET);
            return new String(payload, Charset.forName("US-ASCII"));
        } catch (IOException e) {
            Log.e(TAG, "IOException while writing MifareUltralight message...", e);
        } finally {
            if (mifare != null) {
                try {
                    mifare.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing tag...", e);
                }
            }
        }
        return null;
    }

    private int getOrders(String customerName) {
        SharedPreferences sharedPreferences = getSharedPreferences(BARRYS_BEER_BAR_PREFERENCES, MODE_PRIVATE);
        return sharedPreferences.getInt(customerName, 0);
    }

    private void setOrders(String customerName, int orders) {
        SharedPreferences sharedPreferences = getSharedPreferences(BARRYS_BEER_BAR_PREFERENCES, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(customerName, orders);
        editor.apply();
    }
}

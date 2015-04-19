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
import java.util.Arrays;

public class MainActivity extends Activity  implements NfcAdapter.ReaderCallback{

    public static final String BARRYS_BEER_BAR_PREFERENCES = "BarrysBeerBarPreferences";

    public static final String TAG = "MainActivity";
    public static final int PAGE_OFFSET = 4;

    // AID
    private static final String AID = "F222333222";
    // ISO-DEP command HEADER for selecting an AID.
    // Format: [Class | Instruction | Parameter 1 | Parameter 2]
    private static final String SELECT_APDU_HEADER = "00A40400";
    // "OK" status word sent in response to SELECT AID command (0x9000)
    private static final byte[] SELECT_OK_SW = {(byte) 0x90, (byte) 0x00};

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

        IsoDep isoDep = IsoDep.get(tag);
        if(isoDep!=null) {
            return readIsoDepTag(isoDep);
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

    private String readIsoDepTag(IsoDep isoDep) {
        try {
            isoDep.connect();
            Log.i(TAG, "Requesting remote AID: " + AID);
            byte[] command = BuildSelectApdu(AID);

            Log.i(TAG, "Sending: " + ByteArrayToHexString(command));
            byte[] result = isoDep.transceive(command);

            // If AID is successfully selected, 0x9000 is returned as the status word (last 2
            // bytes of the result) by convention. Everything before the status word is
            // optional payload, which is used here to hold the account number.
            int resultLength = result.length;
            byte[] statusWord = {result[resultLength - 2], result[resultLength - 1]};
            byte[] payload = Arrays.copyOf(result, resultLength - 2);
            if (Arrays.equals(SELECT_OK_SW, statusWord)) {
                return new String(payload, "US-ASCII");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error communicating with card: " + e.toString());
        }
        return null;
    }


    /**
     * Build APDU for SELECT AID command. This command indicates which service a reader is
     * interested in communicating with. See ISO 7816-4.
     *
     * @param aid Application ID (AID) to select
     * @return APDU for SELECT AID command
     */
    public static byte[] BuildSelectApdu(String aid) {
        // Format: [CLASS | INSTRUCTION | PARAMETER 1 | PARAMETER 2 | LENGTH | DATA]
        return HexStringToByteArray(SELECT_APDU_HEADER + String.format("%02X", aid.length() / 2) + aid);
    }

    /**
     * Utility class to convert a hexadecimal string to a byte string.
     *
     * <p>Behavior with input strings containing non-hexadecimal characters is undefined.
     *
     * @param s String containing hexadecimal characters to convert
     * @return Byte array generated from input
     */
    public static byte[] HexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    /**
     * Utility class to convert a byte array to a hexadecimal string.
     *
     * @param bytes Bytes to convert
     * @return String, containing hexadecimal representation.
     */
    public static String ByteArrayToHexString(byte[] bytes) {
        final char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for ( int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
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

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

package com.example.android.beam;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Properties;

/**
 * This code is based on:
 *
 * AndroidBeamDemo in the Android SDK.
 *
 * http://stackoverflow.com/a/27009164
 */
public class SetDeviceOwner extends Activity implements
        CreateNdefMessageCallback, OnNdefPushCompleteCallback {
    private static final int MESSAGE_SENT = 1;
    private static final String TAG = "Beam";

    private NfcAdapter mNfcAdapter;
    private TextView mInfoText, mChecksum;
    private EditText mPackageName, mUrl;
    private EditText mWiFiSSID, mWiFiPassWD, mWiFiSType;
    private boolean checksumCalculated = true;

    private OnEditorActionListener mOnEditorActionListener = new OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            checksumCalculated = true;
            return false;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mInfoText = (TextView) findViewById(R.id.textView);
        mInfoText.setText(R.string.info);
        mPackageName = (EditText) findViewById(R.id.package_name);
        mPackageName.setOnEditorActionListener(mOnEditorActionListener);
        mChecksum = (TextView) findViewById(R.id.checksum);
        mUrl = (EditText) findViewById(R.id.url);
        mUrl.setOnEditorActionListener(mOnEditorActionListener);
        mWiFiSSID = (EditText) findViewById(R.id.wifi_ssid);
        mWiFiSSID.setOnEditorActionListener(mOnEditorActionListener);
        mWiFiPassWD = (EditText) findViewById(R.id.wifi_passwd);
        mWiFiPassWD.setOnEditorActionListener(mOnEditorActionListener);
        mWiFiSType = (EditText) findViewById(R.id.wifi_S_type);
        mWiFiSType.setOnEditorActionListener(mOnEditorActionListener);

        // Check for available NFC Adapter
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (mNfcAdapter == null) {
            mInfoText.setText("NFC is not available on this device.");
            mPackageName.setEnabled(false);
            mChecksum.setEnabled(false);
            mUrl.setEnabled(false);
        } else {
            // Register callback to set NDEF message
            mNfcAdapter.setNdefPushMessageCallback(this, this);
            // Register callback to listen for message-sent success
            mNfcAdapter.setOnNdefPushCompleteCallback(this, this);
        }
    }

    public void computeChecksum(View v) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String checksum = getChecksum(new URL(mUrl.getText()
                            .toString()));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mChecksum.setText(checksum);
                            checksumCalculated = true;
                        }
                    });

                } catch (MalformedURLException e) {
                    Log.e(TAG, "Error in computeChecksum:", e);
                }
            }
        }).start();
    }

    public String getChecksum(URL url) {
        try {
            BufferedInputStream in = new BufferedInputStream(url.openStream());
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer, 0, buffer.length)) > 0)
                sha.update(buffer, 0, len);
            in.close();
            byte[] result = new byte[200];
            len = sha.digest(result, 0, 200);
            String checksum = Base64.encodeToString(result, 0, len,
                    Base64.URL_SAFE);
            return checksum;
        } catch (Exception e) {
            Log.e(TAG, "Error in getCheckum:", e);
            return null;
        }
    }

    /**
     * Implementation for the CreateNdefMessageCallback interface
     */
    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        Log.d(TAG, "createNdefMessage");
        if (!checksumCalculated) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(SetDeviceOwner.this, "You need to compute the checksum first!",
                            Toast.LENGTH_LONG).show();
                }
            });
            return null;
        }
        try {
            Properties p = new Properties();

            p.setProperty(
                    DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                    mPackageName.getText().toString());
            p.setProperty(
                    DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION,
                    mUrl.getText().toString());
            p.setProperty(
                    DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM,
                    mChecksum.getText().toString());

//            p.setProperty(
//                DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID,
//                mWiFiSSID.getText().toString());
            p.setProperty(
                DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID,
                "AndroidAP");

//            p.setProperty(
//                DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PASSWORD,
//                mWiFiPassWD.getText().toString());
            p.setProperty(
                DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PASSWORD,
                "12345678");

            p.setProperty(
                DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE,
                mWiFiSType.getText().toString());

            Log.e(TAG, "mWiFiSSID.getText().toString() " + mWiFiSSID.getText().toString());
            Log.e(TAG, "mWiFiPassWD.getText().toString() " + mWiFiPassWD.getText().toString());
            Log.e(TAG, "mWiFiSType.getText().toString() " + mWiFiSType.getText().toString());

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            FileOutputStream outputStream = new FileOutputStream(new File(getExternalFilesDir(
                Environment.DIRECTORY_DOWNLOADS), "CS_bytes.txt"));

            OutputStream out = new ObjectOutputStream(bos);
            OutputStream out2 = new ObjectOutputStream(outputStream);
            p.store(out, "");
            p.store(out2, "");
            final byte[] bytes = bos.toByteArray();

            NdefMessage msg = new NdefMessage(NdefRecord.createMime(
                    DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC, bytes));

            Log.e(TAG, "bytes " + bytes.toString());

            out2.close();

            return msg;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Implementation for the OnNdefPushCompleteCallback interface
     */
    @Override
    public void onNdefPushComplete(NfcEvent arg0) {
        Log.d(TAG, "onNdefPushComplete");
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
                Log.d(TAG, "MESSAGE_SENT");
                Toast.makeText(getApplicationContext(), "Message sent!",
                        Toast.LENGTH_LONG).show();
                break;
            }
        }
    };

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
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
        Log.d(TAG, "processIntent " + intent);
        Parcelable[] rawMsgs = intent
                .getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        // only one message sent during the beam
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        // record 0 contains the MIME type, record 1 is the AAR, if present
        mInfoText.setText(new String(msg.getRecords()[0].getPayload()));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // If NFC is not available, we won't be needing this menu
        if (mNfcAdapter == null) {
            return super.onCreateOptionsMenu(menu);
        }
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_settings:
            Intent intent = new Intent(Settings.ACTION_NFCSHARING_SETTINGS);
            startActivity(intent);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
}

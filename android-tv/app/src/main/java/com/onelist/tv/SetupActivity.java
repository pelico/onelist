package com.onelist.tv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class SetupActivity extends Activity {

    private static final String PREFS_NAME = "OneListTV";
    private static final String KEY_SERVER_URL = "server_url";

    private EditText editUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        editUrl = findViewById(R.id.editUrl);
        Button btnSave = findViewById(R.id.btnSave);
        TextView tvHint = findViewById(R.id.tvHint);

        // Load previously saved URL as default
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUrl = prefs.getString(KEY_SERVER_URL, "");
        if (!savedUrl.isEmpty()) {
            editUrl.setText(savedUrl);
            editUrl.setSelection(savedUrl.length());
        } else {
            editUrl.setHint("http://192.168.1.100:5245");
        }

        btnSave.setOnClickListener(v -> saveAndLaunch());

        // Also allow Enter key to save
        editUrl.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
                saveAndLaunch();
                return true;
            }
            return false;
        });

        editUrl.requestFocus();
    }

    private void saveAndLaunch() {
        String url = editUrl.getText().toString().trim();

        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }

        // Auto-add http:// if no protocol specified
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }

        // Remove trailing slash
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        // Save URL
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_SERVER_URL, url).apply();

        Toast.makeText(this, "已保存，正在连接...", Toast.LENGTH_SHORT).show();

        // Launch main activity
        finish();
    }
}

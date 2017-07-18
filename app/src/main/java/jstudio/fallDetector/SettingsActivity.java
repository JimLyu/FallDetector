package jstudio.fallDetector;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.input.InputManager;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Map;
import java.util.UUID;
import java.util.prefs.Preferences;

public class SettingsActivity extends AppCompatActivity {

    static final String USER_NAME = "pref_key_user_name";
    static final String USER_UUID = "pref_key_user";
    static final String USER_DATA = "pref_key_user_data";
    static final String NET = "pref_key_net";
    static final String NET_STATE = "pref_key_net_state";
    static final String NET_IP = "pref_key_net_IP";
    static final String SMS = "pref_key_sms";
    static final String SMS_NUMBER = "pref_key_sms_number";
    static final String SMS_CONTENT = "pref_key_sms_content";
    static final String[] UPDATE = {USER_NAME, NET_IP, SMS_NUMBER, SMS_CONTENT};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        if(actionBar != null){
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new Settings())
                .commit();
    }

    public static class Settings extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            updateSummary();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
           updateSummary();//一次重整全部
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
            findPreference(NET_STATE).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    String ip = getPreferenceManager().getSharedPreferences().getString(NET_IP, "");
                    int state = Integer.parseInt(getPreferenceManager().getSharedPreferences().getString(NET_STATE, "0"));
                    new ConnectToServer(ip, state);
                    return false;
                }
            });
            findPreference(USER_NAME).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    final View layout = LayoutInflater.from(getActivity()).inflate(R.layout.layout_selectfile, null);
                    final EditText editText = (EditText) layout.findViewById(R.id.editText);
                    String name = getPreferenceManager().getSharedPreferences().getString(USER_NAME, MainActivity.defaultName);
                    editText.setText(name);
                    editText.setSelection(0, name.length());
                    editText.requestFocus();
                    AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                            .setTitle(getResources().getString(R.string.name_update)+ MainActivity.NAME_LIMIT + getResources().getString(R.string.name_limit))
                            .setView(layout)
                            .setPositiveButton("確認", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    Message msg = new Message();
                                    msg.what = MainActivity.handler.UPDATE_NAME;
                                    msg.obj = editText.getText().toString();
                                    MainActivity.handler.sendMessage(msg);
                                }
                            }).create();
                    Window w = alertDialog.getWindow();
                    if(w != null){
                        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                    }
                    alertDialog.show();
                    return false;
                }
            });
            findPreference(USER_DATA).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent().setClass(getActivity(), SQLiteActivity.class));
                    return false;
                }
            });
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        void updateSummary(){
            for(int i = 0; i < UPDATE.length; i++){
                Preference p = findPreference(UPDATE[i]);
                p.setSummary(getPreferenceManager().getSharedPreferences().getString(UPDATE[i], ""));
            }
//            Map<String, ?> allEntries = getPreferenceManager().getSharedPreferences().getAll();
//            for (Map.Entry<String, ?> entry : allEntries.entrySet())
//                findPreference(entry.getKey()).setSummary(entry.getValue().toString());
            changeStateColor();
        }

        void changeStateColor(){
            int state = Integer.parseInt(getPreferenceManager().getSharedPreferences().getString(NET_STATE, "0"));
            Spannable summary = new SpannableString(getResources().getStringArray(R.array.set_net_state_description)[state]);
            switch (state){
                case 0:
                    summary.setSpan(new ForegroundColorSpan(Color.RED), 0, 3, 0);
                    break;
                case 1:
                    summary.setSpan(new ForegroundColorSpan(Color.YELLOW), 0, 3, 0);
                    break;
                case 2:
                    summary.setSpan(new ForegroundColorSpan(Color.GREEN), 0, 3, 0);
            }
            findPreference(NET_STATE).setSummary(summary);
        }
    }
}

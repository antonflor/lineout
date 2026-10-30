package xyz.montylabs.lineout;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SettingsActivity extends Activity {

    private LinearLayout list;
    private MonoTextView rowDefault;
    private MonoTextView rowMedia;
    private MonoTextView rowIcons;
    private MonoTextView rowSeconds;
    private MonoTextView rowHidden;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        list = (LinearLayout) findViewById(R.id.set_list);

        rowDefault = addRow(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)
                        && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 1);
                } else {
                    try {
                        startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        rowMedia = addRow(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                } catch (Exception ignored) {
                }
            }
        });
        rowIcons = addRow(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setShowIcons(SettingsActivity.this, !Prefs.showIcons(SettingsActivity.this));
                render();
            }
        });
        rowSeconds = addRow(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setClockSeconds(SettingsActivity.this,
                        !Prefs.clockSeconds(SettingsActivity.this));
                render();
            }
        });
        rowHidden = addRow(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHiddenDialog();
            }
        });
        addRow(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/antonflor/lineout")));
                } catch (Exception ignored) {
                }
            }
        }).setText("> ABOUT // LINEOUT v" + versionName());
    }

    private MonoTextView addRow(View.OnClickListener onClick) {
        MonoTextView tv = new MonoTextView(this);
        tv.setTypeface(Fonts.get(this, Fonts.JB_MEDIUM));
        tv.setTextSize(14f);
        tv.setTextColor(getColorStateList(R.color.txt_btn_white));
        tv.setBackgroundResource(R.drawable.btn_white);
        int padH = dp(16);
        int padV = dp(13);
        tv.setPadding(padH, padV, padH, padV);
        tv.setClickable(true);
        tv.setFocusable(true);
        tv.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        list.addView(tv, lp);
        return tv;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        render();
    }

    private void render() {
        boolean isDefault = false;
        RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
            isDefault = rm.isRoleHeld(RoleManager.ROLE_HOME);
        }
        rowDefault.setText("> DEFAULT_LAUNCHER " + (isDefault ? "[ OK ]" : "[ SET ]"));
        rowMedia.setText("> MEDIA_ACCESS "
                + (MediaHub.hasAccess(this) ? "[ ON ]" : "[ OFF ]"));
        rowIcons.setText("> SHOW_ICONS "
                + (Prefs.showIcons(this) ? "[ ON ]" : "[ OFF ]"));
        rowSeconds.setText("> CLOCK_SECONDS "
                + (Prefs.clockSeconds(this) ? "[ ON ]" : "[ OFF ]"));
        rowHidden.setText(String.format(Locale.US, "> HIDDEN_APPS [ %d ]",
                Prefs.getHidden(this).size()));
    }

    private void showHiddenDialog() {
        Set<String> hidden = Prefs.getHidden(this);
        if (hidden.isEmpty()) {
            android.widget.Toast.makeText(this, "> NO_HIDDEN_APPS", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        List<AppEntry> all = AppRepository.loadAll(this);
        final ArrayList<String> keys = new ArrayList<String>();
        final ArrayList<String> labels = new ArrayList<String>();
        for (String key : hidden) {
            AppEntry e = AppRepository.findByKey(all, key);
            keys.add(key);
            labels.add("> " + (e == null ? key : e.label.toUpperCase(Locale.getDefault()))
                    + "  [ UNHIDE ]");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, R.layout.row_action, R.id.action_label, labels);
        new AlertDialog.Builder(this)
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Prefs.unhide(SettingsActivity.this, keys.get(which));
                        render();
                    }
                })
                .show();
    }
}

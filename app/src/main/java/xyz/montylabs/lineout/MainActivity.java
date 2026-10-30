package xyz.montylabs.lineout;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements MediaHub.Listener {

    private static final int REQ_ROLE_HOME = 41;

    private MediaHub media;
    private AppsAdapter adapter;
    private List<AppEntry> allApps = new ArrayList<AppEntry>();      // visible in drawer
    private List<AppEntry> allWithHidden = new ArrayList<AppEntry>(); // for resolving pins
    private final HashMap<String, Drawable> iconCache = new HashMap<String, Drawable>();
    private final HashMap<String, String> labelCache = new HashMap<String, String>();
    private boolean showIcons;

    private FrameLayout drawer;
    private GridView grid;
    private MonoEditText search;
    private TextView clock;
    private TextView date;
    private TextView batt;
    private TextView drawerCount;
    private LinearLayout favList;
    private View favEmpty;
    private View setDefaultChip;

    private View npCard;
    private View npBody;
    private TextView npState;
    private View npGrant;
    private TextView npTitle;
    private TextView npArtist;
    private TextView npSrc;
    private TextView npPlay;
    private ImageView npArt;
    private View npProgFill;
    private View npProgRest;

    private final Handler tick = new Handler(Looper.getMainLooper());
    private final Runnable tickRun = new Runnable() {
        @Override
        public void run() {
            updateClock();
            updateBattery();
            updateProgress();
            tick.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            iconCache.clear();
            labelCache.clear();
            refreshApps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawer = (FrameLayout) findViewById(R.id.drawer);
        grid = (GridView) findViewById(R.id.app_grid);
        search = (MonoEditText) findViewById(R.id.search);
        clock = (TextView) findViewById(R.id.clock);
        date = (TextView) findViewById(R.id.date);
        batt = (TextView) findViewById(R.id.status_batt);
        drawerCount = (TextView) findViewById(R.id.drawer_count);
        favList = (LinearLayout) findViewById(R.id.fav_list);
        favEmpty = findViewById(R.id.fav_empty);
        setDefaultChip = findViewById(R.id.set_default_chip);

        npCard = findViewById(R.id.np_card);
        npBody = findViewById(R.id.np_body);
        npState = (TextView) findViewById(R.id.np_state);
        npGrant = findViewById(R.id.np_grant);
        npTitle = (TextView) findViewById(R.id.np_title);
        npArtist = (TextView) findViewById(R.id.np_artist);
        npSrc = (TextView) findViewById(R.id.np_src);
        npPlay = (TextView) findViewById(R.id.np_play);
        npArt = (ImageView) findViewById(R.id.np_art);
        npProgFill = findViewById(R.id.np_prog_fill);
        npProgRest = findViewById(R.id.np_prog_rest);

        media = new MediaHub(this, this);
        adapter = new AppsAdapter();
        grid.setAdapter(adapter);

        wireHome();
        wireDrawer();
        registerPackageReceiver();
        refreshApps();
    }

    private void wireHome() {
        View root = findViewById(R.id.root);
        final GestureDetector gestures = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                        if (e1 == null || e2 == null) {
                            return false;
                        }
                        if (vy < -1200f && (e1.getY() - e2.getY()) > 120f) {
                            openDrawer();
                            return true;
                        }
                        return false;
                    }
                });
        // Claim DOWN so the rest of the gesture stream reaches us on
        // non-clickable surfaces; children still get first dibs.
        View.OnTouchListener swipe = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean handled = gestures.onTouchEvent(event);
                return handled || event.getActionMasked() == MotionEvent.ACTION_DOWN;
            }
        };
        root.setOnTouchListener(swipe);
        findViewById(R.id.home_pane).setOnTouchListener(swipe);
        // The favorites ScrollView consumes vertical gestures for its own
        // scrolling: sniff them without interfering so swipe-up still works.
        View.OnTouchListener sniff = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestures.onTouchEvent(event);
                return false;
            }
        };
        int[] scrollers = {R.id.fav_scroll, R.id.home_scroll};
        for (int i = 0; i < scrollers.length; i++) {
            View sv = findViewById(scrollers[i]);
            if (sv != null) {
                sv.setOnTouchListener(sniff);
            }
        }

        findViewById(R.id.btn_apps).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDrawer();
            }
        });
        findViewById(R.id.btn_sys).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        setDefaultChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestHomeRole();
            }
        });

        npGrant.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                } catch (Exception ignored) {
                }
            }
        });
        findViewById(R.id.np_prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                media.previous();
            }
        });
        npPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                media.playPause();
            }
        });
        findViewById(R.id.np_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                media.next();
            }
        });
        View.OnClickListener open = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                media.openPlayer();
            }
        };
        npArt.setOnClickListener(open);
        npTitle.setOnClickListener(open);
    }

    private void wireDrawer() {
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launch(adapter.get(position));
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showAppActions(adapter.get(position), false);
                return true;
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO && adapter.getCount() > 0) {
                    launch(adapter.get(0));
                    return true;
                }
                return false;
            }
        });
    }

    private void registerPackageReceiver() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(pkgReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(pkgReceiver, f);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(pkgReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        media.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        media.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean icons = Prefs.showIcons(this);
        if (icons != showIcons) {
            showIcons = icons;
            adapter.notifyDataSetChanged();
        }
        refreshApps();
        updateClock();
        updateBattery();
        updateDefaultChip();
        media.refresh();
        tick.removeCallbacks(tickRun);
        tick.post(tickRun);
    }

    @Override
    protected void onPause() {
        super.onPause();
        tick.removeCallbacks(tickRun);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // HOME pressed while we are already home: reset to the front page.
        closeDrawer();
    }

    @Override
    public void onBackPressed() {
        if (drawer.getVisibility() == View.VISIBLE) {
            closeDrawer();
        }
        // A home screen ignores back.
    }

    // ------------------------------------------------------------------ home

    private void updateClock() {
        String fmt = Prefs.clockSeconds(this) ? "HH:mm:ss" : "HH:mm";
        clock.setText(new SimpleDateFormat(fmt, Locale.US).format(new Date()));
        String d = new SimpleDateFormat("EEE yyyy-MM-dd", Locale.US).format(new Date());
        date.setText(d.toUpperCase(Locale.US) + " // LOCAL");
    }

    private void updateBattery() {
        Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bi == null) {
            batt.setText("");
            return;
        }
        int level = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        if (level < 0 || scale <= 0) {
            batt.setText("");
            return;
        }
        int pct = (int) (level * 100f / scale);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        batt.setText(String.format(Locale.US, "PWR %03d%%%s", pct, charging ? " +" : ""));
    }

    private void updateDefaultChip() {
        boolean isDefault = false;
        RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
            isDefault = rm.isRoleHeld(RoleManager.ROLE_HOME);
        }
        setDefaultChip.setVisibility(isDefault ? View.GONE : View.VISIBLE);
    }

    private void requestHomeRole() {
        RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)
                && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_ROLE_HOME);
        } else {
            try {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ROLE_HOME) {
            updateDefaultChip();
        }
    }

    // ----------------------------------------------------------------- media

    @Override
    public void onMediaChanged() {
        boolean access = MediaHub.hasAccess(this);
        if (!access) {
            npBody.setVisibility(View.GONE);
            npState.setVisibility(View.VISIBLE);
            npGrant.setVisibility(View.VISIBLE);
            npState.setText(R.string.np_no_access);
            npSrc.setText("");
            return;
        }
        npGrant.setVisibility(View.GONE);
        if (!media.hasSession()) {
            npBody.setVisibility(View.GONE);
            npState.setVisibility(View.VISIBLE);
            npState.setText(R.string.np_waiting);
            npSrc.setText("");
            return;
        }
        npState.setVisibility(View.GONE);
        npBody.setVisibility(View.VISIBLE);

        String t = media.title();
        String a = media.artist();
        npTitle.setText(t == null ? "UNKNOWN_TRACK" : t);
        npArtist.setText(a == null ? "--" : a);
        String src = media.sourcePackage();
        if (src != null) {
            npSrc.setText("SRC: " + appLabel(src).toUpperCase(Locale.getDefault()));
        } else {
            npSrc.setText("");
        }
        Bitmap art = media.art();
        if (art != null) {
            npArt.setImageBitmap(art);
        } else {
            npArt.setImageDrawable(null);
        }
        npPlay.setText(media.isPlaying() ? R.string.btn_pause : R.string.btn_play);
        updateProgress();
    }

    /** Human name of the app that owns the media session, e.g. "FiiO Music". */
    private String appLabel(String pkg) {
        String cached = labelCache.get(pkg);
        if (cached != null) {
            return cached;
        }
        String label;
        try {
            label = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            int dot = pkg.lastIndexOf('.');
            label = dot >= 0 ? pkg.substring(dot + 1) : pkg;
        }
        labelCache.put(pkg, label);
        return label;
    }

    private void updateProgress() {
        if (npBody.getVisibility() != View.VISIBLE) {
            return;
        }
        float p = media.progress();
        if (p < 0f) {
            p = 0f;
        }
        LinearLayout.LayoutParams fill = (LinearLayout.LayoutParams) npProgFill.getLayoutParams();
        LinearLayout.LayoutParams rest = (LinearLayout.LayoutParams) npProgRest.getLayoutParams();
        fill.weight = p;
        rest.weight = 1f - p;
        npProgFill.setLayoutParams(fill);
        npProgRest.setLayoutParams(rest);
    }

    // ------------------------------------------------------------------ apps

    /**
     * Media apps pinned automatically on first run when installed,
     * in this order. Everything else is one long-press away.
     */
    private static final String[] SEED_PINS = {
            "com.fiio.music",                        // FiiO Music
            "com.google.android.youtube",            // YouTube
            "com.google.android.youtube.tvunplugged",// YouTube TV
            "com.plexapp.android",                   // Plex
            "org.videolan.vlc",                      // VLC
            "com.hiby.music",                        // HiBy Music
            "com.spotify.music",                     // Spotify
            "com.aspiro.tidal",                      // TIDAL
            "com.maxmpz.audioplayer",                // Poweramp
            "com.extreamsd.usbaudioplayerpro",       // UAPP
    };

    private void seedPinsOnce() {
        if (Prefs.isSeeded(this)) {
            return;
        }
        List<String> pins = Prefs.getPins(this);
        for (int k = 0; k < SEED_PINS.length; k++) {
            for (int i = 0; i < allWithHidden.size(); i++) {
                AppEntry e = allWithHidden.get(i);
                if (e.packageName.equals(SEED_PINS[k]) && !pins.contains(e.key())) {
                    pins.add(e.key());
                    break;
                }
            }
        }
        Prefs.setPins(this, pins);
        Prefs.setSeeded(this);
    }

    private void refreshApps() {
        allWithHidden = AppRepository.loadAll(this);
        seedPinsOnce();
        Set<String> hidden = Prefs.getHidden(this);
        ArrayList<AppEntry> visible = new ArrayList<AppEntry>();
        for (int i = 0; i < allWithHidden.size(); i++) {
            AppEntry e = allWithHidden.get(i);
            if (!hidden.contains(e.key())) {
                visible.add(e);
            }
        }
        allApps = visible;
        adapter.setData(visible);
        drawerCount.setText(String.format(Locale.US, "[ %03d ]", visible.size()));
        renderFavs();
    }

    private void renderFavs() {
        // Remove all rows except the empty hint.
        for (int i = favList.getChildCount() - 1; i >= 0; i--) {
            View child = favList.getChildAt(i);
            if (child != favEmpty) {
                favList.removeViewAt(i);
            }
        }
        List<String> pins = Prefs.getPins(this);
        int added = 0;
        for (int i = 0; i < pins.size(); i++) {
            final AppEntry e = AppRepository.findByKey(allWithHidden, pins.get(i));
            if (e == null) {
                continue;
            }
            View row = LayoutInflater.from(this).inflate(R.layout.row_app, favList, false);
            bindRow(row, e);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launch(e);
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showAppActions(e, true);
                    return true;
                }
            });
            favList.addView(row);
            added++;
        }
        favEmpty.setVisibility(added == 0 ? View.VISIBLE : View.GONE);
    }

    private void bindRow(View row, AppEntry e) {
        TextView label = (TextView) row.findViewById(R.id.app_label);
        ImageView icon = (ImageView) row.findViewById(R.id.app_icon);
        label.setText(promptLabel(e.label));
        if (showIcons) {
            icon.setVisibility(View.VISIBLE);
            icon.setImageDrawable(iconOf(e));
        } else {
            icon.setVisibility(View.GONE);
        }
    }

    private CharSequence promptLabel(String label) {
        SpannableString s = new SpannableString("> " + label.toUpperCase(Locale.getDefault()));
        s.setSpan(new ForegroundColorSpan(getColor(R.color.signal_green)),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private Drawable iconOf(AppEntry e) {
        Drawable d = iconCache.get(e.key());
        if (d == null) {
            try {
                d = getPackageManager().getActivityIcon(e.component());
            } catch (Exception ex) {
                try {
                    d = getPackageManager().getApplicationIcon(e.packageName);
                } catch (Exception ex2) {
                    d = null;
                }
            }
            if (d != null) {
                iconCache.put(e.key(), d);
            }
        }
        return d;
    }

    private void launch(AppEntry e) {
        if (e == null) {
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(e.component());
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
            closeDrawer();
        } catch (Exception ex) {
            // Stale entry (uninstalled between refreshes)
            refreshApps();
        }
    }

    // ---------------------------------------------------------------- drawer

    private void openDrawer() {
        if (drawer.getVisibility() == View.VISIBLE) {
            return;
        }
        search.setText("");
        adapter.filter("");
        drawer.setVisibility(View.VISIBLE);
        drawer.setTranslationY(drawer.getHeight() * 0.06f);
        drawer.setAlpha(0f);
        drawer.animate().translationY(0f).alpha(1f).setDuration(140).start();
        grid.setSelection(0);
    }

    private void closeDrawer() {
        if (drawer.getVisibility() != View.VISIBLE) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        }
        drawer.setVisibility(View.GONE);
    }

    // --------------------------------------------------------------- actions

    private void showAppActions(final AppEntry e, final boolean fromFavs) {
        if (e == null) {
            return;
        }
        final boolean pinned = Prefs.isPinned(this, e.key());
        final ArrayList<String> items = new ArrayList<String>();
        items.add("> LAUNCH");
        items.add(pinned ? "> UNPIN" : "> PIN_TO_HOME");
        if (fromFavs && pinned) {
            items.add("> MOVE_UP");
            items.add("> MOVE_DOWN");
        }
        items.add("> HIDE_FROM_DRAWER");
        items.add("> APP_INFO");
        items.add("> UNINSTALL");

        ArrayAdapter<String> dialogAdapter = new ArrayAdapter<String>(
                this, R.layout.row_action, R.id.action_label, items);

        TextView title = new MonoTextView(this);
        title.setText(e.label.toUpperCase(Locale.getDefault()));
        title.setTextColor(getColor(R.color.signal_green));
        title.setTextSize(14f);
        title.setTypeface(Fonts.get(this, Fonts.JB_BOLD));
        int pad = (int) (getResources().getDisplayMetrics().density * 18);
        title.setPadding(pad, pad, pad, pad / 2);

        new AlertDialog.Builder(this)
                .setCustomTitle(title)
                .setAdapter(dialogAdapter, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String item = items.get(which);
                        if ("> LAUNCH".equals(item)) {
                            launch(e);
                        } else if ("> PIN_TO_HOME".equals(item) || "> UNPIN".equals(item)) {
                            Prefs.togglePin(MainActivity.this, e.key());
                            renderFavs();
                        } else if ("> MOVE_UP".equals(item)) {
                            Prefs.movePin(MainActivity.this, e.key(), -1);
                            renderFavs();
                        } else if ("> MOVE_DOWN".equals(item)) {
                            Prefs.movePin(MainActivity.this, e.key(), 1);
                            renderFavs();
                        } else if ("> HIDE_FROM_DRAWER".equals(item)) {
                            Prefs.hide(MainActivity.this, e.key());
                            refreshApps();
                        } else if ("> APP_INFO".equals(item)) {
                            try {
                                startActivity(new Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + e.packageName)));
                            } catch (Exception ignored) {
                            }
                        } else if ("> UNINSTALL".equals(item)) {
                            try {
                                startActivity(new Intent(Intent.ACTION_DELETE,
                                        Uri.parse("package:" + e.packageName)));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                })
                .show();
    }

    // --------------------------------------------------------------- adapter

    private class AppsAdapter extends BaseAdapter {

        private List<AppEntry> master = new ArrayList<AppEntry>();
        private final List<AppEntry> shown = new ArrayList<AppEntry>();
        private String query = "";

        void setData(List<AppEntry> data) {
            master = data;
            applyFilter();
        }

        void filter(String q) {
            query = q == null ? "" : q.trim().toLowerCase(Locale.getDefault());
            applyFilter();
        }

        private void applyFilter() {
            shown.clear();
            if (query.length() == 0) {
                shown.addAll(master);
            } else {
                for (int i = 0; i < master.size(); i++) {
                    AppEntry e = master.get(i);
                    if (e.label.toLowerCase(Locale.getDefault()).contains(query)
                            || e.packageName.toLowerCase(Locale.US).contains(query)) {
                        shown.add(e);
                    }
                }
            }
            notifyDataSetChanged();
        }

        AppEntry get(int position) {
            return position >= 0 && position < shown.size() ? shown.get(position) : null;
        }

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.row_app, parent, false);
            }
            bindRow(row, shown.get(position));
            return row;
        }
    }
}

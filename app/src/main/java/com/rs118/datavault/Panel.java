package com.rs118.datavault;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The floating DataVault window. */
final class Panel {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private final Activity act;
    private final Vault vault;
    private final Vault.Settings settings;

    private Dialog dialog;
    private EditText nameInput;
    private LinearLayout listBox;
    private TextView listTitle;

    static void show(Activity a) {
        try {
            new Panel(a).open();
        } catch (Throwable t) {
            Toast.makeText(a, "DataVault: " + t, Toast.LENGTH_LONG).show();
        }
    }

    private Panel(Activity a) {
        act = a;
        vault = ProcessControl.vaultFor(a.getApplicationInfo());
        settings = vault.loadSettings();
    }

    // ------------------------------------------------------------------ layout

    private void open() {
        final int match = ViewGroup.LayoutParams.MATCH_PARENT;

        dialog = new Dialog(act, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout root = new FrameLayout(act);

        ScrollView scroll = new ScrollView(act);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackground(Ui.shape(act, 30, Ui.PANEL_BG, 0, 0));
        scroll.setClipToOutline(true);

        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(Ui.dp(act, 18), Ui.dp(act, 18), Ui.dp(act, 18), Ui.dp(act, 18));
        scroll.addView(c, new ViewGroup.LayoutParams(match, ViewGroup.LayoutParams.WRAP_CONTENT));

        // header
        c.addView(Ui.text(act, "DataVault", 30, Ui.WHITE));
        TextView by = Ui.text(act, "Developed by Rs118", 14, Ui.SUB);
        c.addView(by, Ui.lp(act, match, 4));

        // backups section
        LinearLayout sec = Ui.section(act);
        sec.addView(Ui.text(act, "Backups", 22, Ui.WHITE));
        TextView info = Ui.text(act, "Data directory:\n" + vault.dataRoot().getPath()
                + "\n\nBackup directory:\n" + vault.vaultDir().getPath(), 13, Ui.SUB);
        info.setLineSpacing(0, 1.1f);
        sec.addView(info, Ui.lp(act, match, 10));

        nameInput = Ui.input(act, "Backup name, e.g. Account 1");
        sec.addView(nameInput, Ui.lp(act, match, 12));

        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView create = Ui.pill(act, "Create backup");
        TextView clean = Ui.pill(act, "Clean backup");
        create.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createBackup();
            }
        });
        clean.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmCleanBackup();
            }
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dp(act, 5);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l2.leftMargin = Ui.dp(act, 5);
        btns.addView(create, l1);
        btns.addView(clean, l2);
        sec.addView(btns, Ui.lp(act, match, 10));

        TextView hint = Ui.text(act, "\"Clean backup\" closes the app, saves its data while nothing is "
                + "running, then reopens it. Safest for apps that use databases.", 12, Ui.MUTED);
        sec.addView(hint, Ui.lp(act, match, 8));
        c.addView(sec, Ui.lp(act, match, 16));

        // saved backups
        LinearLayout saved = Ui.section(act);
        listTitle = Ui.text(act, "Saved backups", 22, Ui.WHITE);
        saved.addView(listTitle);
        listBox = new LinearLayout(act);
        listBox.setOrientation(LinearLayout.VERTICAL);
        saved.addView(listBox, Ui.lp(act, match, 6));
        c.addView(saved, Ui.lp(act, match, 14));

        // options
        c.addView(Ui.toggleRow(act, "Include cache", "Also back up cache and code_cache folders.",
                settings.includeCache, new ToggleView.Listener() {
                    @Override
                    public void onChanged(boolean v) {
                        settings.includeCache = v;
                        saveSettings();
                    }
                }), Ui.lp(act, match, 14));
        c.addView(Ui.toggleRow(act, "Include device-protected data", "Also back up /data/user_de/ data of the app.",
                settings.includeDe, new ToggleView.Listener() {
                    @Override
                    public void onChanged(boolean v) {
                        settings.includeDe = v;
                        saveSettings();
                    }
                }), Ui.lp(act, match, 12));
        c.addView(Ui.toggleRow(act, "Include external data", "Also back up Android/data/" + act.getPackageName() + ".",
                settings.includeExt, new ToggleView.Listener() {
                    @Override
                    public void onChanged(boolean v) {
                        settings.includeExt = v;
                        saveSettings();
                    }
                }), Ui.lp(act, match, 12));
        c.addView(Ui.toggleRow(act, "Backup before loading", "Save the current data automatically before loading a backup.",
                settings.backupBeforeLoad, new ToggleView.Listener() {
                    @Override
                    public void onChanged(boolean v) {
                        settings.backupBeforeLoad = v;
                        saveSettings();
                    }
                }), Ui.lp(act, match, 12));

        TextView close = Ui.pill(act, "Close");
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        c.addView(close, Ui.lp(act, match, 14));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(match, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.setMargins(Ui.dp(act, 14), Ui.dp(act, 28), Ui.dp(act, 14), Ui.dp(act, 28));
        root.addView(scroll, lp);
        dialog.setContentView(root, new ViewGroup.LayoutParams(match, match));
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x66000000));
            w.setLayout(match, match);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        refreshList();
        dialog.show();
    }

    private void saveSettings() {
        try {
            vault.saveSettings(settings);
        } catch (IOException e) {
            toast("Could not save settings: " + e.getMessage());
        }
    }

    private void refreshList() {
        listBox.removeAllViews();
        List<Vault.Backup> all = vault.list();
        listTitle.setText("Saved backups (" + all.size() + ")");
        if (all.isEmpty()) {
            listBox.addView(Ui.text(act, "No backups yet. Type a name above and tap \"Create backup\".", 13, Ui.SUB));
            return;
        }
        for (Vault.Backup b : all) listBox.addView(row(b), Ui.lp(act, ViewGroup.LayoutParams.MATCH_PARENT, 8));
    }

    private View row(final Vault.Backup b) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(Ui.dp(act, 16), Ui.dp(act, 12), Ui.dp(act, 16), Ui.dp(act, 12));
        r.setBackground(Ui.pressable(act, 22, 0, Ui.STROKE));
        r.setClickable(true);

        TextView name = Ui.text(act, (b.auto ? "[auto] " : "") + b.name, 18, Ui.WHITE);
        r.addView(name);
        TextView d = Ui.text(act, date(b) + "  ·  " + Vault.formatSize(b.bytes) + "  ·  " + b.files + " files", 12, Ui.SUB);
        r.addView(d, Ui.lp(act, ViewGroup.LayoutParams.WRAP_CONTENT, 4));
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions(b);
            }
        });
        return r;
    }

    private static String date(Vault.Backup b) {
        return new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(new Date(b.created));
    }

    private static String details(Vault.Backup b) {
        StringBuilder sb = new StringBuilder();
        sb.append(new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(new Date(b.created)));
        sb.append("\n").append(Vault.formatSize(b.bytes)).append(" · ").append(b.files).append(" files");
        sb.append("\nIncludes: app data");
        if (b.parts.contains(Vault.PART_DE)) sb.append(", device-protected");
        if (b.parts.contains(Vault.PART_EXT)) sb.append(", external");
        if (b.cache) sb.append(", cache");
        if (b.skipped > 0) sb.append("\n").append(b.skipped).append(" unreadable items were skipped");
        return sb.toString();
    }

    // ------------------------------------------------------------------ actions

    private void actions(final Vault.Backup b) {
        new Card(act, b.name, details(b))
                .button("Load & restart", new Runnable() {
                    @Override
                    public void run() {
                        confirmLoad(b);
                    }
                })
                .button("Rename", new Runnable() {
                    @Override
                    public void run() {
                        rename(b);
                    }
                })
                .button("Delete", new Runnable() {
                    @Override
                    public void run() {
                        confirmDelete(b);
                    }
                }, true)
                .button("Close", null)
                .show();
    }

    private void confirmLoad(final Vault.Backup b) {
        String msg = "\"" + b.name + "\" will replace the data of this app.\n\n"
                + (settings.backupBeforeLoad
                ? "Your current data is saved as an automatic backup first.\n\n"
                : "Your current data will be lost.\n\n")
                + "The app closes and reopens by itself. If it does not reopen, just open it again.";
        new Card(act, "Load backup?", msg)
                .button("Load & restart", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            vault.queueRestore(b, settings);
                        } catch (IOException e) {
                            toast("Could not queue load: " + e.getMessage());
                            return;
                        }
                        dialog.dismiss();
                        ProcessControl.restartApp(act);
                    }
                })
                .button("Cancel", null)
                .show();
    }

    private void confirmCleanBackup() {
        new Card(act, "Clean backup", "The app will close, DataVault saves its data while nothing is running, "
                + "then the app reopens.")
                .button("Backup & restart", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            vault.queueBackup(chosenName(), settings);
                        } catch (IOException e) {
                            toast("Could not queue backup: " + e.getMessage());
                            return;
                        }
                        dialog.dismiss();
                        ProcessControl.restartApp(act);
                    }
                })
                .button("Cancel", null)
                .show();
    }

    private void rename(final Vault.Backup b) {
        final EditText e = Ui.input(act, "New name");
        e.setText(b.name);
        e.setSelection(e.getText().length());
        new Card(act, "Rename backup", null)
                .add(e, 12)
                .button("Save", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            vault.rename(b, e.getText().toString());
                        } catch (IOException ex) {
                            toast("Rename failed: " + ex.getMessage());
                        }
                        refreshList();
                    }
                })
                .button("Cancel", null)
                .show();
    }

    private void confirmDelete(final Vault.Backup b) {
        new Card(act, "Delete backup?", "\"" + b.name + "\" will be deleted permanently.")
                .button("Delete", new Runnable() {
                    @Override
                    public void run() {
                        final Card busy = new Card(act, "Deleting…", "").cancelable(false);
                        busy.show();
                        IO.execute(new Runnable() {
                            @Override
                            public void run() {
                                vault.delete(b);
                                act.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        busy.dismiss();
                                        refreshList();
                                    }
                                });
                            }
                        });
                    }
                }, true)
                .button("Cancel", null)
                .show();
    }

    private String chosenName() {
        String n = nameInput.getText().toString().trim();
        if (n.isEmpty()) n = "Backup " + new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(new Date());
        return n;
    }

    private void createBackup() {
        final String name = chosenName();
        final Card busy = new Card(act, "Creating backup…", "Starting…").cancelable(false);
        busy.show();
        IO.execute(new Runnable() {
            @Override
            public void run() {
                String error = null;
                Vault.Result res = null;
                try {
                    res = vault.create(name, settings, false, new Vault.Progress() {
                        @Override
                        public void onProgress(final String m) {
                            act.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    busy.message.setText(m);
                                }
                            });
                        }
                    });
                } catch (Throwable t) {
                    error = t.getMessage() != null ? t.getMessage() : t.toString();
                }
                final String err = error;
                final Vault.Result r = res;
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        busy.dismiss();
                        if (err != null) {
                            toast("Backup failed: " + err);
                        } else {
                            nameInput.setText("");
                            toast("Backup saved: " + r.backup.name
                                    + (r.skipped > 0 ? " (" + r.skipped + " unreadable items skipped)" : ""));
                            refreshList();
                        }
                    }
                });
            }
        });
    }

    private void toast(String m) {
        Toast.makeText(act, m, Toast.LENGTH_LONG).show();
    }
}

/*
 * Copyright (c) 2026 DNA Mobile Applications.
 * All rights reserved.
 *
 * This file is DroidBridge project code.
 * It is not part of Minecraft and does not grant rights to Minecraft,
 * Mojang, Microsoft, PojavLauncher, Zalith Launcher, or any third-party project.
 *
 * Files written entirely by DNA Mobile Applications are proprietary unless
 * a file header or separate license notice states otherwise.
 */

package ca.dnamobile.javalauncher.controls;

import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import ca.dnamobile.javalauncher.utils.FullscreenUtils;

/** Drag buttons to move; tap a button to edit/delete it. */
public final class ControlsEditorActivity extends AppCompatActivity {
    private static final String UI_PREFS = "touch_controls_editor_ui";
    private static final String KEY_MENU_X = "floating_menu_x";
    private static final String KEY_MENU_Y = "floating_menu_y";

    private TouchControlsOverlay overlay;
    private FrameLayout root;
    private LinearLayout editorPanel;
    private Button menuButton;
    private TextView globalOpacityValue;
    private SeekBar globalOpacitySlider;

    private int menuTouchSlop;
    private float menuDownRawX;
    private float menuDownRawY;
    private float menuStartX;
    private float menuStartY;
    private boolean menuDragging;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        menuTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        root = new FrameLayout(this);
        setContentView(root);

        overlay = new TouchControlsOverlay(this);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        editorPanel = new LinearLayout(this);
        editorPanel.setOrientation(LinearLayout.VERTICAL);
        editorPanel.setGravity(Gravity.CENTER_VERTICAL);
        editorPanel.setPadding(dp(10), dp(10), dp(10), dp(10));
        editorPanel.setBackground(makePanelBackground());
        editorPanel.setVisibility(View.GONE);
        root.addView(editorPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START
        ));

        buildEditorPanel();

        menuButton = new Button(this);
        menuButton.setText("⚙");
        menuButton.setTextSize(22f);
        menuButton.setAllCaps(false);
        menuButton.setAlpha(0.76f);
        menuButton.setBackground(makeGearBackground());
        menuButton.setOnTouchListener(this::handleMenuButtonTouch);
        root.addView(menuButton, new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP | Gravity.START));

        editorPanel.bringToFront();
        menuButton.bringToFront();

        root.post(() -> {
            restoreMenuButtonPosition();
            if (overlay != null) {
                overlay.setEditMode(true);
                overlay.loadSelectedLayout();
            }
            enableImmersiveSafely();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableImmersiveSafely();
    }

    private void buildEditorPanel() {
        TextView header = new TextView(this);
        header.setText("Touch editor");
        header.setTextSize(14f);
        header.setTextColor(0xFFE0E0E0);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 0, 0, dp(6));
        editorPanel.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addGlobalOpacityControls();

        LinearLayout rowOne = panelRow();
        LinearLayout rowTwo = panelRow();
        LinearLayout rowThree = panelRow();
        LinearLayout rowFour = panelRow();

        Button addKey = panelButton("Add Key");
        addKey.setOnClickListener(view -> {
            overlay.addControl(TouchControlData.key("Key", 32, 120, 120, 72, 52));
            Toast.makeText(this, "Tap the new button to edit it.", Toast.LENGTH_SHORT).show();
        });
        rowOne.addView(addKey, panelButtonParams());

        Button addMouse = panelButton("Add Mouse");
        addMouse.setOnClickListener(view -> overlay.addControl(TouchControlData.mouse("Mouse", 0, 220, 120)));
        rowOne.addView(addMouse, panelButtonParams());

        Button addJoystick = panelButton("Add Stick");
        addJoystick.setOnClickListener(view -> {
            overlay.addControl(TouchControlData.joystick("Joystick", 48, 330, 128, 128));
            Toast.makeText(this, "Added joystick. Long-press it to resize or move it.", Toast.LENGTH_SHORT).show();
        });
        rowOne.addView(addJoystick, panelButtonParams());

        Button snap = panelButton("");
        updateSnapButtonText(snap);
        snap.setOnClickListener(view -> {
            boolean enabled = !ControlsPreferences.isSnapControlsEnabled(this);
            ControlsPreferences.setSnapControlsEnabled(this, enabled);
            updateSnapButtonText(snap);
            Toast.makeText(this, enabled ? "Snap enabled." : "Snap disabled.", Toast.LENGTH_SHORT).show();
        });
        rowTwo.addView(snap, panelButtonParams());

        Button mouseToggle = panelButton("");
        updateMouseButtonText(mouseToggle);
        mouseToggle.setOnClickListener(view -> {
            boolean enabled = !ControlsPreferences.isVirtualMouseEnabled(this);
            ControlsPreferences.setVirtualMouseEnabled(this, enabled);
            updateMouseButtonText(mouseToggle);
            Toast.makeText(this, enabled ? "Virtual cursor shown." : "Virtual cursor hidden.", Toast.LENGTH_SHORT).show();
        });
        rowTwo.addView(mouseToggle, panelButtonParams());

        Button save = panelButton("Save");
        save.setOnClickListener(view -> {
            overlay.saveLayout();
            Toast.makeText(this, "Touch controls saved.", Toast.LENGTH_SHORT).show();
        });
        rowTwo.addView(save, panelButtonParams());

        Button undo = panelButton("Undo");
        undo.setOnClickListener(view -> {
            if (overlay.undoLastChange()) {
                Toast.makeText(this, "Undid last touch edit.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Nothing to undo.", Toast.LENGTH_SHORT).show();
            }
        });
        rowThree.addView(undo, panelButtonParams());

        Button redo = panelButton("Redo");
        redo.setOnClickListener(view -> {
            if (overlay.redoLastChange()) {
                Toast.makeText(this, "Redid touch edit.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Nothing to redo.", Toast.LENGTH_SHORT).show();
            }
        });
        rowThree.addView(redo, panelButtonParams());

        Button hide = panelButton("Hide panel");
        hide.setOnClickListener(view -> setPanelVisible(false));
        rowFour.addView(hide, panelButtonParams());

        Button close = panelButton("Close");
        close.setOnClickListener(view -> finish());
        rowFour.addView(close, panelButtonParams());

        editorPanel.addView(rowOne);
        editorPanel.addView(rowTwo);
        editorPanel.addView(rowThree);
        editorPanel.addView(rowFour);
    }


    private void addGlobalOpacityControls() {
        TextView title = new TextView(this);
        title.setText("All button opacity");
        title.setTextSize(12f);
        title.setTextColor(0xFFE0E0E0);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(4), 0, 0);
        editorPanel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        globalOpacityValue = new TextView(this);
        globalOpacityValue.setTextSize(11f);
        globalOpacityValue.setTextColor(0xFFBDBDBD);
        globalOpacityValue.setGravity(Gravity.CENTER);
        globalOpacityValue.setPadding(0, 0, 0, dp(2));
        editorPanel.addView(globalOpacityValue, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        globalOpacitySlider = new SeekBar(this);
        globalOpacitySlider.setMax(100);
        int progress = Math.round(ControlsPreferences.getGlobalOpacity(this) * 100f);
        globalOpacitySlider.setProgress(Math.max(0, Math.min(100, progress)));
        updateGlobalOpacityLabel(globalOpacitySlider.getProgress());
        globalOpacitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int safeProgress = Math.max(0, Math.min(100, progress));
                updateGlobalOpacityLabel(safeProgress);
                if (fromUser) {
                    ControlsPreferences.setGlobalOpacity(ControlsEditorActivity.this, safeProgress / 100f);
                    if (overlay != null) overlay.refreshButtonVisuals();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int safeProgress = Math.max(0, Math.min(100, seekBar.getProgress()));
                ControlsPreferences.setGlobalOpacity(ControlsEditorActivity.this, safeProgress / 100f);
                updateGlobalOpacityLabel(safeProgress);
                if (overlay != null) overlay.refreshButtonVisuals();
            }
        });
        editorPanel.addView(globalOpacitySlider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(38)
        ));
    }

    private void updateGlobalOpacityLabel(int progress) {
        if (globalOpacityValue != null) {
            globalOpacityValue.setText("Opacity: " + Math.max(0, Math.min(100, progress)) + "%");
        }
    }

    private boolean handleMenuButtonTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                menuDownRawX = event.getRawX();
                menuDownRawY = event.getRawY();
                menuStartX = menuButton.getX();
                menuStartY = menuButton.getY();
                menuDragging = false;
                requestParentDisallowIntercept(view, true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - menuDownRawX;
                float dy = event.getRawY() - menuDownRawY;
                if (!menuDragging && ((dx * dx) + (dy * dy)) > (menuTouchSlop * menuTouchSlop)) {
                    menuDragging = true;
                    setPanelVisible(false);
                }
                if (menuDragging) moveMenuButton(menuStartX + dx, menuStartY + dy);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                requestParentDisallowIntercept(view, false);
                if (menuDragging) saveMenuButtonPosition();
                else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    view.performClick();
                    setPanelVisible(editorPanel.getVisibility() != View.VISIBLE);
                }
                menuDragging = false;
                return true;
            default:
                return true;
        }
    }

    private void requestParentDisallowIntercept(View view, boolean disallow) {
        ViewParent parent = view == null ? null : view.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private void setPanelVisible(boolean visible) {
        editorPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        menuButton.setAlpha(visible ? 1.0f : 0.76f);
        if (visible) editorPanel.post(this::positionPanelNearMenuButton);
    }

    private void restoreMenuButtonPosition() {
        SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        float defaultX = Math.max(dp(4), (root.getWidth() - menuButton.getWidth()) / 2f);
        float defaultY = Math.max(dp(4), (root.getHeight() - menuButton.getHeight()) / 2f);
        boolean hasSavedPosition = prefs.contains(KEY_MENU_X) && prefs.contains(KEY_MENU_Y);
        float x = hasSavedPosition ? prefs.getFloat(KEY_MENU_X, defaultX) : defaultX;
        float y = hasSavedPosition ? prefs.getFloat(KEY_MENU_Y, defaultY) : defaultY;
        moveMenuButton(x, y);
    }

    private void saveMenuButtonPosition() {
        getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit()
                .putFloat(KEY_MENU_X, menuButton.getX())
                .putFloat(KEY_MENU_Y, menuButton.getY())
                .apply();
    }

    private void moveMenuButton(float x, float y) {
        float maxX = Math.max(0f, root.getWidth() - menuButton.getWidth() - dp(4));
        float maxY = Math.max(0f, root.getHeight() - menuButton.getHeight() - dp(4));
        menuButton.setX(clamp(x, dp(4), maxX));
        menuButton.setY(clamp(y, dp(4), maxY));
        if (editorPanel.getVisibility() == View.VISIBLE) positionPanelNearMenuButton();
    }

    private void positionPanelNearMenuButton() {
        if (root.getWidth() <= 0 || root.getHeight() <= 0) return;
        editorPanel.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.AT_MOST));
        int panelWidth = Math.max(1, editorPanel.getMeasuredWidth());
        int panelHeight = Math.max(1, editorPanel.getMeasuredHeight());
        float spacing = dp(8);
        boolean openRight = menuButton.getX() + menuButton.getWidth() / 2f < root.getWidth() / 2f;
        float x = openRight ? menuButton.getX() + menuButton.getWidth() + spacing : menuButton.getX() - panelWidth - spacing;
        float y = menuButton.getY();
        if (x < dp(4) || x + panelWidth > root.getWidth() - dp(4)) {
            x = clamp(menuButton.getX(), dp(4), Math.max(dp(4), root.getWidth() - panelWidth - dp(4)));
            y = menuButton.getY() > root.getHeight() / 2f ? menuButton.getY() - panelHeight - spacing : menuButton.getY() + menuButton.getHeight() + spacing;
        }
        editorPanel.setX(clamp(x, dp(4), Math.max(dp(4), root.getWidth() - panelWidth - dp(4))));
        editorPanel.setY(clamp(y, dp(4), Math.max(dp(4), root.getHeight() - panelHeight - dp(4))));
    }

    private LinearLayout panelRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));
        return row;
    }

    private Button panelButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private LinearLayout.LayoutParams panelButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(112), dp(42));
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private GradientDrawable makePanelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xDD111111);
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), 0x55FFFFFF);
        return drawable;
    }

    private GradientDrawable makeGearBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xDD202124);
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setStroke(dp(1), 0x66FFFFFF);
        return drawable;
    }

    private void enableImmersiveSafely() {
        try { FullscreenUtils.enableImmersive(this); } catch (Throwable ignored) { }
    }

    private void updateSnapButtonText(Button button) {
        button.setText(ControlsPreferences.isSnapControlsEnabled(this) ? "Snap: ON" : "Snap: OFF");
    }

    private void updateMouseButtonText(Button button) {
        button.setText(ControlsPreferences.isVirtualMouseEnabled(this) ? "Cursor: ON" : "Cursor: OFF");
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

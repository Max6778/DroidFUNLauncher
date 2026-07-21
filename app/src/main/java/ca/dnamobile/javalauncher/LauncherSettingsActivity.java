⁶/*
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

package ca.dnamobile.javalauncher;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import ca.dnamobile.javalauncher.auth.MicrosoftAuthConfigPersonal;
import ca.dnamobile.javalauncher.BuildConfig;
import ca.dnamobile.javalauncher.auth.MicrosoftAuthManagerPersonal;
import ca.dnamobile.javalauncher.controls.ControlsActivity;
import ca.dnamobile.javalauncher.controls.ControlsPreferences;
import ca.dnamobile.javalauncher.data.AccountStore;
import ca.dnamobile.javalauncher.databinding.ActivityLauncherSettingsBinding;
import ca.dnamobile.javalauncher.feature.log.Logging;
import ca.dnamobile.javalauncher.input.GamepadMappingDialog;
import ca.dnamobile.javalauncher.input.GamepadMappingStore;
import ca.dnamobile.javalauncher.legal.LegalLinks;
import ca.dnamobile.javalauncher.logs.LauncherLogManager;
import ca.dnamobile.javalauncher.modcompat.AndroidMicrophonePermission;
import ca.dnamobile.javalauncher.notifications.LauncherNotificationPermissionHelper;
import ca.dnamobile.javalauncher.renderer.Driver;
import ca.dnamobile.javalauncher.renderer.DriverPluginManager;
import ca.dnamobile.javalauncher.renderer.MobileGluesConfigHelper;
import ca.dnamobile.javalauncher.renderer.RendererInterface;
import ca.dnamobile.javalauncher.renderer.RendererPluginManager;
import ca.dnamobile.javalauncher.renderer.Renderers;
import ca.dnamobile.javalauncher.settings.GameOverlayPreferences;
import ca.dnamobile.javalauncher.settings.LauncherPreferences;
import ca.dnamobile.javalauncher.settings.MemoryAllocationUtils;
import ca.dnamobile.javalauncher.skin.CustomSkinStore;
import ca.dnamobile.javalauncher.skin.MicrosoftSkinUploader;
import ca.dnamobile.javalauncher.skin.PlayerHeadLoader;
import ca.dnamobile.javalauncher.skin.SkinModelType;
import ca.dnamobile.javalauncher.update.LauncherUpdateDialogs;
import ca.dnamobile.javalauncher.update.LauncherUpdatePreferences;
import ca.dnamobile.javalauncher.utils.FullscreenUtils;
import ca.dnamobile.javalauncher.utils.path.PathManager;

public final class LauncherSettingsActivity extends AppCompatActivity {
    private static final String SETTINGS_DEFAULTS_PREFS = "launcher_settings_defaults";
    private static final String SETTINGS_DEFAULTS_APPLIED_KEY = "settings_defaults_applied_2026_04_instances";

    private ActivityLauncherSettingsBinding binding;
    private AccountStore accountStore;
    private MicrosoftAuthManagerPersonal authManager;
    private CustomSkinStore customSkinStore;
    private ActivityResultLauncher<Intent> customSkinPickerLauncher;
    private ActivityResultLauncher<Intent> microsoftSkinPickerLauncher;
    private ActivityResultLauncher<Intent> offlineSkinPickerLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ActivityResultLauncher<String> microphonePermissionLauncher;
    private ActivityResultLauncher<Intent> mobileGluesFolderPickerLauncher;
    private Uri pendingOfflineSkinUri;
    private ImageView pendingOfflineSkinPreview;
    private TextView pendingOfflineSkinLabel;
    private AlertDialog offlineAccountsDialog;
    private final List<RendererInterface> availableRenderers = new ArrayList<>();
    private final List<Driver> availableDrivers = new ArrayList<>();
    private boolean rendererSpinnerReady;
    private boolean driverSpinnerReady;
    private TextView textHardwareMouseDpiScale;
    private SeekBar sliderHardwareMouseDpiScale;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PathManager.initContextConstants(this);
        binding = ActivityLauncherSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        FullscreenUtils.enableImmersive(this);

        binding.buttonSettingsBack.setOnClickListener(view -> finish());
        applySettingsDefaultsOnce();
        setupSettingsSectionTabs();
        registerSkinPickerLauncher();
        registerMicrosoftSkinPickerLauncher();
        registerOfflineSkinPickerLauncher();
        registerNotificationPermissionLauncher();
        registerMicrophonePermissionLauncher();
        registerMobileGluesFolderPickerLauncher();
        setupAccountUi();
        setupInstanceSettings();
        setupRendererSettings();
        setupRenderSurfaceSettings();
        setupControllerSettings();
        setupLauncherSettings();
        setupPrivacyPolicySettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        FullscreenUtils.enableImmersive(this);
        if (binding != null) {
            RendererInterface selectedRenderer = getSelectedRendererFromSpinner();
            updateMobileGluesConfigSummary(selectedRenderer);
            if (DriverPluginManager.isVulkanZinkRenderer(selectedRenderer)) {
                DriverPluginManager.reload(this);
                updateVulkanDriverSettings(selectedRenderer);
            }
            refreshControllerSettingsValues();
            updateInstallNotificationSettingsUi();
            updateSimpleVoiceChatPermissionUi();
            refreshAccountUiFromStore();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            FullscreenUtils.enableImmersive(this);
        }
    }

    @Override
    protected void onDestroy() {
        if (authManager != null && !isChangingConfigurations()) {
            authManager.dispose();
        }
        super.onDestroy();
    }

    private void refreshAccountUiFromStore() {
        if (accountStore == null || binding == null) return;

        try {
            AccountStore.Account account = accountStore.load();
            updateAccountStatus(account);
            updateSkinUi(account);
            updateChangeMicrosoftSkinButtonState(account);
        } catch (Throwable throwable) {
            Logging.e("LauncherSettings", "Unable to refresh account UI", throwable);
        }
    }

    private void applySettingsDefaultsOnce() {
        android.content.SharedPreferences preferences = getSharedPreferences(SETTINGS_DEFAULTS_PREFS, MODE_PRIVATE);
        if (preferences.getBoolean(SETTINGS_DEFAULTS_APPLIED_KEY, false)) return;

        // Defaults requested for the settings screen:
        // - Shared installs hidden/off by default.
        // - Keep inherited/base Minecraft versions on by default.
        LauncherPreferences.setShowSharedInstalls(this, false);
        LauncherPreferences.setRemoveInheritedVanillaAfterLoaderInstall(this, false);
        preferences.edit().putBoolean(SETTINGS_DEFAULTS_APPLIED_KEY, true).apply();
    }

    private void setupSettingsSectionTabs() {
        binding.settingsSectionTabs.removeAllTabs();
        addSettingsSectionTab(R.string.settings_account_title);
        addSettingsSectionTab(R.string.renderer_settings_title);
        addSettingsSectionTab(R.string.controller_settings_title);
        addSettingsSectionTab(R.string.settings_launcher_title);
        addSettingsSectionTab(R.string.settings_instance_title);
        addSettingsSectionTab("Privacy Policy");




        binding.settingsSectionTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                scrollToSettingsSection(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                scrollToSettingsSection(tab.getPosition());
            }
        });
    }

    private void addSettingsSectionTab(int titleResId) {
        TabLayout.Tab tab = binding.settingsSectionTabs.newTab();
        tab.setText(titleResId);
        binding.settingsSectionTabs.addTab(tab);
    }

    private void addSettingsSectionTab(@NonNull String title) {
        TabLayout.Tab tab = binding.settingsSectionTabs.newTab();
        tab.setText(title);
        binding.settingsSectionTabs.addTab(tab);
    }

    private void scrollToSettingsSection(int position) {
        View target;

        switch (position) {
            case 0:
                target = binding.cardAccountSettings;
                break;
            case 1:
                target = binding.cardRendererSettings;

                break;
            case 2:
                target = binding.cardControllerSettings;

                break;
            case 3:
                target = binding.cardLauncherSettings;
                break;
            case 4:
                target = binding.cardInstanceSettings;
                break;
            case 5:
                target = binding.cardPrivacyPolicySettings;
                break;
            default:
                return;
        }

        binding.settingsScrollView.post(() ->
                binding.settingsScrollView.smoothScrollTo(0, Math.max(0, target.getTop() - dp(8)))
        );
    }

    private void setupAccountUi() {
        try {
            accountStore = new AccountStore(this);
            customSkinStore = new CustomSkinStore(this);
            authManager = new MicrosoftAuthManagerPersonal(this, accountStore);
            authManager.setListener(new MicrosoftAuthManagerPersonal.Listener() {
                @Override
                public void onSignedIn(@NonNull AccountStore.Account account) {
                    updateAccountStatus(account);
                    updateSkinUi(account);
                    updateChangeMicrosoftSkinButtonState(account);
                    binding.buttonRefreshMicrosoftSkin.setEnabled(true);
                }

                @Override
                public void onError(@NonNull String message) {
                    binding.textAccountStatus.setText(message);
                    binding.buttonRefreshMicrosoftSkin.setEnabled(true);
                    Toast.makeText(LauncherSettingsActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });

            AccountStore.Account account = accountStore.load();
            updateAccountStatus(account);
            updateSkinUi(account);
        } catch (Throwable throwable) {
            Logging.e("LauncherSettings", "Microsoft account UI initialization failed", throwable);
            binding.textAccountStatus.setText(R.string.status_signed_out);
            binding.buttonSignIn.setEnabled(false);
            binding.buttonSignOut.setEnabled(false);
            binding.buttonManageOfflineAccounts.setEnabled(false);
            binding.buttonUseMicrosoftAccount.setEnabled(false);
            binding.buttonRefreshMicrosoftSkin.setEnabled(false);
        }

        setupChangeMicrosoftSkinButton();

        binding.buttonSignIn.setOnClickListener(view -> {
            if (authManager == null) return;
            if (!MicrosoftAuthConfigPersonal.isConfigured()) {
                binding.textAccountStatus.setText(R.string.msg_configure_client_id);
                return;
            }
            authManager.signIn();
        });

        binding.buttonSignOut.setOnClickListener(view -> showSignOutConfirmationDialog());

        binding.buttonUseMicrosoftAccount.setOnClickListener(view -> useRememberedMicrosoftAccount());
        binding.buttonManageOfflineAccounts.setOnClickListener(view -> showOfflineAccountsDialog());
        binding.buttonRefreshMicrosoftSkin.setOnClickListener(view -> refreshMicrosoftAccountAndSkin(true));
        updateChangeMicrosoftSkinButtonState(accountStore != null ? accountStore.load() : null);
    }

    private void setupChangeMicrosoftSkinButton() {
        if (binding == null) return;

        binding.buttonChangeMicrosoftSkin.setOnClickListener(view -> showChangeMicrosoftSkinDialog());
    }

    private void showSignOutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sign_out_confirm_title)
                .setMessage(R.string.sign_out_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.button_sign_out, (dialog, which) -> performMicrosoftSignOut())
                .show();
    }

    private void performMicrosoftSignOut() {
        if (authManager == null || accountStore == null) return;

        authManager.signOut();

        AccountStore.Account account = accountStore.load();
        updateAccountStatus(account);
        updateSkinUi(account);

        if (binding.buttonRefreshMicrosoftSkin != null) {
            binding.buttonRefreshMicrosoftSkin.setEnabled(false);
        }
        updateChangeMicrosoftSkinButtonState(account);

        Toast.makeText(this, R.string.msg_sign_out_success, Toast.LENGTH_SHORT).show();
    }

    private void registerSkinPickerLauncher() {
        customSkinPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> { }
        );
    }

    private void registerMicrosoftSkinPickerLauncher() {
        microsoftSkinPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                    Uri uri = result.getData().getData();
                    if (uri == null) return;
                    prepareMicrosoftSkinUpload(uri);
                }
        );
    }

    private void registerOfflineSkinPickerLauncher() {
        offlineSkinPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                    Uri uri = result.getData().getData();
                    if (uri == null) return;
                    pendingOfflineSkinUri = uri;
                    if (pendingOfflineSkinPreview != null) {
                        updatePendingOfflineSkinPreview(uri);
                    }
                    if (pendingOfflineSkinLabel != null) {
                        pendingOfflineSkinLabel.setText(R.string.offline_account_skin_selected);
                    }
                }
        );
    }

    private void openCustomSkinPicker() {
        // Kept for old callers. Custom skins are now managed per offline profile.
        showOfflineAccountsDialog();
    }

    private void handleCustomSkinResult(@NonNull Uri uri) {
        // Kept for old callers. Custom skins are now managed per offline profile.
        pendingOfflineSkinUri = uri;
    }

    private void updateSkinUi(@Nullable AccountStore.Account account) {
        boolean offlineUnlocked = accountStore != null && (BuildConfig.DEBUG || accountStore.hasMicrosoftLoginCompletedOnce());
        boolean activeOfflineSkin = account != null && account.isOfflineAccount() && account.hasOfflineSkin();
        boolean microsoftSkin = account != null && account.isMicrosoftAccount() && !isNullOrBlank(account.skinUrl);
        boolean rememberedMicrosoft = accountStore != null && accountStore.hasStoredMicrosoftAccount();

        if (activeOfflineSkin) {
            binding.textSkinStatus.setText(getString(R.string.offline_account_skin_active, account.getBestDisplayName()));
        } else if (microsoftSkin) {
            binding.textSkinStatus.setText(R.string.custom_skin_status_microsoft);
        } else if (rememberedMicrosoft) {
            binding.textSkinStatus.setText(R.string.microsoft_skin_needs_refresh);
        } else if (!offlineUnlocked) {
            binding.textSkinStatus.setText(R.string.custom_skin_status_locked);
        } else {
            binding.textSkinStatus.setText(R.string.custom_skin_status_none);
        }

        PlayerHeadLoader.loadInto(this, binding.imagePlayerHead, account, null);
        updateChangeMicrosoftSkinButtonState(account);
    }

    private void setupInstanceSettings() {
        binding.textFolder.setText(getString(R.string.launcher_folder_value, PathManager.DIR_MINECRAFT_HOME));

        boolean showSharedInstalls = LauncherPreferences.isShowSharedInstalls(this);
        binding.switchShowSharedInstalls.setChecked(showSharedInstalls);
        updateSharedInstallsSwitchText(showSharedInstalls);
        binding.switchShowSharedInstalls.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LauncherPreferences.setShowSharedInstalls(this, isChecked);
            updateSharedInstallsSwitchText(isChecked);
        });

        // Checked = remove the inherited/base Minecraft version after the loader profile is flattened.
        // Unchecked = keep/install the inherited/base version.
        boolean removeInheritedVanilla = LauncherPreferences.isRemoveInheritedVanillaAfterLoaderInstall(this);
        binding.switchRemoveInheritedVanilla.setChecked(removeInheritedVanilla);
        updateRemoveInheritedVanillaSwitchText(removeInheritedVanilla);
        binding.switchRemoveInheritedVanilla.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LauncherPreferences.setRemoveInheritedVanillaAfterLoaderInstall(this, isChecked);
            updateRemoveInheritedVanillaSwitchText(isChecked);
        });
    }

    private void setupRendererSettings() {
        binding.spinnerRenderer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (!rendererSpinnerReady || position < 0 || position >= availableRenderers.size()) return;
                RendererInterface renderer = availableRenderers.get(position);
                LauncherPreferences.setSelectedRendererIdentifier(LauncherSettingsActivity.this, renderer.getUniqueIdentifier());
                Renderers.setCurrentRenderer(LauncherSettingsActivity.this, renderer.getUniqueIdentifier(), true);
                updateRendererDescription(renderer);
                updateRendererPluginButtons(renderer);
                updateVulkanDriverSettings(renderer);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        binding.spinnerVulkanDriver.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (!driverSpinnerReady || position < 0 || position >= availableDrivers.size()) return;
                Driver driver = availableDrivers.get(position);
                LauncherPreferences.setSelectedVulkanDriverName(LauncherSettingsActivity.this, driver.getName());
                updateVulkanDriverDescription(driver);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        binding.buttonImportRendererPlugin.setOnClickListener(view -> openSelectedRendererPluginSettings());
        binding.buttonGrantRendererStorageAccess.setOnClickListener(view -> openJavaLauncherStorageAccessSettings());
        binding.buttonClearRendererPluginCache.setOnClickListener(view -> clearRendererPluginCache());
        binding.buttonRefreshRenderers.setOnClickListener(view -> {
            Renderers.reload(this);
            DriverPluginManager.reload(this);
            refreshRendererList();
        });

        boolean useSystemVulkanDriver = LauncherPreferences.isUseSystemVulkanDriver(this);
        binding.switchUseSystemVulkanDriver.setChecked(useSystemVulkanDriver);
        updateSystemVulkanDriverSwitchText(useSystemVulkanDriver);
        binding.switchUseSystemVulkanDriver.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LauncherPreferences.setUseSystemVulkanDriver(this, isChecked);
            updateSystemVulkanDriverSwitchText(isChecked);
            updateVulkanDriverSettings(getSelectedRendererFromSpinner());
        });

        boolean useOpenGl26Plus = LauncherPreferences.isUseOpenGlForMinecraft26Plus(this);
        binding.switchUseOpenGlFor26Plus.setChecked(useOpenGl26Plus);
        updateOpenGl26PlusSwitchText(useOpenGl26Plus);
        binding.switchUseOpenGlFor26Plus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LauncherPreferences.setUseOpenGlForMinecraft26Plus(this, isChecked);
            updateOpenGl26PlusSwitchText(isChecked);
        });

        Renderers.reload(this);
        refreshRendererList();
    }

    private void refreshRendererList() {
        rendererSpinnerReady = false;
        availableRenderers.clear();
        availableRenderers.addAll(Renderers.getCompatibleRenderers(this));

        ArrayList<String> names = new ArrayList<>();
        for (RendererInterface renderer : availableRenderers) {
            names.add(renderer.getRendererName() + (renderer.isExternalPlugin() ? "  •  Plugin" : ""));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerRenderer.setAdapter(adapter);

        if (availableRenderers.isEmpty()) {
            binding.textRendererDescription.setText(R.string.renderer_none_found);
            updateRendererPluginButtons(null);
            updateMobileGluesConfigSummary(null);
            updateVulkanDriverSettings(null);
            return;
        }

        int selectedIndex = Renderers.indexOfRenderer(availableRenderers, LauncherPreferences.getSelectedRendererIdentifier(this));
        binding.spinnerRenderer.setSelection(selectedIndex, false);
        updateRendererDescription(availableRenderers.get(selectedIndex));
        updateRendererPluginButtons(availableRenderers.get(selectedIndex));
        updateVulkanDriverSettings(availableRenderers.get(selectedIndex));
        rendererSpinnerReady = true;
    }

    private void updateVulkanDriverSettings(@Nullable RendererInterface renderer) {
        boolean show = DriverPluginManager.isVulkanZinkRenderer(renderer)
                && !LauncherPreferences.isUseSystemVulkanDriver(this);
        binding.layoutVulkanDriverSettings.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            driverSpinnerReady = false;
            availableDrivers.clear();
            binding.spinnerVulkanDriver.setAdapter(null);
            binding.textVulkanDriverDescription.setText("");
            return;
        }

        refreshVulkanDriverList();
    }

    private void refreshVulkanDriverList() {
        driverSpinnerReady = false;
        availableDrivers.clear();
        availableDrivers.addAll(DriverPluginManager.getDrivers(this));

        ArrayList<String> names = new ArrayList<>();
        for (Driver driver : availableDrivers) {
            names.add(driver.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerVulkanDriver.setAdapter(adapter);

        if (availableDrivers.isEmpty()) {
            binding.textVulkanDriverDescription.setText("");
            return;
        }

        int selectedIndex = DriverPluginManager.indexOfDriver(this, LauncherPreferences.getSelectedVulkanDriverName(this));
        binding.spinnerVulkanDriver.setSelection(selectedIndex, false);
        updateVulkanDriverDescription(availableDrivers.get(selectedIndex));
        driverSpinnerReady = true;
    }

    private void updateVulkanDriverDescription(@NonNull Driver driver) {
        String description = driver.getDescription();
        if (description == null || description.trim().isEmpty()) {
            description = "Uses the selected Vulkan driver for Vulkan/Zink rendering.";
        }

        binding.textVulkanDriverDescription.setText(getString(
                R.string.vulkan_driver_description_value,
                driver.getName(),
                description
        ));
    }

    private void updateRendererDescription(@NonNull RendererInterface renderer) {
        binding.textRendererDescription.setText(buildFriendlyRendererDescription(renderer));
        updateMobileGluesConfigSummary(renderer);
    }

    @NonNull
    private String buildFriendlyRendererDescription(@NonNull RendererInterface renderer) {
        String name = renderer.getRendererName();
        String lookup = (
                renderer.getRendererName() + " "
                        + renderer.getRendererId() + " "
                        + renderer.getUniqueIdentifier() + " "
                        + renderer.getRendererLibrary()
        ).toLowerCase();

        if (lookup.contains("mobileglues") || lookup.contains("mobile glues")) {
            return name + "\nRecommended for most Android devices. Good balance of compatibility and performance for modern Minecraft versions.";
        }

        if (lookup.contains("vulkan") || lookup.contains("zink")) {
            return name + "\nUses Vulkan/Zink rendering. Best for devices with strong Vulkan support, and useful for newer Minecraft versions or Vulkan-focused testing.";
        }

        if (lookup.contains("gl4es") || lookup.contains("opengles")) {
            return name + "\nClassic OpenGL ES compatibility renderer. Useful for older Minecraft versions or devices that do not work well with Vulkan.";
        }

        if (lookup.contains("virgl")) {
            return name + "\nCompatibility renderer for specific devices and setups. Try this if the recommended renderer does not work correctly.";
        }

        String description = renderer.getRendererDescription();
        if (description != null && !description.trim().isEmpty()) {
            return name + "\n" + description.trim();
        }

        return name + "\nRuns Minecraft using this renderer.";
    }

    private void updateMobileGluesConfigSummary(@Nullable RendererInterface renderer) {
        boolean mobileGlues = MobileGluesConfigHelper.isMobileGluesRenderer(renderer);

        if (!mobileGlues) {
            binding.textRendererPluginConfig.setText("");
            binding.textRendererPluginConfig.setVisibility(View.GONE);
            binding.buttonGrantRendererStorageAccess.setVisibility(View.GONE);
            return;
        }

        binding.textRendererPluginConfig.setText(MobileGluesConfigHelper.buildSettingsSummary(this, renderer));
        binding.textRendererPluginConfig.setVisibility(View.VISIBLE);

        boolean hasAccess = MobileGluesConfigHelper.hasStorageAccess(this);
        binding.buttonGrantRendererStorageAccess.setVisibility(View.VISIBLE);
        binding.buttonGrantRendererStorageAccess.setEnabled(true);
        binding.buttonGrantRendererStorageAccess.setText(hasAccess
                ? "Choose MobileGlues folder again"
                : "Choose MobileGlues folder");
    }

    private void updateRendererPluginButtons(@Nullable RendererInterface renderer) {
        boolean externalPlugin = renderer != null && renderer.isExternalPlugin();
        binding.buttonImportRendererPlugin.setEnabled(externalPlugin);
        binding.buttonClearRendererPluginCache.setEnabled(RendererPluginManager.hasImportedOrCachedRendererPlugins(this));
    }

    private void openSelectedRendererPluginSettings() {
        RendererInterface renderer = getSelectedRendererFromSpinner();
        if (renderer == null || !renderer.isExternalPlugin()) {
            return;
        }

        RendererPluginManager.openPluginApp(this, renderer);
    }

    private void openJavaLauncherStorageAccessSettings() {
        if (mobileGluesFolderPickerLauncher == null) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        mobileGluesFolderPickerLauncher.launch(intent);
    }

    @Nullable
    private RendererInterface getSelectedRendererFromSpinner() {
        int position = binding.spinnerRenderer.getSelectedItemPosition();
        if (position < 0 || position >= availableRenderers.size()) return null;
        return availableRenderers.get(position);
    }

    private void clearRendererPluginCache() {
        RendererPluginManager.clearImportedAndCachedRendererPlugins(this);
        Renderers.reload(this);
        refreshRendererList();
    }

    private void setupRenderSurfaceSettings() {
        boolean useNativeSurfaceView = LauncherPreferences.isUseNativeSurfaceView(this);
        binding.switchUseNativeSurface.setChecked(useNativeSurfaceView);
        updateRenderSurfaceSwitchText(useNativeSurfaceView);
        binding.switchUseNativeSurface.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LauncherPreferences.setUseNativeSurfaceView(this, isChecked);
            updateRenderSurfaceSwitchText(isChecked);
        });

        setupGameDisplaySettings();
    }

    private void setupGameDisplaySettings() {
        int currentScale = LauncherPreferences.getGameResolutionScalePercent(this);
        binding.sliderGameResolutionScale.setMax(
                LauncherPreferences.MAX_GAME_RESOLUTION_SCALE_PERCENT
                        - LauncherPreferences.MIN_GAME_RESOLUTION_SCALE_PERCENT
        );
        updateResolutionScaleUi(currentScale);

        binding.sliderGameResolutionScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int percent = LauncherPreferences.MIN_GAME_RESOLUTION_SCALE_PERCENT + progress;
                LauncherPreferences.setGameResolutionScalePercent(LauncherSettingsActivity.this, percent);
                updateResolutionScaleText(percent);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int percent = LauncherPreferences.MIN_GAME_RESOLUTION_SCALE_PERCENT + seekBar.getProgress();
                percent = LauncherPreferences.clampGameResolutionScalePercent(percent);
                LauncherPreferences.setGameResolutionScalePercent(LauncherSettingsActivity.this, percent);
                updateResolutionScaleUi(percent);
            }
        });

        binding.textGameResolutionScale.setOnClickListener(view -> openResolutionScaleInputDialog());

        boolean forceFullscreen = LauncherPreferences.isForceFullscreenMode(this);
        binding.switchForceFullscreenMode.setChecked(forceFullscreen);
        updateForceFullscreenSwitchText(forceFullscreen);
        binding.switchForceFullscreenMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LauncherPreferences.setForceFullscreenMode(this, isChecked);
            updateForceFullscreenSwitchText(isChecked);
        });

        boolean avoidRoundedCorners = LauncherPreferences.isAvoidRoundedDisplayCorners(this);
        binding.switchAvoidRoundedCorners.setChecked(avoidRoundedCorners);
        updateAvoidRoundedCornersSwitchText(avoidRoundedCorners);
        binding.switchAvoidRoundedCorners.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LauncherPreferences.setAvoidRoundedDisplayCorners(this, isChecked);
            updateAvoidRoundedCornersSwitchText(isChecked);
        });
    }

    private void openResolutionScaleInputDialog() {
        int currentScale = LauncherPreferences.getGameResolutionScalePercent(this);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setText(String.valueOf(currentScale));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.settings_renderer_resolution_scale_title)
                .setMessage(getString(
                        R.string.settings_renderer_resolution_scale_dialog_message,
                        LauncherPreferences.MIN_GAME_RESOLUTION_SCALE_PERCENT,
                        LauncherPreferences.MAX_GAME_RESOLUTION_SCALE_PERCENT
                ))
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            int percent = parseResolutionScaleInput(input.getText() == null ? "" : input.getText().toString());
            percent = LauncherPreferences.clampGameResolutionScalePercent(percent);
            LauncherPreferences.setGameResolutionScalePercent(this, percent);
            updateResolutionScaleUi(percent);
            dialog.dismiss();
        }));

        dialog.show();
    }

    private int parseResolutionScaleInput(@NonNull String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable ignored) {
            return LauncherPreferences.getGameResolutionScalePercent(this);
        }
    }

    private void updateResolutionScaleUi(int percent) {
        int safePercent = LauncherPreferences.clampGameResolutionScalePercent(percent);
        binding.sliderGameResolutionScale.setProgress(
                safePercent - LauncherPreferences.MIN_GAME_RESOLUTION_SCALE_PERCENT
        );
        updateResolutionScaleText(safePercent);
    }

    private void updateResolutionScaleText(int percent) {
        binding.textGameResolutionScale.setText(getString(
                R.string.settings_renderer_resolution_scale_value,
                LauncherPreferences.clampGameResolutionScalePercent(percent)
        ));
    }

    private void setupControllerSettings() {
        binding.buttonEditBuiltInController.setOnClickListener(view ->
                GamepadMappingDialog.show(this, () -> runOnUiThread(() -> FullscreenUtils.enableImmersive(this)))
        );

        binding.buttonManageTouchControls.setOnClickListener(view ->
                startActivity(new Intent(this, ControlsActivity.class))
        );

        setupHardwareMouseDpiScaleSettings();
        refreshControllerSettingsValues();
    }


    private void setupHardwareMouseDpiScaleSettings() {
        if (binding == null || binding.layoutControllerSettings == null) return;
        if (binding.layoutControllerSettings.findViewWithTag("hardware_mouse_dpi_scale") != null) return;

        LinearLayout container = new LinearLayout(this);
        container.setTag("hardware_mouse_dpi_scale");
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(14), 0, 0);

        View divider = new View(this);
        divider.setBackgroundColor(0x33000000);
        container.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(1))
        ));

        TextView title = new TextView(this);
        title.setText("Hardware mouse DPI scale");
        title.setTextSize(16f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(12);
        container.addView(title, titleParams);

        TextView summary = new TextView(this);
        summary.setText("Adjusts real mouse / captured-pointer speed using a Zalith-style relative pointer multiplier. Touch camera movement, hotbar taps, and absolute menu taps are not scaled.");
        summary.setTextSize(13f);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        summaryParams.topMargin = dp(2);
        container.addView(summary, summaryParams);

        textHardwareMouseDpiScale = new TextView(this);
        textHardwareMouseDpiScale.setTextSize(14f);
        textHardwareMouseDpiScale.setTypeface(textHardwareMouseDpiScale.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        valueParams.topMargin = dp(8);
        container.addView(textHardwareMouseDpiScale, valueParams);

        sliderHardwareMouseDpiScale = new SeekBar(this);
        sliderHardwareMouseDpiScale.setMax(GamepadMappingStore.MAX_MOUSE_DPI_SCALE - GamepadMappingStore.MIN_MOUSE_DPI_SCALE);
        sliderHardwareMouseDpiScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = GamepadMappingStore.MIN_MOUSE_DPI_SCALE + progress;
                updateHardwareMouseDpiScaleText(percent);
                if (fromUser) {
                    GamepadMappingStore.get(LauncherSettingsActivity.this).setHardwareMouseDpiScale(percent);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int percent = GamepadMappingStore.MIN_MOUSE_DPI_SCALE + seekBar.getProgress();
                GamepadMappingStore.get(LauncherSettingsActivity.this).setHardwareMouseDpiScale(percent);
                updateHardwareMouseDpiScaleUi(percent);
            }
        });
        container.addView(sliderHardwareMouseDpiScale, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        binding.layoutControllerSettings.addView(container);
    }

    private void refreshControllerSettingsValues() {
        if (binding == null) return;

        boolean touchControlsEnabled = ControlsPreferences.isTouchControlsEnabled(this);
        binding.switchTouchControlsEnabled.setOnCheckedChangeListener(null);
        binding.switchTouchControlsEnabled.setChecked(touchControlsEnabled);
        updateTouchControlsSwitchText(touchControlsEnabled);
        binding.switchTouchControlsEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ControlsPreferences.setTouchControlsEnabled(this, isChecked);
            updateTouchControlsSwitchText(isChecked);
        });

        updateMinecraftTouchGestureSettingsUi();
        updateHardwareMouseDpiScaleUi(GamepadMappingStore.get(this).getHardwareMouseDpiScale());

        boolean forceSdl = LauncherPreferences.isForceSdlControllerBridge(this);
        binding.switchForceSdlControllerBridge.setOnCheckedChangeListener(null);
        binding.switchForceSdlControllerBridge.setChecked(forceSdl);
        updateForceSdlControllerBridgeSwitchText(forceSdl);
        binding.switchForceSdlControllerBridge.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LauncherPreferences.setForceSdlControllerBridge(this, isChecked);
            updateForceSdlControllerBridgeSwitchText(isChecked);
        });
    }


    private void updateMinecraftTouchGestureSettingsUi() {
        boolean gesturesEnabled = ControlsPreferences.isMinecraftTouchGesturesEnabled(this);
        binding.switchMinecraftTouchGestures.setOnCheckedChangeListener(null);
        binding.switchMinecraftTouchGestures.setChecked(gesturesEnabled);
        updateMinecraftTouchGesturesSwitchText(gesturesEnabled);
        binding.switchMinecraftTouchGestures.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ControlsPreferences.setMinecraftTouchGesturesEnabled(this, isChecked);
            updateMinecraftTouchGesturesSwitchText(isChecked);
            updateDoubleTapToDropEnabledState(isChecked);
        });

        boolean doubleTapToDrop = ControlsPreferences.isDoubleTapToDropEnabled(this);
        binding.switchDoubleTapToDrop.setOnCheckedChangeListener(null);
        binding.switchDoubleTapToDrop.setChecked(doubleTapToDrop);
        updateDoubleTapToDropSwitchText(doubleTapToDrop);
        updateDoubleTapToDropEnabledState(gesturesEnabled);
        binding.switchDoubleTapToDrop.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ControlsPreferences.setDoubleTapToDropEnabled(this, isChecked);
            updateDoubleTapToDropSwitchText(isChecked);
        });
    }

    private void updateMinecraftTouchGesturesSwitchText(boolean enabled) {
        binding.switchMinecraftTouchGestures.setText(enabled
                ? R.string.controller_minecraft_touch_gestures_on
                : R.string.controller_minecraft_touch_gestures_off);
    }

    private void updateDoubleTapToDropSwitchText(boolean enabled) {
        binding.switchDoubleTapToDrop.setText(enabled
                ? R.string.controller_double_tap_to_drop_on
                : R.string.controller_double_tap_to_drop_off);
    }

    private void updateDoubleTapToDropEnabledState(boolean gesturesEnabled) {
        binding.switchDoubleTapToDrop.setEnabled(gesturesEnabled);
        binding.textDoubleTapToDropSummary.setAlpha(gesturesEnabled ? 1.0f : 0.55f);
    }

    private void updateHardwareMouseDpiScaleUi(int percent) {
        int safePercent = Math.max(
                GamepadMappingStore.MIN_MOUSE_DPI_SCALE,
                Math.min(GamepadMappingStore.MAX_MOUSE_DPI_SCALE, percent)
        );
        if (sliderHardwareMouseDpiScale != null) {
            sliderHardwareMouseDpiScale.setProgress(safePercent - GamepadMappingStore.MIN_MOUSE_DPI_SCALE);
        }
        updateHardwareMouseDpiScaleText(safePercent);
    }

    private void updateHardwareMouseDpiScaleText(int percent) {
        if (textHardwareMouseDpiScale != null) {
            textHardwareMouseDpiScale.setText("Mouse DPI scale: " + percent + "%");
        }
    }

    private void updateTouchControlsSwitchText(boolean enabled) {
        binding.switchTouchControlsEnabled.setText(enabled
                ? R.string.controller_touch_controls_enabled_on
                : R.string.controller_touch_controls_enabled_off);
    }

    private void updateForceSdlControllerBridgeSwitchText(boolean enabled) {
        binding.switchForceSdlControllerBridge.setText(enabled
                ? R.string.controller_force_sdl_on
                : R.string.controller_force_sdl_off);
    }

    private void registerNotificationPermissionLauncher() {
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    LauncherNotificationPermissionHelper.setBackgroundInstallNotificationsEnabled(this, granted);
                    updateInstallNotificationSettingsUi();
                    Toast.makeText(
                            this,
                            granted
                                    ? R.string.notification_permission_enabled_toast
                                    : R.string.notification_permission_denied_toast,
                            granted ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG
                    ).show();

                    if (!granted && LauncherNotificationPermissionHelper.requiresRuntimePermission()) {
                        showNotificationDeniedSettingsDialog();
                    }
                }
        );
    }

    private void setupInstallNotificationSettings() {
        updateInstallNotificationSettingsUi();
    }

    private void updateInstallNotificationSettingsUi() {
        if (binding == null || binding.switchInstallNotifications == null) return;

        boolean permissionGranted = LauncherNotificationPermissionHelper.hasPostNotificationsPermission(this);
        boolean enabled = LauncherNotificationPermissionHelper.isBackgroundInstallNotificationsEnabled(this) && permissionGranted;

        binding.switchInstallNotifications.setOnCheckedChangeListener(null);
        binding.switchInstallNotifications.setChecked(enabled);
        binding.switchInstallNotifications.setText(enabled
                ? R.string.install_notifications_on
                : R.string.install_notifications_off);

        if (!LauncherNotificationPermissionHelper.requiresRuntimePermission()) {
            binding.textInstallNotificationsSummary.setText(R.string.install_notifications_summary_old_android);
        } else if (permissionGranted) {
            binding.textInstallNotificationsSummary.setText(R.string.install_notifications_summary_enabled);
        } else {
            binding.textInstallNotificationsSummary.setText(R.string.install_notifications_summary_permission_needed);
        }

        binding.switchInstallNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                LauncherNotificationPermissionHelper.setBackgroundInstallNotificationsEnabled(this, false);
                updateInstallNotificationSettingsUi();
                return;
            }

            if (LauncherNotificationPermissionHelper.hasPostNotificationsPermission(this)) {
                LauncherNotificationPermissionHelper.setBackgroundInstallNotificationsEnabled(this, true);
                updateInstallNotificationSettingsUi();
                return;
            }

            LauncherNotificationPermissionHelper.setBackgroundInstallNotificationsEnabled(this, true);
            if (notificationPermissionLauncher != null) {
                LauncherNotificationPermissionHelper.requestPostNotificationsPermission(notificationPermissionLauncher);
            }
        });
    }

    private void showNotificationDeniedSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_denied_title)
                .setMessage(R.string.notification_permission_denied_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.notification_permission_open_settings, (dialog, which) ->
                        LauncherNotificationPermissionHelper.openAppNotificationSettings(this))
                .show();
    }

    private void registerMicrophonePermissionLauncher() {
        microphonePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    updateSimpleVoiceChatPermissionUi();
                    Toast.makeText(
                            this,
                            granted
                                    ? R.string.simple_voice_chat_permission_granted_toast
                                    : R.string.simple_voice_chat_permission_denied_toast,
                            granted ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG
                    ).show();

                    if (!granted) {
                        showSimpleVoiceChatPermissionDeniedDialog();
                    }
                }
        );
    }

    private void registerMobileGluesFolderPickerLauncher() {
        mobileGluesFolderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                    Uri treeUri = result.getData().getData();
                    if (treeUri == null) return;
                    try {
                        final int flags = result.getData().getFlags()
                                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        getContentResolver().takePersistableUriPermission(treeUri, flags);
                    } catch (Throwable ignored) {
                    }
                    MobileGluesConfigHelper.setSelectedConfigTreeUri(this, treeUri);
                    RendererInterface renderer = getSelectedRendererFromSpinner();
                    updateMobileGluesConfigSummary(renderer);
                    Toast.makeText(this, "MobileGlues folder saved.", Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void setupSimpleVoiceChatSettings() {
        updateSimpleVoiceChatPermissionUi();
        binding.buttonSimpleVoiceChatMicrophonePermission.setOnClickListener(view -> {
            if (AndroidMicrophonePermission.isGranted(this)) {
                showSimpleVoiceChatPermissionGrantedDialog();
                return;
            }

            if (microphonePermissionLauncher != null) {
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            } else {
                AndroidMicrophonePermission.showRequestDialog(this);
            }
        });
    }

    private void updateSimpleVoiceChatPermissionUi() {
        if (binding == null
                || binding.buttonSimpleVoiceChatMicrophonePermission == null
                || binding.textSimpleVoiceChatMicrophoneStatus == null) {
            return;
        }

        boolean granted = AndroidMicrophonePermission.isGranted(this);
        binding.textSimpleVoiceChatMicrophoneStatus.setText(granted
                ? R.string.simple_voice_chat_microphone_status_granted
                : R.string.simple_voice_chat_microphone_status_missing);
        binding.buttonSimpleVoiceChatMicrophonePermission.setText(granted
                ? R.string.simple_voice_chat_microphone_button_enabled
                : R.string.simple_voice_chat_microphone_button_enable);
    }

    private void showSimpleVoiceChatPermissionGrantedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.simple_voice_chat_microphone_title)
                .setMessage(R.string.simple_voice_chat_microphone_already_granted)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showSimpleVoiceChatPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.simple_voice_chat_microphone_title)
                .setMessage(R.string.simple_voice_chat_microphone_denied_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.simple_voice_chat_open_app_settings, (dialog, which) ->
                        AndroidMicrophonePermission.openAppSettings(this))
                .show();
    }

    private void setupLauncherSettings() {
        setupMemorySettings();
        setupInstallNotificationSettings();
        setupSimpleVoiceChatSettings();

        binding.checkKeepLogs.setChecked(LauncherLogManager.isKeepLogHistoryEnabled(this));
        binding.checkKeepLogs.setOnCheckedChangeListener((buttonView, isChecked) ->
                LauncherLogManager.setKeepLogHistoryEnabled(this, isChecked));

        boolean showInGameSettingsButton = LauncherPreferences.isShowInGameSettingsButton(this);
        binding.switchShowInGameSettingsButton.setChecked(showInGameSettingsButton);
        updateInGameSettingsButtonSwitchText(showInGameSettingsButton);
        binding.switchShowInGameSettingsButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LauncherPreferences.setShowInGameSettingsButton(this, isChecked);
            updateInGameSettingsButtonSwitchText(isChecked);
        });

        boolean showGameLogOverlay = LauncherPreferences.isShowGameLogOverlay(this);
        binding.switchShowGameLogOverlay.setChecked(showGameLogOverlay);
        updateGameLogOverlaySwitchText(showGameLogOverlay);
        binding.switchShowGameLogOverlay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LauncherPreferences.setShowGameLogOverlay(this, isChecked);
            updateGameLogOverlaySwitchText(isChecked);
        });

        setupFloatingGameOverlaySettings();

        binding.buttonShareLatestLog.setOnClickListener(view -> LauncherLogManager.shareLatestLog(this));
        setupUpdateCheckerSettings();
    }


    private void setupFloatingGameOverlaySettings() {
        if (binding == null || binding.switchShowInGameSettingsButton == null) return;
        if (!(binding.switchShowInGameSettingsButton.getParent() instanceof ViewGroup)) return;

        ViewGroup parent = (ViewGroup) binding.switchShowInGameSettingsButton.getParent();
        if (parent.findViewWithTag("floating_game_overlay_settings") != null) return;

        LinearLayout container = new LinearLayout(this);
        container.setTag("floating_game_overlay_settings");
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(12), 0, 0);
        parent.addView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        View divider = new View(this);
        divider.setBackgroundColor(0x33000000);
        container.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(1))
        ));

        TextView title = new TextView(this);
        title.setText("In-game overlay");
        title.setTextSize(16f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(12);
        container.addView(title, titleParams);

        com.google.android.material.switchmaterial.SwitchMaterial showFps =
                new com.google.android.material.switchmaterial.SwitchMaterial(this);
        showFps.setChecked(GameOverlayPreferences.isShowGameFpsCounter(this));
        updateFloatingFpsSwitchText(showFps, showFps.isChecked());
        showFps.setOnCheckedChangeListener((buttonView, isChecked) -> {
            GameOverlayPreferences.setShowGameFpsCounter(this, isChecked);
            updateFloatingFpsSwitchText(showFps, isChecked);
        });
        container.addView(showFps, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView fpsSummary = new TextView(this);
        fpsSummary.setText("Shows real-time FPS on the floating settings button while Minecraft is running.");
        fpsSummary.setTextSize(13f);
        LinearLayout.LayoutParams fpsSummaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        fpsSummaryParams.topMargin = dp(2);
        container.addView(fpsSummary, fpsSummaryParams);

        TextView placementTitle = new TextView(this);
        placementTitle.setText("Floating settings button position");
        placementTitle.setTextSize(15f);
        placementTitle.setTypeface(placementTitle.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams placementTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        placementTitleParams.topMargin = dp(12);
        container.addView(placementTitle, placementTitleParams);

        android.widget.Spinner placementSpinner = new android.widget.Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                GameOverlayPreferences.getPlacementLabels()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        placementSpinner.setAdapter(adapter);
        placementSpinner.setSelection(GameOverlayPreferences.indexOfPlacement(
                GameOverlayPreferences.getGameSettingsButtonPlacement(this)
        ), false);
        placementSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean ready;

            @Override
            public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
                if (!ready) {
                    ready = true;
                    return;
                }
                String placement = GameOverlayPreferences.placementValueForIndex(position);
                GameOverlayPreferences.setGameSettingsButtonPlacement(LauncherSettingsActivity.this, placement);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        spinnerParams.topMargin = dp(6);
        container.addView(placementSpinner, spinnerParams);

        TextView placementSummary = new TextView(this);
        placementSummary.setText("This controls the default corner. Dragging the button in game saves a custom position until you reset it or pick a new corner.");
        placementSummary.setTextSize(13f);
        LinearLayout.LayoutParams placementSummaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        placementSummaryParams.topMargin = dp(2);
        container.addView(placementSummary, placementSummaryParams);

        MaterialButton resetPosition = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        resetPosition.setText("Reset floating button position");
        resetPosition.setAllCaps(false);
        resetPosition.setOnClickListener(view -> GameOverlayPreferences.resetGameSettingsButtonCustomPosition(this));
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        resetParams.topMargin = dp(8);
        container.addView(resetPosition, resetParams);
    }

    private void updateFloatingFpsSwitchText(@NonNull android.widget.CompoundButton switchView, boolean enabled) {
        switchView.setText(enabled ? "Show FPS counter: On" : "Show FPS counter: Off");
    }

    private void setupUpdateCheckerSettings() {
        if (binding == null || binding.buttonShareLatestLog == null) return;
        if (!(binding.buttonShareLatestLog.getParent() instanceof ViewGroup)) return;

        ViewGroup parent = (ViewGroup) binding.buttonShareLatestLog.getParent();
        if (parent.findViewWithTag("update_checker_settings") != null) return;

        LinearLayout container = new LinearLayout(this);
        container.setTag("update_checker_settings");
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(12), 0, 0);
        parent.addView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("Launcher updates");
        title.setTextSize(16f);
        title.setGravity(Gravity.START);
        container.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView summary = new TextView(this);
        summary.setText("Checks GitHub releases for newer DroidBridge builds.");
        summary.setTextSize(13f);
        summary.setPadding(0, dp(2), 0, dp(6));
        container.addView(summary, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        CheckBox autoCheck = new CheckBox(this);
        autoCheck.setText("Check for updates on startup");
        autoCheck.setChecked(LauncherUpdatePreferences.isAutoCheckEnabled(this));
        autoCheck.setOnCheckedChangeListener((buttonView, isChecked) ->
                LauncherUpdatePreferences.setAutoCheckEnabled(this, isChecked));
        container.addView(autoCheck, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        MaterialButton checkNow = new MaterialButton(this);
        checkNow.setText("Check for updates");
        checkNow.setAllCaps(false);
        checkNow.setOnClickListener(view -> LauncherUpdateDialogs.checkManually(this));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonParams.topMargin = dp(6);
        container.addView(checkNow, buttonParams);
    }

    private void setupPrivacyPolicySettings() {
        setupLegalLinkButton(
                binding.buttonOpenMinecraftEula,
                LegalLinks.MINECRAFT_EULA_URL,
                "Minecraft EULA link is not configured."
        );
        setupLegalLinkButton(
                binding.buttonOpenPrivacyPolicy,
                LegalLinks.DROIDBRIDGE_PRIVACY_POLICY_URL,
                "Privacy Policy link is not available yet."
        );
        setupLegalLinkButton(
                binding.buttonOpenDroidBridgeTerms,
                LegalLinks.DROIDBRIDGE_TERMS_URL,
                "DroidBridge Terms of Service link is not available yet."
        );
        setupLegalLinkButton(
                binding.buttonOpenDroidBridgeLicense,
                LegalLinks.DROIDBRIDGE_LICENSING,
                "DroidBridge Terms of Service link is not available yet."
        );
    }

    private void setupLegalLinkButton(
            @NonNull MaterialButton button,
            @Nullable String url,
            @NonNull String unavailableMessage
    ) {
        boolean available = !isNullOrBlank(url);
        button.setEnabled(available);
        button.setOnClickListener(view -> {
            if (!available || !LegalLinks.open(this, url)) {
                Toast.makeText(this, unavailableMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupMemorySettings() {
        int maxMemoryMb = MemoryAllocationUtils.getMaxAllocatableMemoryMb(this);
        int currentMemoryMb = MemoryAllocationUtils.resolveAllocatedMemoryMb(this);

        updateMemorySeekBarBounds(currentMemoryMb);
        updateMemoryText(currentMemoryMb);
        updateAvailableMemorySummary(maxMemoryMb);

        binding.sliderAllocatedRam.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;

                int memoryMb = memoryFromSeekBarProgress(progress);
                LauncherPreferences.setAllocatedMemoryMb(LauncherSettingsActivity.this, memoryMb);
                updateMemoryText(memoryMb);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int memoryMb = memoryFromSeekBarProgress(seekBar.getProgress());
                int safeMemoryMb = MemoryAllocationUtils.clampToAllowedRam(LauncherSettingsActivity.this, memoryMb);
                LauncherPreferences.setAllocatedMemoryMb(LauncherSettingsActivity.this, safeMemoryMb);
                updateMemorySlider(safeMemoryMb);
            }
        });

        binding.textAllocatedRam.setOnClickListener(view -> openMemoryInputDialog());
    }

    private void openMemoryInputDialog() {
        int maxMemoryMb = MemoryAllocationUtils.getMaxAllocatableMemoryMb(this);
        int currentMemoryMb = MemoryAllocationUtils.resolveAllocatedMemoryMb(this);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setText(String.valueOf(currentMemoryMb));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.memory_dialog_title)
                .setMessage(getString(R.string.memory_dialog_message, maxMemoryMb))
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            int requestedMb = parseMemoryInput(input.getText() == null ? "" : input.getText().toString());
            int memoryMb = MemoryAllocationUtils.clampToAllowedRam(this, requestedMb);
            LauncherPreferences.setAllocatedMemoryMb(this, memoryMb);
            updateMemorySlider(memoryMb);
            dialog.dismiss();
        }));

        dialog.show();
    }

    private int parseMemoryInput(@NonNull String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable ignored) {
            return MemoryAllocationUtils.resolveAllocatedMemoryMb(this);
        }
    }

    private void updateMemorySlider(int memoryMb) {
        int maxMemoryMb = MemoryAllocationUtils.getMaxAllocatableMemoryMb(this);
        int safeMemoryMb = MemoryAllocationUtils.clampToAllowedRam(this, memoryMb);

        updateMemorySeekBarBounds(safeMemoryMb);
        updateMemoryText(safeMemoryMb);
        updateAvailableMemorySummary(maxMemoryMb);
    }

    private void updateMemorySeekBarBounds(int memoryMb) {
        int maxMemoryMb = MemoryAllocationUtils.getMaxAllocatableMemoryMb(this);
        int minMemoryMb = MemoryAllocationUtils.getMinimumMemoryMb(maxMemoryMb);
        int safeMemoryMb = MemoryAllocationUtils.clampToAllowedRam(this, memoryMb);
        int steps = Math.max(1, (maxMemoryMb - minMemoryMb) / MemoryAllocationUtils.RAM_STEP_MB);

        binding.sliderAllocatedRam.setMax(steps);
        binding.sliderAllocatedRam.setProgress(progressFromMemory(safeMemoryMb));
    }

    private int progressFromMemory(int memoryMb) {
        int maxMemoryMb = MemoryAllocationUtils.getMaxAllocatableMemoryMb(this);
        int minMemoryMb = MemoryAllocationUtils.getMinimumMemoryMb(maxMemoryMb);
        int safeMemoryMb = MemoryAllocationUtils.clampToAllowedRam(this, memoryMb);
        return Math.max(0, (safeMemoryMb - minMemoryMb) / MemoryAllocationUtils.RAM_STEP_MB);
    }

    private int memoryFromSeekBarProgress(int progress) {
        int maxMemoryMb = MemoryAllocationUtils.getMaxAllocatableMemoryMb(this);
        int minMemoryMb = MemoryAllocationUtils.getMinimumMemoryMb(maxMemoryMb);
        int requestedMb = minMemoryMb + (Math.max(0, progress) * MemoryAllocationUtils.RAM_STEP_MB);
        return MemoryAllocationUtils.clampToAllowedRam(this, requestedMb);
    }

    private void updateMemoryText(int memoryMb) {
        binding.textAllocatedRam.setText(getString(R.string.memory_allocated_value, memoryMb, memoryMb / 1024f));
    }

    private void updateAvailableMemorySummary(int maxMemoryMb) {
        int totalMemoryMb = MemoryAllocationUtils.getTotalMemoryMb(this);
        binding.textAvailableRamSummary.setText(getString(
                R.string.memory_available_summary,
                maxMemoryMb,
                maxMemoryMb / 1024f,
                totalMemoryMb,
                totalMemoryMb / 1024f
        ));
    }

    private void updateSharedInstallsSwitchText(boolean showSharedInstalls) {
        binding.switchShowSharedInstalls.setText(
                showSharedInstalls
                        ? R.string.shared_installs_show
                        : R.string.shared_installs_hide
        );
    }

    private void updateRemoveInheritedVanillaSwitchText(boolean removeInheritedVanilla) {
        binding.switchRemoveInheritedVanilla.setText(
                removeInheritedVanilla
                        ? R.string.inherited_vanilla_remove_on
                        : R.string.inherited_vanilla_remove_off
        );
    }

    private void updateRenderSurfaceSwitchText(boolean useNativeSurfaceView) {
        binding.switchUseNativeSurface.setText(
                useNativeSurfaceView
                        ? R.string.render_surface_surface_view
                        : R.string.render_surface_texture_view
        );
    }

    private void updateForceFullscreenSwitchText(boolean forceFullscreen) {
        binding.switchForceFullscreenMode.setText(
                forceFullscreen
                        ? R.string.settings_renderer_full_screen_on
                        : R.string.settings_renderer_full_screen_off
        );
    }

    private void updateAvoidRoundedCornersSwitchText(boolean avoidRoundedCorners) {
        binding.switchAvoidRoundedCorners.setText(
                avoidRoundedCorners
                        ? R.string.settings_renderer_avoid_rounded_corners_on
                        : R.string.settings_renderer_avoid_rounded_corners_off
        );
    }

    private void updateSystemVulkanDriverSwitchText(boolean useSystemVulkanDriver) {
        binding.switchUseSystemVulkanDriver.setText(
                useSystemVulkanDriver
                        ? R.string.use_system_vulkan_driver_on
                        : R.string.use_system_vulkan_driver_off
        );
    }

    private void updateOpenGl26PlusSwitchText(boolean useOpenGl26Plus) {
        binding.switchUseOpenGlFor26Plus.setText(
                useOpenGl26Plus
                        ? R.string.use_opengl_26_plus_on
                        : R.string.use_opengl_26_plus_off
        );
    }

    private void updateInGameSettingsButtonSwitchText(boolean showInGameSettingsButton) {
        binding.switchShowInGameSettingsButton.setText(
                showInGameSettingsButton
                        ? R.string.game_settings_button_on
                        : R.string.game_settings_button_off
        );
    }

    private void updateGameLogOverlaySwitchText(boolean showGameLogOverlay) {
        binding.switchShowGameLogOverlay.setText(
                showGameLogOverlay
                        ? R.string.game_log_overlay_on
                        : R.string.game_log_overlay_off
        );
    }

    private void updateAccountStatus(@Nullable AccountStore.Account account) {
        boolean hasRememberedMicrosoft = accountStore != null && accountStore.hasStoredMicrosoftAccount();
        boolean offlineUnlocked = accountStore != null && accountStore.hasMicrosoftLoginCompletedOnce();
        boolean activeOffline = account != null && account.isOfflineAccount();
        boolean activeMicrosoft = account != null && account.isMicrosoftAccount();

        binding.buttonSignIn.setVisibility(hasRememberedMicrosoft ? View.GONE : View.VISIBLE);
        binding.buttonSignOut.setVisibility((hasRememberedMicrosoft || account != null) ? View.VISIBLE : View.GONE);
        binding.buttonManageOfflineAccounts.setVisibility(offlineUnlocked ? View.VISIBLE : View.GONE);
        binding.buttonManageOfflineAccounts.setEnabled(offlineUnlocked);
        binding.buttonUseMicrosoftAccount.setVisibility(hasRememberedMicrosoft && activeOffline ? View.VISIBLE : View.GONE);
        binding.buttonRefreshMicrosoftSkin.setVisibility(hasRememberedMicrosoft ? View.VISIBLE : View.GONE);
        binding.buttonRefreshMicrosoftSkin.setEnabled(hasRememberedMicrosoft);

        if (activeOffline) {
            String microsoftName = "Microsoft account";
            AccountStore.Account remembered = accountStore != null ? accountStore.loadLastMicrosoftAccount() : null;
            if (remembered != null) microsoftName = remembered.getBestDisplayName();
            binding.textAccountStatus.setText(getString(R.string.status_offline_account_with_microsoft, account.getBestDisplayName(), microsoftName));
            return;
        }

        if (activeMicrosoft) {
            binding.textAccountStatus.setText(getString(R.string.status_signed_in, account.getBestDisplayName()));
            return;
        }

        if (hasRememberedMicrosoft) {
            AccountStore.Account remembered = accountStore.loadLastMicrosoftAccount();
            String name = remembered != null ? remembered.getBestDisplayName() : "Microsoft Player";
            binding.textAccountStatus.setText(getString(R.string.status_microsoft_remembered, name));
            return;
        }

        if (offlineUnlocked) {
            binding.textAccountStatus.setText(R.string.status_signed_out_offline_unlocked);
        } else {
            binding.textAccountStatus.setText(R.string.status_signed_out);
        }
    }

    private void useRememberedMicrosoftAccount() {
        if (accountStore == null) return;
        try {
            accountStore.useLastMicrosoftAccount();
            AccountStore.Account account = accountStore.load();
            updateAccountStatus(account);
            updateSkinUi(account);
            Toast.makeText(this, R.string.microsoft_account_restored, Toast.LENGTH_SHORT).show();
        } catch (Throwable throwable) {
            Toast.makeText(this, throwable.getMessage() != null ? throwable.getMessage() : throwable.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private void showOfflineAccountsDialog() {
        if (accountStore == null || !(BuildConfig.DEBUG || accountStore.hasMicrosoftLoginCompletedOnce())) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.offline_locked_title)
                    .setMessage(R.string.offline_locked_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(24);
        root.setPadding(padding, dp(18), padding, dp(4));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        root.addView(buildDialogHeader(
                R.drawable.ic_player_head_placeholder,
                R.string.offline_accounts_title,
                R.string.offline_accounts_dialog_summary
        ));

        TextView sectionTitle = new TextView(this);
        sectionTitle.setText(R.string.offline_accounts_section_title);
        sectionTitle.setTextAppearance(android.R.style.TextAppearance_Material_Medium);
        sectionTitle.setTypeface(sectionTitle.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sectionParams.topMargin = dp(18);
        root.addView(sectionTitle, sectionParams);

        ArrayList<AccountStore.Account> accounts = accountStore.listOfflineAccounts();
        if (accounts.isEmpty()) {
            root.addView(buildEmptyOfflineAccountCard());
        } else {
            AccountStore.Account active = accountStore.load();
            for (AccountStore.Account offline : accounts) {
                root.addView(buildOfflineAccountRow(offline, active));
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(scrollView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.offline_account_add, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            dialog.dismiss();
            showEditOfflineAccountDialog(null);
        }));
        offlineAccountsDialog = dialog;
        dialog.setOnDismissListener(d -> { if (offlineAccountsDialog == dialog) offlineAccountsDialog = null; });
        dialog.show();
    }

    @NonNull
    private View buildOfflineAccountRow(@NonNull AccountStore.Account offline, @Nullable AccountStore.Account active) {
        boolean isActive = active != null && active.isOfflineAccount() && offline.accountId.equals(active.accountId);

        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(18));
        card.setCardElevation(dp(1));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(isActive ? 0xFF68C995 : 0x22000000);
        card.setCardBackgroundColor(isActive ? 0xFFE9F8EF : 0xFFFFFFFF);
        card.setUseCompatPadding(true);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.addView(row, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ));

        ImageView avatar = new ImageView(this);
        avatar.setAdjustViewBounds(true);
        avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        avatar.setBackgroundResource(R.drawable.bg_player_head_preview);
        avatar.setPadding(dp(4), dp(4), dp(4), dp(4));
        avatar.setImageResource(R.drawable.ic_player_head_placeholder);
        PlayerHeadLoader.loadInto(this, avatar, offline, null);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(58), dp(58));
        avatarParams.rightMargin = dp(14);
        row.addView(avatar, avatarParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(offline.getBestDisplayName());
        title.setTextAppearance(android.R.style.TextAppearance_Material_Medium);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        labels.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = new TextView(this);
        subtitle.setText(offline.hasOfflineSkin()
                ? getString(R.string.offline_account_row_skin, offline.offlineSkinModel)
                : getString(R.string.offline_account_row_no_skin));
        subtitle.setTextAppearance(android.R.style.TextAppearance_Material_Small);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = dp(2);
        labels.addView(subtitle, subtitleParams);

        if (isActive) {
            TextView activeBadge = new TextView(this);
            activeBadge.setText(R.string.offline_account_active_badge);
            activeBadge.setTextAppearance(android.R.style.TextAppearance_Material_Small);
            activeBadge.setTextColor(0xFF168A49);
            activeBadge.setTypeface(activeBadge.getTypeface(), android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            badgeParams.topMargin = dp(4);
            labels.addView(activeBadge, badgeParams);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        actionsParams.leftMargin = dp(12);
        row.addView(actions, actionsParams);

        MaterialButton use = buildCompactDialogButton(isActive ? R.string.offline_account_active_button : R.string.offline_account_use);
        use.setEnabled(!isActive);
        use.setOnClickListener(v -> {
            if (offlineAccountsDialog != null) offlineAccountsDialog.dismiss();
            accountStore.activateOfflineAccount(offline.accountId);
            AccountStore.Account account = accountStore.load();
            updateAccountStatus(account);
            updateSkinUi(account);
            Toast.makeText(this, getString(R.string.offline_account_enabled, offline.getBestDisplayName()), Toast.LENGTH_SHORT).show();
        });
        addButtonWithTopMargin(actions, use, 0);

        MaterialButton edit = buildCompactDialogButton(R.string.offline_account_edit_button);
        edit.setOnClickListener(v -> {
            if (offlineAccountsDialog != null) offlineAccountsDialog.dismiss();
            showEditOfflineAccountDialog(offline);
        });
        addButtonWithTopMargin(actions, edit, dp(6));

        MaterialButton delete = buildCompactDialogButton(R.string.offline_account_delete_button);
        delete.setOnClickListener(v -> {
            if (offlineAccountsDialog != null) offlineAccountsDialog.dismiss();
            confirmDeleteOfflineAccount(offline);
        });
        addButtonWithTopMargin(actions, delete, dp(6));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(10);
        card.setLayoutParams(cardParams);
        return card;
    }

    private void showEditOfflineAccountDialog(@Nullable AccountStore.Account existing) {
        if (accountStore == null) return;

        pendingOfflineSkinUri = null;
        pendingOfflineSkinPreview = null;
        pendingOfflineSkinLabel = null;

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(18), dp(24), dp(4));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        root.addView(buildDialogHeader(
                R.drawable.ic_player_head_placeholder,
                existing == null ? R.string.offline_account_create_title : R.string.offline_account_edit_title,
                R.string.offline_account_edit_summary
        ));

        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(18));
        card.setCardElevation(dp(1));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(0x22000000);
        card.setCardBackgroundColor(0xFFFFFFFF);
        card.setUseCompatPadding(true);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(16);
        root.addView(card, cardParams);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.addView(row, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ));

        ImageView preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundResource(R.drawable.bg_player_head_preview);
        preview.setPadding(dp(6), dp(6), dp(6), dp(6));
        preview.setImageResource(R.drawable.ic_player_head_placeholder);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(88), dp(88));
        previewParams.rightMargin = dp(14);
        row.addView(preview, previewParams);
        pendingOfflineSkinPreview = preview;

        if (existing != null && existing.hasOfflineSkin()) {
            PlayerHeadLoader.loadInto(this, preview, existing, null);
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        row.addView(form, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setHint(R.string.offline_account_name_hint);
        input.setText(existing != null ? existing.getBestDisplayName() : "Player");
        form.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView skinLabel = new TextView(this);
        skinLabel.setText(existing != null && existing.hasOfflineSkin()
                ? getString(R.string.offline_account_skin_current, existing.offlineSkinModel)
                : getString(R.string.offline_account_skin_none));
        skinLabel.setTextAppearance(android.R.style.TextAppearance_Material_Small);
        LinearLayout.LayoutParams skinLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        skinLabelParams.topMargin = dp(6);
        form.addView(skinLabel, skinLabelParams);
        pendingOfflineSkinLabel = skinLabel;

        LinearLayout skinActions = new LinearLayout(this);
        skinActions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams skinActionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        skinActionsParams.topMargin = dp(8);
        form.addView(skinActions, skinActionsParams);

        MaterialButton chooseSkin = buildCompactDialogButton(R.string.offline_account_choose_skin);
        chooseSkin.setOnClickListener(v -> openOfflineSkinPicker());
        skinActions.addView(chooseSkin, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final boolean[] clearSkin = new boolean[]{false};
        MaterialButton clearSkinButton = buildCompactDialogButton(R.string.offline_account_clear_skin);
        clearSkinButton.setOnClickListener(v -> {
            pendingOfflineSkinUri = null;
            clearSkin[0] = true;
            preview.setImageResource(R.drawable.ic_player_head_placeholder);
            skinLabel.setText(R.string.offline_account_skin_none);
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        clearParams.leftMargin = dp(8);
        skinActions.addView(clearSkinButton, clearParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(scrollView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(existing == null ? R.string.offline_account_create : R.string.offline_account_save, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String name = sanitizeOfflineName(input.getText() == null ? "" : input.getText().toString());
            if (!isValidOfflineName(name)) {
                input.setError(getString(R.string.offline_account_invalid));
                return;
            }

            try {
                AccountStore.Account account = accountStore.saveOrUpdateOfflineAccount(
                        existing != null ? existing.accountId : null,
                        name,
                        pendingOfflineSkinUri,
                        clearSkin[0]
                );
                updateAccountStatus(account);
                updateSkinUi(account);
                Toast.makeText(this, getString(R.string.offline_account_enabled, account.getBestDisplayName()), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } catch (Throwable throwable) {
                input.setError(throwable.getMessage() != null ? throwable.getMessage() : throwable.toString());
            }
        }));

        dialog.setOnDismissListener(dialogInterface -> {
            pendingOfflineSkinUri = null;
            pendingOfflineSkinPreview = null;
            pendingOfflineSkinLabel = null;
        });
        dialog.show();
    }

    @NonNull
    private View buildDialogHeader(int iconResId, int titleResId, int summaryResId) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResId);
        icon.setBackgroundResource(R.drawable.bg_player_head_preview);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(72), dp(72));
        iconParams.rightMargin = dp(16);
        header.addView(icon, iconParams);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        header.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(titleResId);
        title.setTextAppearance(android.R.style.TextAppearance_Material_Large);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        text.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView summary = new TextView(this);
        summary.setText(summaryResId);
        summary.setTextAppearance(android.R.style.TextAppearance_Material_Small);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        summaryParams.topMargin = dp(4);
        text.addView(summary, summaryParams);

        return header;
    }

    @NonNull
    private View buildEmptyOfflineAccountCard() {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(18));
        card.setCardElevation(dp(1));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(0x22000000);
        card.setCardBackgroundColor(0xFFFFFFFF);
        card.setUseCompatPadding(true);

        TextView empty = new TextView(this);
        empty.setText(R.string.offline_accounts_empty);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(18), dp(22), dp(18), dp(22));
        empty.setTextAppearance(android.R.style.TextAppearance_Material_Small);
        card.addView(empty, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    @NonNull
    private MaterialButton buildCompactDialogButton(int textResId) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(textResId);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        return button;
    }

    private void addButtonWithTopMargin(@NonNull LinearLayout parent, @NonNull View child, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = topMargin;
        parent.addView(child, params);
    }

    private void updatePendingOfflineSkinPreview(@NonNull Uri uri) {
        if (pendingOfflineSkinPreview == null) return;
        File previewFile = new File(getCacheDir(), "pending_offline_skin_preview.png");
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(previewFile)) {
            if (input == null) throw new IllegalStateException("Unable to open selected skin.");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            android.graphics.Bitmap head = PlayerHeadLoader.loadHeadFromSkinFile(previewFile);
            if (head != null) pendingOfflineSkinPreview.setImageBitmap(head);
            else pendingOfflineSkinPreview.setImageResource(R.drawable.ic_player_head_placeholder);
        } catch (Throwable ignored) {
            pendingOfflineSkinPreview.setImageResource(R.drawable.ic_player_head_placeholder);
        }
    }

    private void openOfflineSkinPicker() {
        if (offlineSkinPickerLauncher == null) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        offlineSkinPickerLauncher.launch(intent);
    }

    private void confirmDeleteOfflineAccount(@NonNull AccountStore.Account account) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.offline_account_delete_title, account.getBestDisplayName()))
                .setMessage(R.string.offline_account_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.offline_account_delete_button, (dialog, which) -> {
                    accountStore.deleteOfflineAccount(account.accountId);
                    AccountStore.Account active = accountStore.load();
                    updateAccountStatus(active);
                    updateSkinUi(active);
                    showOfflineAccountsDialog();
                })
                .show();
    }

    private void updateChangeMicrosoftSkinButtonState(@Nullable AccountStore.Account activeAccount) {
        if (binding == null) return;

        AccountStore.Account microsoft = getMicrosoftSkinTargetAccount(activeAccount);
        boolean canChange = microsoft != null && microsoft.isMicrosoftAccount() && microsoft.hasMinecraftSession();
        binding.buttonChangeMicrosoftSkin.setVisibility(canChange ? View.VISIBLE : View.GONE);
        binding.buttonChangeMicrosoftSkin.setEnabled(canChange);
    }

    @Nullable
    private AccountStore.Account getMicrosoftSkinTargetAccount(@Nullable AccountStore.Account activeAccount) {
        if (activeAccount != null && activeAccount.isMicrosoftAccount() && activeAccount.hasMinecraftSession()) {
            return activeAccount;
        }
        if (accountStore == null) return null;
        AccountStore.Account remembered = accountStore.loadLastMicrosoftAccount();
        if (remembered != null && remembered.isMicrosoftAccount() && remembered.hasMinecraftSession()) {
            return remembered;
        }
        return null;
    }

    private void showChangeMicrosoftSkinDialog() {
        AccountStore.Account account = getMicrosoftSkinTargetAccount(accountStore != null ? accountStore.load() : null);
        if (account == null) {
            Toast.makeText(this, R.string.microsoft_skin_requires_account, Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.microsoft_skin_change_title)
                .setMessage(getString(R.string.microsoft_skin_change_message, account.getBestDisplayName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.microsoft_skin_pick, (dialog, which) -> openMicrosoftSkinPicker())
                .show();
    }

    private void openMicrosoftSkinPicker() {
        if (microsoftSkinPickerLauncher == null) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        microsoftSkinPickerLauncher.launch(intent);
    }

    private void prepareMicrosoftSkinUpload(@NonNull Uri uri) {
        File tempFile = new File(getCacheDir(), "pending_microsoft_account_skin.png");
        try {
            copyUriToFile(uri, tempFile);
            if (!CustomSkinStore.isSkinValid(tempFile)) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
                Toast.makeText(this, R.string.microsoft_skin_invalid, Toast.LENGTH_LONG).show();
                return;
            }

            SkinModelType detectedModel = CustomSkinStore.getSkinModel(tempFile);
            showConfirmMicrosoftSkinUploadDialog(tempFile, detectedModel);
        } catch (Throwable throwable) {
            Toast.makeText(this, throwable.getMessage() != null ? throwable.getMessage() : throwable.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private void showConfirmMicrosoftSkinUploadDialog(@NonNull File skinFile, @NonNull SkinModelType detectedModel) {
        final SkinModelType[] selectedModel = new SkinModelType[]{
                detectedModel == SkinModelType.SLIM ? SkinModelType.SLIM : SkinModelType.CLASSIC
        };
        String[] choices = new String[]{
                getString(R.string.microsoft_skin_variant_classic),
                getString(R.string.microsoft_skin_variant_slim)
        };
        int checked = selectedModel[0] == SkinModelType.SLIM ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle(R.string.microsoft_skin_upload_title)
                .setMessage(R.string.microsoft_skin_upload_message)
                .setSingleChoiceItems(choices, checked, (dialog, which) ->
                        selectedModel[0] = which == 1 ? SkinModelType.SLIM : SkinModelType.CLASSIC)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.microsoft_skin_upload, (dialog, which) ->
                        uploadMicrosoftAccountSkin(skinFile, selectedModel[0]))
                .show();
    }

    private void uploadMicrosoftAccountSkin(@NonNull File skinFile, @NonNull SkinModelType model) {
        AccountStore.Account account = getMicrosoftSkinTargetAccount(accountStore != null ? accountStore.load() : null);
        if (account == null) {
            Toast.makeText(this, R.string.microsoft_skin_requires_account, Toast.LENGTH_LONG).show();
            return;
        }

        binding.buttonChangeMicrosoftSkin.setEnabled(false);
        binding.buttonRefreshMicrosoftSkin.setEnabled(false);
        binding.textSkinStatus.setText(R.string.microsoft_skin_uploading);

        Thread thread = new Thread(() -> {
            try {
                MicrosoftSkinUploader.uploadSkin(account.minecraftAccessToken, skinFile, model);
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.microsoft_skin_upload_success, Toast.LENGTH_LONG).show();
                    refreshMicrosoftAccountAndSkin(false);
                });
            } catch (Throwable throwable) {
                runOnUiThread(() -> {
                    binding.buttonChangeMicrosoftSkin.setEnabled(true);
                    binding.buttonRefreshMicrosoftSkin.setEnabled(true);
                    String message = throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
                    binding.textSkinStatus.setText(getString(R.string.microsoft_skin_upload_failed, message));
                    Toast.makeText(this, getString(R.string.microsoft_skin_upload_failed, message), Toast.LENGTH_LONG).show();
                });
            }
        }, "Microsoft Skin Upload");
        thread.start();
    }

    private void copyUriToFile(@NonNull Uri sourceUri, @NonNull File destination) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(sourceUri);
             FileOutputStream output = new FileOutputStream(destination)) {
            if (input == null) throw new IllegalStateException("Could not open selected skin.");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private void refreshMicrosoftAccountAndSkin(boolean showToast) {
        if (authManager == null) return;
        binding.buttonRefreshMicrosoftSkin.setEnabled(false);
        binding.textSkinStatus.setText(R.string.microsoft_skin_refreshing);
        if (showToast) {
            Toast.makeText(this, R.string.microsoft_skin_refreshing, Toast.LENGTH_SHORT).show();
        }
        authManager.refreshMicrosoftAccount();
    }

    private static String sanitizeOfflineName(@Nullable String raw) {
        if (raw == null) return "Player";
        String cleaned = raw.trim().replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.length() > 16) cleaned = cleaned.substring(0, 16);
        return cleaned.length() == 0 ? "Player" : cleaned;
    }

    private static boolean isValidOfflineName(@Nullable String value) {
        return value != null && value.matches("[A-Za-z0-9_]{3,16}");
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static boolean isNullOrBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}

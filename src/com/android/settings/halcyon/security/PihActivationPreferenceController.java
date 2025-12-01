/*
 * Copyright (C) 2019-2021 ConquerOS Project
 *           (C) 2021-2025 Halcyon Project
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
package com.android.settings.halcyon.security;

import android.content.Context;
import android.os.SystemProperties;
import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.android.settings.core.BasePreferenceController;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.development.SystemPropPoker;
import com.android.internal.util.android.SystemRestartUtils;

public class PihActivationPreferenceController extends BasePreferenceController
        implements Preference.OnPreferenceChangeListener {

    private static final String PIH_PROPERTY = "persist.sys.dps.enabled";
    private final MetricsFeatureProvider mMetricsFeatureProvider;

    public PihActivationPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mMetricsFeatureProvider = FeatureFactory.getFeatureFactory().getMetricsFeatureProvider();
    }

    @Override
    public void updateState(Preference preference) {
        boolean enabled = SystemProperties.getBoolean(PIH_PROPERTY, true);

        if (!SystemProperties.get(PIH_PROPERTY, "").isEmpty()) {
            ((MainSwitchPreference) preference).setChecked(enabled);
        } else {
            SystemProperties.set(PIH_PROPERTY, Boolean.toString(true));
            SystemPropPoker.getInstance().poke();
            ((MainSwitchPreference) preference).setChecked(true);
        }
    }

    @Override
    public boolean onPreferenceChange(
            @NonNull Preference preference, @NonNull Object newValue) {

        mMetricsFeatureProvider.logClickedPreference(preference, getMetricsCategory());
        boolean enabled = (Boolean) newValue;

        SystemProperties.set(PIH_PROPERTY, Boolean.toString(enabled));
        SystemPropPoker.getInstance().poke();

        SystemRestartUtils.showSystemRestartDialog(mContext);

        return true;
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE_UNSEARCHABLE;
    }
}
/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.settings.development;

import android.content.Context;
import android.os.SystemProperties;

import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.development.DeveloperOptionsPreferenceController;

public class CertifiedPropsPreferenceController extends DeveloperOptionsPreferenceController
        implements Preference.OnPreferenceChangeListener, PreferenceControllerMixin {

    private static final String CERTIFIED_PROPS_KEY = "certified_props";
    private static final String DISABLE_GMS_PROPS_PROPERTY =
            "persist.sys.pihooks.disable.gms_props";

    public CertifiedPropsPreferenceController(Context context) {
        super(context);
    }

    @Override
    public String getPreferenceKey() {
        return CERTIFIED_PROPS_KEY;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final boolean isEnabled = (Boolean) newValue;
        SystemProperties.set(DISABLE_GMS_PROPS_PROPERTY, Boolean.toString(!isEnabled));
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        ((TwoStatePreference) mPreference).setChecked(!SystemProperties.getBoolean(
                DISABLE_GMS_PROPS_PROPERTY, false /* default */));
    }
}

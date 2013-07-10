/**
 * This file is part of Todo.txt Touch, an Android app for managing your todo.txt file (http://todotxt.com).
 *
 * Copyright (c) 2009-2012 Todo.txt contributors (http://todotxt.com)
 *
 * LICENSE:
 *
 * Todo.txt Touch is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 2 of the License, or (at your option) any
 * later version.
 *
 * Todo.txt Touch is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with Todo.txt Touch.  If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Todo.txt contributors <todotxt@yahoogroups.com>
 * @license http://www.gnu.org/licenses/gpl.html
 * @copyright 2009-2012 Todo.txt contributors (http://todotxt.com)
 */
package nl.mpcjanssen.simpletask;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.util.Log;

import nl.mpcjanssen.simpletask.util.Util;
import nl.mpcjanssen.simpletask.R;

import java.io.File;
import java.util.List;

public class Preferences extends PreferenceActivity {
	final static String TAG = Preferences.class.getSimpleName();
	public static final int RESULT_LOGOUT = RESULT_FIRST_USER + 1 ;
	public static final int RESULT_ARCHIVE = RESULT_FIRST_USER + 2 ;

	private void broadcastIntentAndClose(String intent, int result) {
		
		Intent broadcastIntent = new Intent(intent);
		sendBroadcast(broadcastIntent);
		
		// Close preferences screen
		setResult(result);
		finish();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
	
	@Override
	public void onBuildHeaders(List<Header> target) {
		loadHeadersFromResource(R.xml.preference_headers, target);
	}
	
	public static class TodoTxtPrefFragment extends PreferenceFragment
	{
        String pathValue ;
        EditTextPreference path;

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode==0 && resultCode == RESULT_OK) {
                pathValue = data.getData().getPath();
                Log.v(TAG, "" + data.getData().getPath());
                SharedPreferences.Editor edit = getPreferenceManager().getSharedPreferences().edit();
                edit.putString(getString(R.string.todo_path_pref_key), pathValue);
                edit.commit();
                path.setText(pathValue);
            }
        }

        @Override
		public void onCreate(final Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.todotxt_preferences);
            File defaultPath = new File(Environment.getExternalStorageDirectory(),
                    "data/nl.mpcjanssen.simpletask/");
            path = (EditTextPreference) findPreference(getString(R.string.todo_path_pref_key));
            pathValue = getPreferenceManager().getSharedPreferences().getString(getString(R.string.todo_path_pref_key),
                    defaultPath.toString());
            path.setText(pathValue);

            Preference oiBrowser = findPreference(getString(R.string.oibrowse_pref_key));
            oiBrowser.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("text/plain");
                    if(intent.resolveActivity(getActivity().getPackageManager()) != null) {
                        startActivityForResult(intent, 0);
                    } else {
                        intent = new Intent();
                        intent.setData(Uri.parse("market://details?id=org.openintents.filemanager"));
                        startActivityForResult(intent, 1);
                    }
                    return true;
                }
            });
		}
	}	

	public static class AboutPrefFragment extends PreferenceFragment
	{
		@Override
		public void onCreate(final Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.about_preferences);
			PreferenceActivity act = (PreferenceActivity) getActivity();
			PackageInfo packageInfo;
			String git_version;
			final Preference versionPref = findPreference("app_version");
			try {
				packageInfo = act.getPackageManager().getPackageInfo(act.getPackageName(),
						0);
				versionPref.setSummary("v" + packageInfo.versionName);
			} catch (NameNotFoundException e) {
				e.printStackTrace();
			}
			
			versionPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				
				@Override
				public boolean onPreferenceClick(Preference preference) {
					ClipboardManager clipboard = (ClipboardManager)
					        getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
					Util.showToastShort(getActivity(), R.string.version_copied);
					ClipData clip = ClipData.newPlainText("version info", versionPref.getSummary());
					clipboard.setPrimaryClip(clip);
					return true;
				}
			}) ;
		}
	}
}

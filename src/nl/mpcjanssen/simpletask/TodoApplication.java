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
import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxException;
import com.dropbox.sync.android.DbxFile;
import com.dropbox.sync.android.DbxFileStatus;
import com.dropbox.sync.android.DbxFileSystem;
import com.dropbox.sync.android.DbxPath;
import com.dropbox.sync.android.DbxSyncStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import nl.mpcjanssen.simpletask.task.Priority;
import nl.mpcjanssen.simpletask.task.Task;
import nl.mpcjanssen.simpletask.task.TaskPersistException;
import nl.mpcjanssen.simpletask.util.Util;
import nl.mpcjanssen.todotxtholo.R;


public class TodoApplication extends Application {
    private final static String TAG = TodoApplication.class.getSimpleName();
    private static Context mAppContext;
    public SharedPreferences mPrefs;
    private DbxAccountManager mDbxAcctMgr;

    private DbxFileSystem.SyncStatusListener mSyncStatusListener;
    private DbxFileSystem.PathListener mSyncListener;

    public TaskBag getTaskBag() {
        initTaskBag();
        return mTaskBag;
    }

    private TaskBag mTaskBag;
    private DbxFileSystem mDbxFs;
    private DbxPath mTodoPath;
    private DbxPath mDonePath;

    @Override
    public void onCreate() {
        super.onCreate();
        mAppContext = getApplicationContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        String appKey = getString(R.string.dropbox_consumer_key);
        String appSecret = getString(R.string.dropbox_consumer_secret);
        appKey = appKey.replaceFirst("^db-", "");
        mDbxAcctMgr = DbxAccountManager.getInstance(getApplicationContext(), appKey, appSecret);
    }

    private void initTaskBag()  {
        if (!isLinked()) {
            return;
        }
        try {
            mDbxFs = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());
            mTodoPath = new DbxPath(getRemotePath(), "todo.txt");
            mDonePath = new DbxPath(getRemotePath(), "done.txt");
            mDbxFs.awaitFirstSync();
            mTaskBag = new TaskBag();
            mTaskBag.reload();
        } catch (IOException e) {
            throw new TodoException("Failed to create taskbag" , e);
        }
    }

    public void watchDropbox(boolean watch) {
        Log.v(TAG, "Watching dropbox changes: " + watch);
        if (watch) {
            mSyncListener = new DbxFileSystem.PathListener() {
                @Override
                public void onPathChange(DbxFileSystem dbxFileSystem, DbxPath dbxPath, Mode mode) {
                    Log.v("SYNC", "Dropbox path changed reloading taskbag:" + dbxPath);
                    new AsyncTask() {
                        @Override
                        protected Object doInBackground(Object... objects) {
                            Log.v(TAG,"Dropbox syncing in background...");
                            Intent i = new Intent(Constants.INTENT_SYNC_START);
                            sendBroadcast(i);
                            try {
                                mDbxFs.syncNowAndWait();
                                Log.v(TAG,"Dropbox syncing in background done");
                                mTaskBag.reload();
                            } catch (DbxException e) {
                                e.printStackTrace();
                            }
                            i = new Intent(Constants.INTENT_SYNC_DONE);
                            sendBroadcast(i);
                            return null;
                        }
                    }.execute();
                }
            };
            mDbxFs.addPathListener(mSyncListener,mTodoPath, DbxFileSystem.PathListener.Mode.PATH_ONLY);
        } else {
            mDbxFs.removePathListener(mSyncListener, mTodoPath, DbxFileSystem.PathListener.Mode.PATH_ONLY);
        }
    }

    public void showToast(int resid) {
        Util.showToastLong(this, resid);
    }

    public void showToast(String string) {
        Util.showToastLong(this, string);
    }

    /**
     * Update user interface
     *
     * Update the elements of the user interface. The listview with tasks will be updated
     * if it is visible (by broadcasting an intent). All widgets will be updated as well.
     * This method should be called whenever the TaskBag changes.
     */
    private void updateUI() {
        sendBroadcast(new Intent(Constants.INTENT_UPDATE_UI));
        updateWidgets();
    }

    public void updateWidgets() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(getApplicationContext());
        for (int appWidgetId : mgr.getAppWidgetIds(new ComponentName(getApplicationContext(), MyAppWidgetProvider.class))) {
            mgr.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widgetlv);
            Log.v(TAG, "Updating widget: " + appWidgetId);
        }
    }

    private boolean isLinked() {
        return (mDbxAcctMgr!=null && mDbxAcctMgr.hasLinkedAccount());
    }

    public boolean isLoggedIn() {
        if (!isLinked()) {
            return false;
        } else {
            return true;
        }
    }

    public void startLogin(Activity activity) {
        Intent intent = new Intent(activity, LoginScreen.class);
        activity.startActivity(intent);
    }

    public DbxAccountManager getDbxAcctMgr() {
        return mDbxAcctMgr;
    }

    public void logout() {
        mDbxAcctMgr.unlink();
        mTaskBag = null;
    }

    private DbxPath getRemotePath() {
        return new DbxPath(mPrefs.getString("todotxtpath", getString(R.string.TODOTXTPATH_defaultPath)));
    }

    private DbxFile createOrOpen(DbxPath path) throws DbxException {
        if (mDbxFs.exists(path)) {
            return mDbxFs.open(path);
        } else {
            return mDbxFs.create(path);
        }
    }



    public static Context getAppContext() {
        return mAppContext;
    }

    public class TaskBag {
        private ArrayList<Task> tasks = new ArrayList<Task>();

        public void store() {
            String output = "";
            for (Task task : tasks) {
                output += task.inFileFormat() + getLineBreak();
            }
            try {
                DbxFile file = createOrOpen(mTodoPath);
                file.writeString(output);
                file.close();
            } catch (IOException e) {
                throw new TaskPersistException("Couldn't store taskbag" , e);
            }
            updateUI();
        }

        private void reload() {
            try {
                DbxFile file = createOrOpen(mTodoPath);
                tasks.clear();
                long line = 0;
                for (String text : file.readString().split("\r\n|\r|\n")) {
                    line++;
                    text = text.trim();
                    if (text=="") continue;
                    tasks.add(new Task(line, text));
                }
                file.close();
            } catch (IOException e) {
                throw new TaskPersistException("Couldn't load taskbag" , e);
            }
            updateUI();
        }

        public int size() {
            return tasks.size();
        }

        public ArrayList<Task> getTasks() {
            return tasks;
        }

        private String getLineBreak() {
            return mPrefs.getBoolean("linebreakspref", false) ? "\r\n" : "\n";
        }

        public void addAsTask(String input) {
            ArrayList<Task> tasksToAdd = new ArrayList<Task>();
            tasksToAdd.add(new Task(0, input));
            addTasks(tasksToAdd);
        }

        public void delete(Task task) {
            if (!tasks.remove(task)) {
                throw new TaskPersistException("Task not found, not deleted");
            }
        }

        public ArrayList<Priority> getPriorities() {
            // TODO cache this after reloads?
            Set<Priority> res = new HashSet<Priority>();
            for (Task item : tasks) {
                res.add(item.getPriority());
            }
            ArrayList<Priority> ret = new ArrayList<Priority>(res);
            Collections.sort(ret);
            return ret;
        }

        public ArrayList<String> getContexts(boolean includeNone) {
            // TODO cache this after reloads?
            Set<String> res = new HashSet<String>();
            for (Task item : tasks) {
                res.addAll(item.getContexts());
            }
            ArrayList<String> ret = new ArrayList<String>(res);
            Collections.sort(ret);
            if (includeNone) {
                ret.add(0, "-");
            }
            return ret;
        }

        public ArrayList<String> getProjects(boolean includeNone) {
            // TODO cache this after reloads?
            Set<String> res = new HashSet<String>();
            for (Task item : tasks) {
                res.addAll(item.getProjects());
            }
            ArrayList<String> ret = new ArrayList<String>(res);
            Collections.sort(ret);
            if (includeNone) {
                ret.add(0, "-");
            }
            return ret;
        }

        public void updateTask(Task old, String input) {
            int pos = tasks.indexOf(old);
            tasks.set(pos, new Task(0, input));
        }

        public void addTasks(ArrayList<Task> tasksToAdd) {
            tasks.addAll(tasksToAdd);
            store();
        }
    }
}

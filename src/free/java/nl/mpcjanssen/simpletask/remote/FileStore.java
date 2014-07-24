package nl.mpcjanssen.simpletask.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxException;
import com.dropbox.sync.android.DbxFile;
import com.dropbox.sync.android.DbxFileInfo;
import com.dropbox.sync.android.DbxFileSystem;
import com.dropbox.sync.android.DbxPath;
import com.dropbox.sync.android.DbxSyncStatus;
import com.google.common.io.CharStreams;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nl.mpcjanssen.simpletask.Constants;
import nl.mpcjanssen.simpletask.R;
import nl.mpcjanssen.simpletask.TodoException;
import nl.mpcjanssen.simpletask.util.ListenerList;
import nl.mpcjanssen.simpletask.util.Strings;
import nl.mpcjanssen.simpletask.util.Util;

/**
 * FileStore implementation backed by Dropbox
 */
public class FileStore implements FileStoreInterface {

    private final String TAG = getClass().getName();
    private String mEol;
    @Nullable
    private DbxFileSystem.PathListener m_observer;
    private DbxAccountManager mDbxAcctMgr;
    private Context mCtx;
    private DbxFileSystem mDbxFs;
    @Nullable
    private DbxFileSystem.SyncStatusListener m_syncstatus;
    @Nullable
    String activePath;
    @Nullable
    private ArrayList<String> mLines;
    private boolean m_isSyncing = true;

    public FileStore( Context ctx, String eol) {
        mCtx = ctx;
        mEol = eol;
        this.activePath = null;
        syncInProgress(true);
        setDbxAcctMgr();
    }

    private void setDbxAcctMgr () {
        if (mDbxAcctMgr==null) {
            String app_secret = mCtx.getString(R.string.dropbox_consumer_secret);
            String app_key = mCtx.getString(R.string.dropbox_consumer_key);
            app_key = app_key.replaceFirst("^db-","");
            mDbxAcctMgr = DbxAccountManager.getInstance(mCtx, app_key, app_secret);
        }
    }

    @Nullable
    private DbxFileSystem getDbxFS () {
        if (mDbxFs!=null) {
            return mDbxFs;
        }
        if (isAuthenticated()) {
            try {
                this.mDbxFs = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());
                return mDbxFs;
            } catch (IOException e) {
                e.printStackTrace();
                throw new TodoException("Dropbox", e);
            }
        }
        return null;
    }
    @NotNull
    static public String getDefaultPath() {
        return "/todo/todo.txt";
    }

    @Override
    public boolean isAuthenticated() {
        return mDbxAcctMgr != null && mDbxAcctMgr.hasLinkedAccount();
    }

    private void initialSync(final DbxFileSystem fs) {
        syncInProgress(true);
        new AsyncTask<Void,Void,Boolean>() {

            @Override
            protected Boolean doInBackground(Void... params) {
                Log.v(TAG, "Initial sync in background");
                try {
                    fs.awaitFirstSync();
                } catch (DbxException e) {
                    Log.e(TAG,"First sync failed: " + e.getCause());
                    e.printStackTrace();
                    return Boolean.FALSE;
                }
                return Boolean.TRUE;
            }
            @Override
            protected void onPostExecute(Boolean success) {
                Log.v(TAG, "Intial sync status" + success);
                if (success) {

                    notifyFileChanged();
                }
            }
        }.execute();


    }

    @Nullable
    @Override
    public ArrayList<String> get(final String path) {
        Log.v(TAG, "Getting contents of: " + path);
        if (!isAuthenticated()) {
            Log.v(TAG, "Not authenticated");
            return new ArrayList<String>();
        }
        DbxFileSystem fs = getDbxFS();
        if (fs==null) {
            return new ArrayList<String>();
        }
        try {
            if (!fs.hasSynced()) {
                initialSync(fs);
                return new ArrayList<String>();
            }
        } catch (DbxException e) {
            e.printStackTrace();
            return new ArrayList<String>();
        }
        startWatching(path);
        if (activePath != null && activePath.equals(path) && mLines!=null) {
            return mLines;
        }
        syncInProgress(true);

        // Clear and reload cache
        mLines = null;

        // Did we switch todo file?
        if (activePath!=null && !activePath.equals(path)) {
            stopWatching(activePath);
        }
        new AsyncTask<String, Void, ArrayList<String>>() {
            @Nullable
            @Override
            protected ArrayList<String> doInBackground(String... params) {
                syncInProgress(true);
                String path = params[0];
                activePath = path;
                ArrayList<String> results = null;
                try {
                    DbxFile openFile = openDbFile(path);
                    if (openFile==null) {
                        return null;
                    }
                    openFile.update();
                    results =  syncGetLines(openFile);
                    openFile.close();
                } catch (DbxException e) {
                    e.printStackTrace();
                }
                return results;
            }
            @Override
            protected void onPostExecute(ArrayList<String> results) {
                // Trigger update
                if (results!=null) {
                    syncInProgress(false);
                    notifyFileChanged();
                }
                mLines = results;
            }
        }.execute(path);
        return new ArrayList<String>();
    }


    @Nullable
    private synchronized DbxFile openDbFile(String path) throws DbxException {
        if (mDbxFs == null) {
            return null;
        }
        DbxPath dbPath = new DbxPath(path);
        if (mDbxFs.exists(dbPath)) {
            return mDbxFs.open(dbPath);
        } else {
            return mDbxFs.create(dbPath);
        }
    }

    @NotNull
    private synchronized ArrayList<String> syncGetLines(@Nullable DbxFile dbFile) {
        ArrayList<String> result = new ArrayList<String>();
        DbxFileSystem fs = getDbxFS();
        if (!isAuthenticated() || fs == null || dbFile == null) {
            return result;
        }
        try {
            try {
                dbFile.update();
            } catch (DbxException e) {
                Log.v(TAG, "Couldn't download latest" + e.toString());
            }
            FileInputStream stream = dbFile.getReadStream();
            result.addAll(CharStreams.readLines(new InputStreamReader(stream)));
            stream.close();
            return result;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;

    }

    private void syncInProgress(boolean inProgress) {
        m_isSyncing = inProgress;
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(mCtx);
        if (inProgress) {
            bm.sendBroadcast(new Intent(Constants.BROADCAST_SYNC_START));
        } else {
            bm.sendBroadcast(new Intent(Constants.BROADCAST_SYNC_DONE));
        }
    }

    @Override
    public void startLogin(Activity caller, int i) {
        mDbxAcctMgr.startLink(caller, 0);
    }

    @Override
    public void startWatching(final String path) {
        if (isAuthenticated() && getDbxFS() != null) {
            if (m_syncstatus==null) {
                m_syncstatus = new DbxFileSystem.SyncStatusListener() {

                    @Override
                    public void onSyncStatusChange(@NotNull DbxFileSystem dbxFileSystem) {
                        DbxSyncStatus status;
                        try {
                            status = dbxFileSystem.getSyncStatus();
                            Log.v(TAG, "Synchronizing: v " + status.download + " ^ " + status.upload);
                            if (!status.anyInProgress() || status.anyFailure() != null) {
                                Log.v(TAG, "Synchronizing done");
                                syncInProgress(false);
                            } else {
                                syncInProgress(true);
                            }
                        } catch (DbxException e) {
                            e.printStackTrace();
                        }
                    }
                };
                mDbxFs.addSyncStatusListener(m_syncstatus);
            }
            if (m_observer==null) {
                m_observer = new DbxFileSystem.PathListener() {
                    @Override
                    public void onPathChange(DbxFileSystem dbxFileSystem, DbxPath dbxPath, Mode mode) {
                        Log.v(TAG, "Synchronizing path change: " + dbxPath.toString());
                        notifyFileChanged();
                    }
                };
                mDbxFs.addPathListener(m_observer,new DbxPath(path), DbxFileSystem.PathListener.Mode.PATH_ONLY);
            }
        }
    }

    private void notifyFileChanged() {
        Log.v(TAG, "File changed: " + activePath);
        LocalBroadcastManager.getInstance(mCtx).sendBroadcast(new Intent(Constants.BROADCAST_FILE_CHANGED));
        LocalBroadcastManager.getInstance(mCtx).sendBroadcast(new Intent(Constants.BROADCAST_UPDATE_UI));
    }

    @Override
    public void stopWatching(String path) {
        if (getDbxFS()==null) {
            return;
        }
        if (m_syncstatus!=null) {
            mDbxFs.removeSyncStatusListener(m_syncstatus);
            m_syncstatus = null;
        }
        if (m_observer!=null) {
            mDbxFs.removePathListenerForAll(m_observer);
        }
        m_observer = null;
    }

    @Override
    public void deauthenticate() {
        mDbxAcctMgr.unlink();
    }

    @Override
    public void browseForNewFile(Activity act, String path, FileSelectedListener listener) {
        FileDialog dialog = new FileDialog(act, new DbxPath(path), true);
        dialog.addFileListener(listener);
        dialog.createFileDialog();
    }

    @Override
    public void append(String path, final List<String> lines) {
        if (isAuthenticated() && getDbxFS() != null) {
            new AsyncTask<String, Void, Void>() {
                @Nullable
                @Override
                protected Void doInBackground(String... params) {
                    String path = params[0];
                    String data = params[1];
                    Log.v(TAG, "Saving " + path + "in background thread");
                    try {
                        DbxFile openFile = openDbFile(path);
                        if (openFile!=null) {
                            openFile.appendString(data+mEol);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                @Override
                public void onPostExecute (Void v) {
                    if (mLines==null) {
                        mLines = new ArrayList<String>();
                    }
                    mLines.addAll(lines);
                }
            }.execute(path, mEol + Util.join(lines, mEol).trim());
        }
    }

    @Override
    public void update(final String filename, @NotNull final List<String> alOriginal, @NotNull final List<String> alUpdated) {
        new AsyncTask<String, Void, Void>() {
            @Nullable
            @Override
            protected Void doInBackground(String... params) {
                if (isAuthenticated() && getDbxFS() != null) {
                    try {
                        DbxFile openFile = openDbFile(filename);
                        if (openFile==null) {
                            Log.w(TAG, "Failed to open: " + filename + " tasks not updated");
                            return null;
                        }
                        ArrayList<String> contents = new ArrayList<String>();
                        contents.addAll(syncGetLines(openFile));
                        for (int i=0 ; i<alOriginal.size();i++) {
                            int index = contents.indexOf(alOriginal.get(i));
                            if (index!=-1) {
                                contents.remove(index);
                                contents.add(index,alUpdated.get(i));
                            }
                        }
                        openFile.writeString(Util.join(contents, mEol)+mEol);
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new TodoException("Dropbox", e);
                    }
                }
                return null;
            }
        }.execute();
    }


    @Override
    public void delete(final String mTodoName, final List<String> stringsToDelete) {

        new AsyncTask<String,Void, Void>() {
            @Nullable
            @Override
            protected Void doInBackground(String... params) {
                if (isAuthenticated() && getDbxFS() != null) {
                    try {
                        DbxFile dbFile = openDbFile(mTodoName);
                        if (dbFile==null) {
                            Log.w(TAG, "Failed to open: " + mTodoName + " tasks not deleted");
                            return null;
                        }
                        ArrayList<String> contents = new ArrayList<String>();
                        contents.addAll(syncGetLines(dbFile));
                        contents.removeAll(stringsToDelete);
                        dbFile.writeString(Util.join(contents, mEol)+mEol);
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new TodoException("Dropbox", e);
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                if (mLines!=null) {
                    mLines.removeAll(stringsToDelete);
                }
            }
        }.execute();
    }

    @Override
    public void move(final String sourcePath, final String targetPath, final ArrayList<String> strings) {

        new AsyncTask<String,Void, Void>() {
            @Nullable
            @Override
            protected Void doInBackground(String... params) {
                DbxFileSystem fs  = getDbxFS();
                if (isAuthenticated() && getDbxFS() != null) {
                    try {
                        Log.v(TAG, "Moving lines from " + sourcePath + " to " + targetPath);
                        DbxPath dbPath = new DbxPath(targetPath);
                        DbxFile destFile;
                        if (fs.exists(dbPath)) {
                            destFile = fs.open(dbPath);
                        } else {
                            destFile = fs.create(dbPath);
                        }
                        ArrayList<String> contents = new ArrayList<String>();
                        destFile.appendString(Util.join(strings, mEol)+mEol);
                        destFile.close();
                        DbxFile srcFile = openDbFile(sourcePath);
                        if (srcFile==null) {
                            Log.w(TAG, "Failed to open: " + sourcePath + " tasks not moved");
                            return null;
                        }
                        contents.addAll(syncGetLines(srcFile));
                        contents.removeAll(strings);
                        srcFile.writeString(Util.join(contents, mEol));
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new TodoException("Dropbox", e);
                    }  catch (DbxPath.InvalidPathException e) {
                        e.printStackTrace();
                        Log.w(TAG, "Invalid archive filename "  + targetPath);
                        throw new TodoException("Dropbox", e);
                    }
                }
                return null;
            }

            @Override
            public void onPostExecute (Void v) {
               if (mLines!=null) {
                   mLines.removeAll(strings);
               }
            }
        }.execute();
    }

    @Override
    public void setEol(String eol) {
        mEol = eol;
    }

    @Override
    public boolean isSyncing() {
        return m_isSyncing;
    }

    @Override
    public boolean write(File path, String contents) {
        if(!isAuthenticated() || isSyncing()) {
            return false;
        }
        DbxFileSystem fs = getDbxFS();
        if (fs == null) {
            return false;
        }

        try {
            DbxFile dbFile = openDbFile(path.getPath());
            dbFile.writeString(contents);
            dbFile.close();
        } catch (DbxException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public boolean initialSyncDone() {
        if (mDbxFs!=null) {
            try {
                return mDbxFs.hasSynced();
            } catch (DbxException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    @Override
    public void invalidateCache() {
        mLines = null;
    }

    @Override
    public int getType() {
        return Constants.STORE_DROPBOX;
    }

    private class FileDialog {
        private static final String PARENT_DIR = "..";
        private String[] fileList;
        private DbxPath currentPath;

        @NotNull
        private ListenerList<FileSelectedListener> fileListenerList = new ListenerList<FileSelectedListener>();
        private final Activity activity;

        /**
         * @param activity  Activity to display the file dialog
         * @param path      File path to start the dialog at
         * @param txtOnly   Show only txt files. Not used for Dropbox
         */
        @SuppressWarnings({"UnusedDeclaration"})
        public FileDialog(Activity activity, @NotNull DbxPath path, boolean txtOnly ) {
            this.activity = activity;
            this.currentPath = path;
            loadFileList(path.getParent());
        }

        /**
         * @return file dialog
         */
        @Nullable
        public Dialog createFileDialog() {
            if (getDbxFS()==null) {
                return null;
            }
            Dialog dialog;
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            String title = currentPath.getName();
            if (Strings.isEmptyOrNull(title)) {
                title = "/";
            }
            if (fileList==null) {
                Toast.makeText(mCtx,"Awaiting first Dropbox Sync", Toast.LENGTH_LONG).show();
                return null;
            }
            builder.setTitle(title);

            builder.setItems(fileList, new DialogInterface.OnClickListener() {
                public void onClick(@NotNull DialogInterface dialog, int which) {
                    String fileChosen = fileList[which];
                    DbxPath chosenFile = getChosenFile(fileChosen);
                    try {
                        if (mDbxFs.getFileInfo(chosenFile).isFolder) {
                            loadFileList(chosenFile);
                            dialog.cancel();
                            dialog.dismiss();
                            showDialog();
                        } else fireFileSelectedEvent(chosenFile);
                    } catch (DbxException e) {
                        e.printStackTrace();
                    }
                }
            });
            dialog = builder.show();
            return dialog;
        }


        public void addFileListener(FileSelectedListener listener) {
            fileListenerList.add(listener);
        }

        /**
         * Show file dialog
         */
        public void showDialog() {
            Dialog d = createFileDialog();
            if(d!=null && !this.activity.isFinishing()) {
                d.show();
            }
        }

        private void fireFileSelectedEvent(@NotNull final DbxPath file) {
            fileListenerList.fireEvent(new ListenerList.FireHandler<FileSelectedListener>() {
                public void fireEvent(@NotNull FileSelectedListener listener) {
                    listener.fileSelected(file.toString());
                }
            });
        }

        private void loadFileList(DbxPath path) {
            this.currentPath = path;
            List<String> f = new ArrayList<String>();
            List<String> d = new ArrayList<String>();
            if (path != DbxPath.ROOT) d.add(PARENT_DIR);

            try {
                if (!getDbxFS().hasSynced()) {
                    fileList = null ;
                    return;
                } else {
                    for (DbxFileInfo fInfo : mDbxFs.listFolder(path)) {
                        if (fInfo.isFolder) {
                            d.add(fInfo.path.getName());
                        } else {
                            f.add(fInfo.path.getName());
                        }
                    }
                }
            } catch (DbxException e) {
                e.printStackTrace();
            }

            Collections.sort(d);
            Collections.sort(f);
            d.addAll(f);
            fileList = d.toArray(new String[d.size()]);
        }

        private DbxPath getChosenFile(@NotNull String fileChosen) {
            if (fileChosen.equals(PARENT_DIR)) return currentPath.getParent();
            else return new DbxPath(currentPath, fileChosen);
        }
    }
}

package nl.mpcjanssen.simpletask.remote;

import android.app.Activity;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface definition of the storage backend used.
 */
public interface FileStoreInterface {
    boolean isAuthenticated();
    @Nullable
    List<String> get(String path);
    void append(String path, List<String> lines);
    void startLogin(Activity caller, int i);
    void startWatching(String path);
    void stopWatching(String path);
    void deauthenticate();
    void browseForNewFile(Activity act, String path, FileSelectedListener listener);
    void update(String mTodoName, List<String> original, List<String> updated);
    void delete(String mTodoName, List<String> strings);
    int getType();
    void move(String sourcePath, String targetPath, ArrayList<String> strings);
    void setEol(String eol);
    boolean isSyncing();
    boolean write(File path, String contents);
    boolean initialSyncDone();

    void invalidateCache();

    public interface FileSelectedListener {
        void fileSelected(String file);
    }
}

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
package nl.mpcjanssen.simpletask.task;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import android.os.FileObserver;
import nl.mpcjanssen.simpletask.util.TaskIo;
import nl.mpcjanssen.simpletask.util.Util;
import android.os.Environment;
import android.util.Log;


/**
 * A task repository for interacting with the local file system
 * 
 * @author Tim Barlotta
 */
public class LocalFileTaskRepository {
	private static final String TAG = LocalFileTaskRepository.class
			.getSimpleName();
    private FileObserver mFileObserver;
    private File todo_file = null;
	
	public File get_todo_file() {
		return todo_file;
	}

	private final TaskBag.Preferences preferences;

    public void setFileObserver (FileObserver observer) {
        this.mFileObserver = observer;
    }

	public LocalFileTaskRepository(String filename , TaskBag.Preferences m_prefs) {
		this.preferences = m_prefs;
        this.mFileObserver = null;
		todo_file = new File(filename);
		try {
			if (!todo_file.exists()) {
				Util.createParentDirectory(todo_file);
				todo_file.createNewFile();
			}
		} catch (IOException e) {
			Log.e (TAG, "Error initializing LocalFile " + e);
		}

	}

	public ArrayList<Task> load() {
		if (!todo_file.exists()) {
            Util.showToastLong(null, todo_file.getAbsolutePath() + " does not exist!");
		} else {
			try {
				return TaskIo.loadTasksFromFile(todo_file);
			} catch (IOException e) {
				Util.showToastLong(null, "Error loading from local file" + e);
			}			
		}
		return null;
	}

	public void store(ArrayList<Task> tasks) {
        if (mFileObserver!=null) {
            mFileObserver.stopWatching();
            Log.v(TAG, "Stop watching " + todo_file + " when storing taskbag");
        }
		TaskIo.writeToFile(tasks, todo_file,
				preferences.isUseWindowsLineBreaksEnabled());
        if (mFileObserver!=null) {
            Log.v(TAG, "Start watching " + todo_file + " storing taskbag done");
            mFileObserver.startWatching();
        }
	}
}

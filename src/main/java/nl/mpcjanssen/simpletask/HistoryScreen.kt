/**
 * @copyright 2014- Mark Janssen)
 */
package nl.mpcjanssen.simpletask

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.widget.ScrollView
import android.widget.TextView
import nl.mpcjanssen.simpletask.dao.Daos
import nl.mpcjanssen.simpletask.util.createCachedDatabase
import nl.mpcjanssen.simpletask.util.shareText
import nl.mpcjanssen.simpletask.util.showToastShort
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class HistoryScreen : ThemedActionBarActivity() {

    private var log: Logger? = null
    private var m_cursor: Cursor? = null
    private var mScroll = 0
    private var m_app: TodoApplication? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = Logger
        m_app = application as TodoApplication

        initCursor()
        val dbFile = databaseFile
        if (dbFile.exists()) {
            var title = title.toString()
            title = title + " (" + dbFile.length() / 1024 + "KB)"
            setTitle(title)
        }
        setContentView(R.layout.history)
        initToolbar()
        displayCurrent()
    }

    private fun initCursor() {
        m_cursor = Daos.initHistoryCursor()
        m_cursor!!.moveToLast()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (m_cursor != null) {
            m_cursor!!.close()
        }

    }

    private fun shareHistory() {
        val shareIntent = Intent(android.content.Intent.ACTION_SEND)
        shareIntent.type = "application/x-sqlite3"
        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
                "Simpletask History Database")
        val dataBase = databaseFile
        try {
            createCachedDatabase(this, dataBase)
            val fileUri = Uri.parse("content://" + CachedFileProvider.AUTHORITY + "/" + dataBase.name)
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri)
        } catch (e: Exception) {
            log!!.warn(TAG, "Failed to create file for sharing", e)
        }

        startActivity(Intent.createChooser(shareIntent, "Share History Database"))

    }

    private val databaseFile: File
        get() = File(Daos.daoSession.database.path)

    fun initToolbar(): Boolean {
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        val inflater = menuInflater
        toolbar.menu.clear()
        inflater.inflate(R.menu.history_menu, toolbar.menu)
        //updateMenu()
        toolbar.setOnMenuItemClickListener(Toolbar.OnMenuItemClickListener { item ->
            when (item.itemId) {
            // Respond to the action bar's Up/Home button
                R.id.menu_prev -> {
                    showPrev()
                    return@OnMenuItemClickListener true
                }
                R.id.menu_next -> {
                    showNext()
                    return@OnMenuItemClickListener true
                }
                R.id.menu_clear_database -> {
                    clearDatabase()
                    return@OnMenuItemClickListener true
                }
                R.id.menu_share -> {
                    if (m_cursor!!.count == 0) {
                        showToastShort(this@HistoryScreen, "Nothing to share")
                    } else {
                        shareText(this@HistoryScreen, "Old todo version", currentFileContents)
                    }
                    return@OnMenuItemClickListener true
                }
                R.id.menu_share_database -> {
                    shareHistory()
                    return@OnMenuItemClickListener true
                }
            }
            true
        })
        return true
    }

    private fun clearDatabase() {
        log!!.info(TAG, "Clearing history database")
        Daos.backupDao.deleteAll()
        initCursor()
        displayCurrent()
    }

    private fun showNext() {
        saveScroll()
        m_cursor!!.moveToNext()
        displayCurrent()
    }

    private fun saveScroll() {
        val sv = findViewById(R.id.scrollbar) as ScrollView
        mScroll = sv.scrollY
    }


    private fun showPrev() {
        saveScroll()
        m_cursor!!.moveToPrevious()
        displayCurrent()
    }

    private fun displayCurrent() {
        var todoContents = "no history"
        var date = 0L
        var name = ""
        if (this.m_cursor!!.count != 0) {
            todoContents = currentFileContents
            date = this.m_cursor!!.getLong(1)

        }
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val fileView = findViewById(R.id.history_view) as TextView

        val nameView = findViewById(R.id.name) as TextView
        val dateView = findViewById(R.id.date) as TextView
        val sv = findViewById(R.id.scrollbar) as ScrollView
        fileView.text = todoContents
        nameView.text = name
        dateView.text = format.format(Date(date))
        sv.scrollY = mScroll
        updateMenu()
    }

    private val currentFileContents: String
        get() = m_cursor!!.getString(0)

    private fun updateMenu() {
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        if (toolbar.menu == null || m_cursor == null) {
            return
        }
        val prev = toolbar.menu!!.findItem(R.id.menu_prev)
        val next = toolbar.menu!!.findItem(R.id.menu_next)
        if (m_cursor!!.isFirst || m_cursor!!.count < 2) {
            prev.isEnabled = false
        } else {
            prev.isEnabled = true
        }
        if (m_cursor!!.isLast || m_cursor!!.count < 2) {
            next.isEnabled = false
        } else {
            next.isEnabled = true
        }
    }

    companion object {
        private val TAG = "HistoryScreen"
    }
}

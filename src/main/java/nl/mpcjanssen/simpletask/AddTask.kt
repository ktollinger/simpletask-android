/**
 * This file is part of Simpletask.
 *
 * @copyright 2013- Mark Janssen
 */
package nl.mpcjanssen.simpletask


import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AlertDialog
import android.support.v7.widget.Toolbar
import android.text.InputType
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import hirondelle.date4j.DateTime
import nl.mpcjanssen.simpletask.dao.Daos
import nl.mpcjanssen.simpletask.dao.gentodo.TodoItem
import nl.mpcjanssen.simpletask.task.Priority
import nl.mpcjanssen.simpletask.task.Task
import nl.mpcjanssen.simpletask.task.TodoList
import nl.mpcjanssen.simpletask.util.*
import java.util.*


class AddTask : ThemedNoActionBarActivity() {

    private val share_text: String? = null

    private val m_backup = ArrayList<TodoItem>()

    private lateinit var textInputField: EditText
    private var m_broadcastReceiver: BroadcastReceiver? = null
    private val log = Logger


    public override fun onCreate(savedInstanceState: Bundle?) {
        log.debug(TAG, "onCreate()")
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
        super.onCreate(savedInstanceState)
        // Config.loadTodoList(true)

        val intent = intent
        val mFilter = ActiveFilter(FilterOptions(luaModule = "addtask"))
        mFilter.initFromIntent(intent)

        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        setContentView(R.layout.add_task)
        val toolbar = findViewById(R.id.addtask_actionbar) as Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp)

        val fab = findViewById(R.id.fab) as FloatingActionButton?
        fab?.setOnClickListener {
            saveTasksAndClose()
        }

        // text
        textInputField = findViewById(R.id.taskText) as EditText
        Config.setEditTextHint(textInputField, R.string.tasktexthint)

        if (share_text != null) {
            textInputField.setText(share_text)
        }

        var iniTask: Task? = null
        setTitle(R.string.addtask)

        val selection = TodoList.selectedTasks
        m_backup.addAll(selection)
        if (selection.size > 0) {
            val preFillString = join(selection.map {it.task.inFileFormat()}, "\n")
            textInputField.setText(preFillString)
            setTitle(R.string.updatetask)
        } else {
            if (textInputField.text.length == 0) {
                iniTask = Task("")
                initTaskWithFilter(iniTask, mFilter)
            }

            if (iniTask != null && iniTask.tags.size == 1) {
                val ps = iniTask.tags
                val project = ps.first()
                if (project != "-") {
                    textInputField.append(" +" + project)
                }
            }


            if (iniTask != null && iniTask.lists.size == 1) {
                val cs = iniTask.lists
                val context = cs.first()
                if (context != "-") {
                    textInputField.append(" @" + context)
                }
            }
        }
        // Listen to enter events, use IME_ACTION_NEXT for soft keyboards
        // like Swype where ENTER keyCode is not generated.

        var inputFlags = InputType.TYPE_CLASS_TEXT

        if (Config.isCapitalizeTasks) {
            inputFlags = inputFlags or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        textInputField.setRawInputType(inputFlags)
        textInputField.imeOptions = EditorInfo.IME_ACTION_NEXT
        textInputField.setOnEditorActionListener { textView, actionId, keyEvent ->
            val hardwareEnterUp = keyEvent != null &&
                    keyEvent.action == KeyEvent.ACTION_UP &&
                    keyEvent.keyCode == KeyEvent.KEYCODE_ENTER
            val hardwareEnterDown = keyEvent != null &&
                    keyEvent.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.keyCode == KeyEvent.KEYCODE_ENTER
            val imeActionNext = actionId == EditorInfo.IME_ACTION_NEXT

            if (imeActionNext || hardwareEnterUp) {
                // Move cursor to end of line
                val position = textInputField.selectionStart
                val remainingText = textInputField.text.toString().substring(position)
                val endOfLineDistance = remainingText.indexOf('\n')
                var endOfLine: Int
                if (endOfLineDistance == -1) {
                    endOfLine = textInputField.length()
                } else {
                    endOfLine = position + endOfLineDistance
                }
                textInputField.setSelection(endOfLine)
                replaceTextAtSelection("\n", false)

                if (Config.isAddTagsCloneTags) {
                    val precedingText = textInputField.text.toString().substring(0, endOfLine)
                    val lineStart = precedingText.lastIndexOf('\n')
                    val line: String
                    if (lineStart != -1) {
                        line = precedingText.substring(lineStart, endOfLine)
                    } else {
                        line = precedingText
                    }
                    val t = Task(line)
                    val tags = LinkedHashSet<String>()
                    for (ctx in t.lists) {
                        tags.add("@" + ctx)
                    }
                    for (prj in t.tags) {
                        tags.add("+" + prj)
                    }
                    replaceTextAtSelection(join(tags, " "), true)
                }
                endOfLine++
                textInputField.setSelection(endOfLine)
            }
            imeActionNext || hardwareEnterDown || hardwareEnterUp
        }

        setWordWrap(Config.isWordWrap)

        val textIndex = 0
        textInputField.setSelection(textIndex)

        // Set button callbacks
        findViewById(R.id.btnContext)?.setOnClickListener { showListMenu() }
        findViewById(R.id.btnProject)?.setOnClickListener { showTagMenu() }
        findViewById(R.id.btnPrio)?.setOnClickListener { showPriorityMenu() }

        findViewById(R.id.btnDue)?.setOnClickListener { insertDate(DateType.DUE) }
        findViewById(R.id.btnThreshold)?.setOnClickListener { insertDate(DateType.THRESHOLD) }

        textInputField.setSelection(textInputField.text.length)
    }


    fun setWordWrap(bool: Boolean) {
        textInputField.setHorizontallyScrolling(!bool)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        val inflater = menuInflater
        inflater.inflate(R.menu.add_task, menu)

        // Set checkboxes
        val menuWordWrap = menu.findItem(R.id.menu_word_wrap)
        menuWordWrap.isChecked = Config.isWordWrap

        val menuPreFill = menu.findItem(R.id.menu_prefill_next)
        menuPreFill.isChecked = Config.isAddTagsCloneTags

        val menuShowHint = menu.findItem(R.id.menu_show_edittext_hint)
        menuShowHint.isChecked = Config.isShowEditTextHint

        val menuCapitalizeTasks = menu.findItem(R.id.menu_capitalize_tasks)
        menuCapitalizeTasks.isChecked = Config.isCapitalizeTasks

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
        // Respond to the action bar's Up/Home button
            android.R.id.home -> {
                finish()
            }
            R.id.menu_prefill_next -> {
                Config.isAddTagsCloneTags =  !Config.isAddTagsCloneTags
                item.isChecked = !item.isChecked
            }
            R.id.menu_word_wrap -> {
                val newVal = !Config.isWordWrap
                Config.isWordWrap = newVal
                setWordWrap(newVal)
                item.isChecked = !item.isChecked
            }
            R.id.menu_capitalize_tasks -> {
                Config.isCapitalizeTasks = !Config.isCapitalizeTasks
                var inputFlags = textInputField.inputType
                if (Config.isCapitalizeTasks) {
                    inputFlags = inputFlags or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                } else {
                    inputFlags = inputFlags and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES.inv()
                }
                textInputField.setRawInputType(inputFlags)
                item.isChecked = !item.isChecked
            }
            R.id.menu_show_edittext_hint -> {
                Config.isShowEditTextHint = !Config.isShowEditTextHint
                Config.setEditTextHint(textInputField, R.string.tasktexthint)
                item.isChecked = !item.isChecked
            }
            R.id.menu_help -> {
                showHelp()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showHelp() {
        val i = Intent(this, HelpScreen::class.java)
        i.putExtra(Constants.EXTRA_HELP_PAGE, getText(R.string.help_add_task))
        startActivity(i)
    }


    private fun saveTasksAndClose() {
        val todoList = TodoList
        // strip line breaks
        textInputField = findViewById(R.id.taskText) as EditText
        val input: String
        input = textInputField.text.toString()

        // Don't add empty tasks
        if (input.trim { it <= ' ' }.isEmpty()) {
            log.info(TAG, "Not adding empty line")
            finish()
            return
        }

        // Update the TodoList with changes
        // Create new tasks
        val enteredTasks = getTasks().dropLastWhile { it.text.isEmpty()}
        log.info(TAG, "Saving ${enteredTasks.size} tasks, updating ${m_backup.size} tasks" )
        for (task in enteredTasks) {
            if (m_backup.size > 0) {
                // Don't modify create date for updated tasks
                m_backup[0].task.update(task.text)
                Daos.todoItemDao.update(m_backup[0])
                m_backup.removeAt(0)
            } else {
                val t: Task
                if (Config.hasPrependDate()) {
                    t = Task(task.text, todayAsString)
                } else {
                    t = task
                }
                todoList.add(t, Config.hasAppendAtEnd())
            }
        }

        // Remove remaining tasks that where selected for update
        for (t in m_backup) {
            todoList.remove(t)
        }

        // Save
        todoList.notifyChanged()
        finish()
    }

    override fun onBackPressed() {
        saveTasksAndClose()
        super.onBackPressed()
    }

    private fun insertDate(dateType: DateType) {
        var titleId = R.string.defer_due
        if (dateType === DateType.THRESHOLD) {
            titleId = R.string.defer_threshold
        }
        val d = createDeferDialog(this, titleId, object : InputDialogListener {
            override fun onClick(input: String) {
                if (input == "pick") {
                    /* Note on some Android versions the OnDateSetListener can fire twice
                     * https://code.google.com/p/android/issues/detail?id=34860
                     * With the current implementation which replaces the dates this is not an
                     * issue. The date is just replaced twice
                     */
                    val today = DateTime.today(TimeZone.getDefault())
                    val dialog = DatePickerDialog(this@AddTask, DatePickerDialog.OnDateSetListener { datePicker, year, month, day ->
                        val date = DateTime.forDateOnly(year, month+1, day)
                        insertDateAtSelection(dateType, date)
                    },
                            today.year!!,
                            today.month!! - 1,
                            today.day!!)

                    val showCalendar = Config.showCalendar()
                    dialog.datePicker.calendarViewShown = showCalendar
                    dialog.datePicker.spinnersShown = !showCalendar
                    dialog.show()
                } else {
                    if (!input.isNullOrEmpty()) {
                        insertDateAtSelection(dateType, addInterval(DateTime.today(TimeZone.getDefault()), input))
                    } else {
                        replaceDate(dateType,input)
                    }
                }
            }
        })
        d.show()
    }

    private fun replaceDate(dateType: DateType, date: String) {
        if (dateType === DateType.DUE) {
            replaceDueDate(date)
        } else {
            replaceThresholdDate(date)
        }
    }

    private fun insertDateAtSelection(dateType: DateType, date: DateTime?) {
        date?.let {
            replaceDate(dateType, date.format("YYYY-MM-DD"))
        }
    }

    private fun showTagMenu() {
        val items = TreeSet<String>()
        val todoList = TodoList
        items.addAll(todoList.projects)
        // Also display contexts in tasks being added
        val tasks = getTasks()
        if (tasks.size == 0) {
            tasks.add(Task(""))
        }
        tasks.forEach {
            items.addAll(it.tags)
        }
        val idx = getCurrentCursorLine()
        val task = getTasks().getOrElse(idx) { Task("") }

        val projects = sortWithPrefix(items, Config.sortCaseSensitive(), null)

        val builder = AlertDialog.Builder(this)
        @SuppressLint("InflateParams") val view = layoutInflater.inflate(R.layout.single_task_tag_dialog, null, false)
        builder.setView(view)
        val lv = view.findViewById(R.id.listView) as ListView
        val ed = view.findViewById(R.id.editText) as EditText
        val lvAdapter = ArrayAdapter(this, R.layout.simple_list_item_multiple_choice,
                projects.toArray<String>(arrayOfNulls<String>(projects.size)))
        lv.adapter = lvAdapter
        lv.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE

        initListViewSelection(lv, lvAdapter, task.tags)

        builder.setPositiveButton(R.string.ok) { dialog, which ->
            val newText = ed.text.toString()
            if (!newText.isEmpty()) {
                task.addTag(newText)
            }
            for (i in 0..lvAdapter.count-1) {
                val tag =  lvAdapter.getItem(i)
                if (lv.isItemChecked(i)) {
                    task.addTag(tag)
                } else {
                    task.removeTag(tag)
                }
            }

            if (idx != -1) {
                tasks[idx] = task
            } else {
                tasks.add(task)
            }
            textInputField.setText(tasks.joinToString("\n") {it.text})
        }
        builder.setNegativeButton(R.string.cancel) { dialog, id -> }
        // Create the AlertDialog
        val dialog = builder.create()
        dialog.setTitle(Config.tagTerm)
        dialog.show()
    }

    private fun showPriorityMenu() {
        val builder = AlertDialog.Builder(this)
        val priorities = Priority.values()
        val priorityCodes = ArrayList<String>()

        for (priority in priorities) {
            priorityCodes.add(priority.code)
        }

        builder.setItems(priorityCodes.toArray<String>(arrayOfNulls<String>(priorityCodes.size))
        ) { arg0, which -> replacePriority(priorities[which].code) }

        // Create the AlertDialog
        val dialog = builder.create()
        dialog.setTitle(R.string.priority_prompt)
        dialog.show()
    }


    private fun getTasks() : MutableList<Task> {
        val input = textInputField.text.toString()
        return input.split("\r\n|\r|\n".toRegex()).map{Task(it)}.toMutableList()
    }

    private fun showListMenu() {
        val items = TreeSet<String>()

        items.addAll(TodoList.contexts)
        // Also display contexts in tasks being added
        val tasks = getTasks()
        if (tasks.size == 0) {
            tasks.add(Task(""))
        }
        tasks.forEach {
            items.addAll(it.lists)
        }
        val idx = getCurrentCursorLine()
        val task = getTasks().getOrElse(idx) { Task("") }

        val lists = sortWithPrefix(items, Config.sortCaseSensitive(), null)

        val builder = AlertDialog.Builder(this)
        @SuppressLint("InflateParams") val view = layoutInflater.inflate(R.layout.single_task_tag_dialog, null, false)
        builder.setView(view)
        val lv = view.findViewById(R.id.listView) as ListView
        val ed = view.findViewById(R.id.editText) as EditText
        val choices = lists.toArray<String>(arrayOfNulls<String>(lists.size))
        val lvAdapter = ArrayAdapter(this, R.layout.simple_list_item_multiple_choice,
                choices)
        lv.adapter = lvAdapter
        lv.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE

        initListViewSelection(lv, lvAdapter, task.lists)

        builder.setPositiveButton(R.string.ok) { dialog, which ->
            val newText = ed.text.toString()
            if (!newText.isEmpty()) {
                task.addList(newText)
            }
            for (i in 0..lvAdapter.count-1) {
                val list =  lvAdapter.getItem(i)
                if (lv.isItemChecked(i)) {
                    task.addList(list)
                } else {
                    task.removeList(list)
                }
            }
            if (idx != -1) {
                tasks[idx] = task
            } else {
                tasks.add(task)
            }
            textInputField.setText(tasks.joinToString("\n") {it.text})
        }
        builder.setNegativeButton(R.string.cancel) { dialog, id -> }
        // Create the AlertDialog
        val dialog = builder.create()
        dialog.setTitle(Config.listTerm)
        dialog.show()
    }

    private fun initListViewSelection(lv: ListView, lvAdapter: ArrayAdapter<String>, selectedItems: Set<String>) {
        for (i in 0..lvAdapter.count - 1) {
            for (item in selectedItems) {
                if (item == lvAdapter.getItem(i)) {
                    lv.setItemChecked(i, true)
                }
            }
        }
    }

    fun getCurrentCursorLine(): Int {
        val selectionStart = textInputField.selectionStart
        if (selectionStart == -1) {
            return -1
        }

        val chars = textInputField.text.subSequence(0, selectionStart)
        var line = 0
        for (i in 0..chars.length - 1) {
            if (chars[i] == '\n') line++

        }
        return line
    }

    private fun replaceDueDate(newDueDate: CharSequence) {
        // save current selection and length
        val start = textInputField.selectionStart
        val length = textInputField.text.length
        val lines = ArrayList<String>()
        Collections.addAll(lines, *textInputField.text.toString().split("\\n".toRegex()).toTypedArray())

        // For some reason the currentLine can be larger than the amount of lines in the EditText
        // Check for this case to prevent any array index out of bounds errors
        var currentLine = getCurrentCursorLine()
        if (currentLine > lines.size - 1) {
            currentLine = lines.size - 1
        }
        if (currentLine != -1) {
            val t = Task(lines[currentLine])
            t.dueDate = newDueDate.toString()
            lines[currentLine] = t.inFileFormat()
            textInputField.setText(join(lines, "\n"))
        }
        restoreSelection(start, length, false)
    }

    private fun replaceThresholdDate(newThresholdDate: CharSequence) {
        // save current selection and length
        val start = textInputField.selectionStart
        val length = textInputField.text.length
        val lines = ArrayList<String>()
        Collections.addAll(lines, *textInputField.text.toString().split("\\n".toRegex()).toTypedArray())

        // For some reason the currentLine can be larger than the amount of lines in the EditText
        // Check for this case to prevent any array index out of bounds errors
        var currentLine = getCurrentCursorLine()
        if (currentLine > lines.size - 1) {
            currentLine = lines.size - 1
        }
        if (currentLine != -1) {
            val t = Task(lines[currentLine])
            t.thresholdDate = newThresholdDate.toString()
            lines[currentLine] = t.inFileFormat()
            textInputField.setText(join(lines, "\n"))
        }
        restoreSelection(start, length, false)
    }

    private fun restoreSelection(location: Int, oldLength: Int, moveCursor: Boolean) {
        var newLocation = location
        val newLength = textInputField.text.length
        val deltaLength = newLength - oldLength
        // Check if we want the cursor to move by delta (for priority changes)
        // or not (for due and threshold changes
        if (moveCursor) {
            newLocation += deltaLength
        }

        // Don't go out of bounds
        newLocation = Math.min(newLocation, newLength)
        newLocation = Math.max(0, newLocation)
        textInputField.setSelection(newLocation, newLocation)
    }

    private fun replacePriority(newPriority: CharSequence) {
        // save current selection and length
        val start = textInputField.selectionStart
        val end = textInputField.selectionEnd
        log.debug(TAG, "Current selection: $start-$end")
        val length = textInputField.text.length
        val lines = ArrayList<String>()
        Collections.addAll(lines, *textInputField.text.toString().split("\\n".toRegex()).toTypedArray())

        // For some reason the currentLine can be larger than the amount of lines in the EditText
        // Check for this case to prevent any array index out of bounds errors
        var currentLine = getCurrentCursorLine()
        if (currentLine > lines.size - 1) {
            currentLine = lines.size - 1
        }
        if (currentLine != -1) {
            val t = Task(lines[currentLine])
            log.debug(TAG, "Changing priority from " + t.priority.toString() + " to " + newPriority.toString())
            t.priority = Priority.toPriority(newPriority.toString())
            lines[currentLine] = t.inFileFormat()
            textInputField.setText(join(lines, "\n"))
        }
        restoreSelection(start, length, true)
    }

    private fun replaceTextAtSelection(newText: CharSequence, spaces: Boolean) {
        var text = newText
        val start = textInputField.selectionStart
        val end = textInputField.selectionEnd
        if (start == end && start != 0 && spaces) {
            // no selection prefix with space if needed
            if (textInputField.text[start - 1] != ' ') {
                text = " " + text
            }
        }
        textInputField.text.replace(Math.min(start, end), Math.max(start, end),
                text, 0, text.length)
    }

    companion object {
        private val TAG = "AddTask"
    }
}

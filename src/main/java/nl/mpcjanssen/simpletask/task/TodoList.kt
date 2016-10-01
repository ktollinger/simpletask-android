/**
 * This file is part of Todo.txt Touch, an Android app for managing your todo.txt file (http://todotxt.com).
 *
 *
 * Copyright (c) 2009-2012 Todo.txt contributors (http://todotxt.com)
 *
 *
 * LICENSE:
 *
 *
 * Todo.txt Touch is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 2 of the License, or (at your option) any
 * later version.
 *
 *
 * Todo.txt Touch is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 *
 * You should have received a copy of the GNU General Public License along with Todo.txt Touch.  If not, see
 * //www.gnu.org/licenses/>.

 * @author Todo.txt contributors @yahoogroups.com>
 * *
 * @license http://www.gnu.org/licenses/gpl.html
 * *
 * @copyright 2009-2012 Todo.txt contributors (http://todotxt.com)
 */
package nl.mpcjanssen.simpletask.task

import android.app.Activity
import android.content.Intent

import de.greenrobot.dao.query.Query
import nl.mpcjanssen.simpletask.*
import nl.mpcjanssen.simpletask.dao.Daos
import nl.mpcjanssen.simpletask.dao.gen.TodoFile
import nl.mpcjanssen.simpletask.dao.gentodo.TodoItem
import nl.mpcjanssen.simpletask.dao.gentodo.TodoItemDao

import nl.mpcjanssen.simpletask.sort.MultiComparator
import nl.mpcjanssen.simpletask.util.*

import java.util.*
import kotlin.comparisons.compareBy


/**
 * Implementation of the in memory representation of the todo list

 * @author Mark Janssen
 */
object TodoList {
    private val log: Logger

    private var mLists: ArrayList<String>? = null
    private var mTags: ArrayList<String>? = null
    var todoItemsDao = Daos.todoItemDao

    init {

        log = Logger
    }

    fun hasPendingAction () : Boolean {
        return ActionQueue.hasPending()
    }

    // Wait until there are no more pending actions
    fun settle() {
        while (hasPendingAction()) {
            Thread.sleep(10)
        }
    }

    val todoItems: List<TodoItem>
    get() = todoItemsDao.loadAll()

    fun firstLine(): Long {
        return todoItems.map { it -> it.line }.min() ?: 0
    }

    fun lastLine(): Long {
        return todoItems.map { it -> it.line }.max() ?: 1
    }

    fun add(t: TodoItem, atEnd: Boolean) {
        ActionQueue.add("Add task", Runnable {
            log.debug(TAG, "Adding task of length {} into {} atEnd " + t.task.inFileFormat().length + " " + atEnd)
            if (atEnd) {
                t.line = lastLine() + 1
            } else {
                t.line = firstLine() - 1
            }
            todoItemsDao.insert(t)
        })
    }

    fun add(t: Task, atEnd: Boolean, select: Boolean = false) {
        val newItem = TodoItem(0, t, select)
        add(newItem, atEnd)
    }


    fun remove(item: TodoItem) {
        ActionQueue.add("Remove", Runnable {
            todoItemsDao.delete(item)
        })
    }


    fun size(): Int {
        return todoItems.size
    }


    val priorities: ArrayList<Priority>
        get() {
            val res = HashSet<Priority>()
            todoItems.forEach {
                res.add(it.task.priority)
            }
            val ret = ArrayList(res)
            Collections.sort(ret)
            return ret
        }

    val contexts: ArrayList<String>
        get() {
            val lists = mLists
            if (lists != null) {
                return lists
            }
            val res = HashSet<String>()
            todoItems.forEach {
                res.addAll(it.task.lists)
            }
            val newLists = ArrayList<String>()
            newLists.addAll(res)
            mLists = newLists
            return newLists
        }

    val projects: ArrayList<String>
        get() {
            val tags = mTags
            if (tags != null) {
                return tags
            }
            val res = HashSet<String>()
            todoItems.forEach {
                res.addAll(it.task.tags)
            }
            val newTags = ArrayList<String>()
            newTags.addAll(res)
            mTags = newTags
            return newTags
        }


    val decoratedContexts: ArrayList<String>
        get() = prefixItems("@", contexts)

    val decoratedProjects: ArrayList<String>
        get() = prefixItems("+", projects)


    fun undoComplete(items: List<TodoItem>) {
        ActionQueue.add("Uncomplete", Runnable {
            items.forEach {
                it.task.markIncomplete()
            }
            todoItemsDao.updateInTx(items)
        })
    }

    fun complete(item: TodoItem,
                 keepPrio: Boolean,
                 extraAtEnd: Boolean) {

        ActionQueue.add("Complete", Runnable {
            val task = item.task
            val extra = task.markComplete(todayAsString)
            todoItemsDao.update(item)
            if (extra != null) {
                add(extra, extraAtEnd)
            }
            if (!keepPrio) {
                task.priority = Priority.NONE
            }
        })
    }


    fun prioritize(items: List<TodoItem>, prio: Priority) {
        ActionQueue.add("Complete", Runnable {
            for (item in items) {
                item.task.priority = prio
            }
            todoItemsDao.updateInTx(items)
        })

    }

    fun defer(deferString: String, items: List<TodoItem>, dateType: DateType) {
        ActionQueue.add("Defer", Runnable {
            items.forEach {
                val taskToDefer = it.task
                when (dateType) {
                    DateType.DUE -> taskToDefer.deferDueDate(deferString, todayAsString)
                    DateType.THRESHOLD -> taskToDefer.deferThresholdDate(deferString, todayAsString)
                }
            }
            todoItemsDao.updateInTx(items)
        })
    }

    val selectionQuery : Query<TodoItem> = todoItemsDao.queryBuilder().where(TodoItemDao.Properties.Selected.eq(true)).build()
    var selectedTasks: List<TodoItem> = ArrayList()
        get() {
            return selectionQuery.list()
        }


    fun notifyChanged() {
        log.info(TAG, "Handler: Queue notifychanged")
        val contents = todoItems.sortedWith(compareBy { it.line }).map { it.task.inFileFormat() }.joinToString(separator = "\n")
        val fileToBackup = TodoFile(contents, Date())
        Daos.backup(fileToBackup)
        ActionQueue.add("Notified changed", Runnable {
            mLists = null
            mTags = null
            broadcastRefreshUI(TodoApplication.app.localBroadCastManager)
        })
    }

    fun startAddTaskActivity(act: Activity) {
        ActionQueue.add("Add/Edit tasks", Runnable {
            log.info(TAG, "Starting addTask activity")
            val intent = Intent(act, AddTask::class.java)
            act.startActivity(intent)
        })
    }

    fun getSortedTasks(filter: ActiveFilter, sorts: ArrayList<String>, caseSensitive: Boolean): List<TodoItem> {
        val filteredTasks = filter.apply(todoItems)
        val comp = MultiComparator(sorts, TodoApplication.app.today, caseSensitive, filter.createIsThreshold)
        Collections.sort(filteredTasks, comp)
        return filteredTasks
    }

    fun update(items : List<TodoItem>) {
        todoItemsDao.updateInTx(items)
    }

    fun isSelected(item: TodoItem): Boolean {
        return item.selected
    }

    fun numSelected(): Long {
        return todoItemsDao.queryBuilder().where(TodoItemDao.Properties.Selected.eq(true)).count()
    }


    internal val TAG = TodoList::class.java.simpleName


    fun selectTodoItems(items: List<TodoItem>) {
        items.forEach {
            it.selected = true
        }
        todoItemsDao.updateInTx(items)
    }

    fun selectTodoItem(item: TodoItem) {
        selectTodoItems(listOf(item))
    }


    fun unSelectTodoItem(item: TodoItem) {
        unSelectTodoItems(listOf(item))
    }

    fun unSelectTodoItems(items: List<TodoItem>) {
        items.forEach {
            it.selected = false
        }
        todoItemsDao.updateInTx(items)
    }

    fun clearSelection() {
        unSelectTodoItems(selectedTasks)
    }

    fun getTaskCount(): Long {
        val items = todoItems
        return items.filter { it.task.inFileFormat().isNotBlank() }.size.toLong()
    }

    fun selectLine(line : Long ) {
        val item = todoItemsDao.load(line)
        item.selected = true
        todoItemsDao.update(item)
    }


}


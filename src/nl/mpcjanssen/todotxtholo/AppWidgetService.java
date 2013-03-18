package nl.mpcjanssen.todotxtholo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import nl.mpcjanssen.todotxtholo.sort.AlphabeticalComparator;
import nl.mpcjanssen.todotxtholo.sort.CompletedComparator;
import nl.mpcjanssen.todotxtholo.sort.ContextComparator;
import nl.mpcjanssen.todotxtholo.sort.MultiComparator;
import nl.mpcjanssen.todotxtholo.sort.PriorityComparator;
import nl.mpcjanssen.todotxtholo.sort.ProjectComparator;
import nl.mpcjanssen.todotxtholo.task.ByContextFilter;
import nl.mpcjanssen.todotxtholo.task.ByPriorityFilter;
import nl.mpcjanssen.todotxtholo.task.ByProjectFilter;
import nl.mpcjanssen.todotxtholo.task.ByTextFilter;
import nl.mpcjanssen.todotxtholo.task.Priority;
import nl.mpcjanssen.todotxtholo.task.Task;
import nl.mpcjanssen.todotxtholo.task.TaskBag;
import nl.mpcjanssen.todotxtholo.task.TaskFilter;
import nl.mpcjanssen.todotxtholo.util.Strings;
import nl.mpcjanssen.todotxtholo.util.Util;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import nl.mpcjanssen.todotxtholo.task.*;

public class AppWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        // TODO Auto-generated method stub
        return new AppWidgetRemoteViewsFactory((TodoApplication)getApplication(), intent);
    }

}

class AppWidgetRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    final static String TAG = AppWidgetRemoteViewsFactory.class.getSimpleName();
    private ArrayList<String> m_contexts;
    private ArrayList<String> m_projects;
    private ArrayList<Priority> m_prios;
    private boolean m_priosNot;
    private boolean m_projectsNot;
    private boolean m_contextsNot;
    private String m_sort;

    private Context mContext;
    private int widgetId;
    private SharedPreferences preferences;
    private TodoApplication application;
    private TaskBag taskBag;
    ArrayList<Task> visibleTasks = new ArrayList<Task>();

    public AppWidgetRemoteViewsFactory(TodoApplication application, Intent intent) {
        // TODO Auto-generated constructor stub
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        Log.v(TAG, "Creating view for widget: " + widgetId);
        mContext =  TodoApplication.getAppContext();
        preferences = mContext.getSharedPreferences(""+widgetId, 0);
        m_contexts = new ArrayList<String>();
        m_contexts.addAll(preferences.getStringSet(Constants.INTENT_CONTEXTS_FILTER, new HashSet<String>()));
        Log.v(TAG, "contexts: " + m_contexts);
        ArrayList<String> prio_strings = new ArrayList<String>();
        prio_strings.addAll(preferences.getStringSet(Constants.INTENT_PRIORITIES_FILTER, new HashSet<String>()));
        m_prios = Priority.toPriority(prio_strings);
        Log.v(TAG, "prios: " + m_prios);
        m_projects = new ArrayList<String>();
        m_projects.addAll(preferences.getStringSet(Constants.INTENT_PROJECTS_FILTER, new HashSet<String>()));
        Log.v(TAG, "tags: " + m_projects);
        m_contextsNot = preferences.getBoolean(Constants.INTENT_CONTEXTS_FILTER_NOT,false);
        m_projectsNot = preferences.getBoolean(Constants.INTENT_PROJECTS_FILTER_NOT,false);
        m_priosNot = preferences.getBoolean(Constants.INTENT_PRIORITIES_FILTER_NOT,false);
        m_sort = preferences.getString(Constants.INTENT_ACTIVE_SORT,"");
        application.initTaskBag();
        taskBag = application.getTaskBag();
        setFilteredTasks();

    }

    private Intent createFilterIntent() {
        Intent target = new Intent();
        target.putExtra(Constants.INTENT_CONTEXTS_FILTER, Util.join(m_contexts, "\n"));
        target.putExtra(Constants.INTENT_CONTEXTS_FILTER_NOT, m_contextsNot);
        target.putExtra(Constants.INTENT_PROJECTS_FILTER, Util.join(m_projects, "\n"));
        target.putExtra(Constants.INTENT_PROJECTS_FILTER_NOT, m_projectsNot);
        target.putExtra(Constants.INTENT_PRIORITIES_FILTER, Util.join(m_prios, "\n"));
        target.putExtra(Constants.INTENT_PRIORITIES_FILTER_NOT, m_priosNot);
        target.putExtra(Constants.INTENT_ACTIVE_SORT, m_sort);
        return target;
    }

    void setFilteredTasks() {
        Log.v(TAG, "setFilteredTasks called");
        AndFilter filter = new AndFilter();
        visibleTasks.clear();
        for (Task t : taskBag.getTasks()) {
            if (filter.apply(t) && !t.isCompleted()) {
                visibleTasks.add(t);
            }
        }
        Collections.sort(visibleTasks,getActiveSort());
    }

    @Override
    public int getCount() {
        // TODO Auto-generated method stub
        return visibleTasks.size();
    }

    @Override
    public long getItemId(int arg0) {
        // TODO Auto-generated method stub
        return arg0;
    }

    @Override
    public RemoteViews getLoadingView() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RemoteViews getViewAt(int position) {

        final int itemId = R.layout.widget_list_item;
        RemoteViews rv = new RemoteViews(mContext.getPackageName(), itemId);
        rv.setTextViewText(R.id.widget_item_text, visibleTasks.get(position).getText());
        rv.setOnClickFillInIntent(R.id.widget_item_text, createFilterIntent());
        return rv;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public void onCreate() {
        // TODO Auto-generated method stub

    }

    @Override
    public void onDataSetChanged() {
        // TODO Auto-generated method stub
        Log.v(TAG, "Data set changed, refresh");
        setFilteredTasks();

    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub

    }

    private MultiComparator getActiveSort() {
        List<Comparator<?>> comparators = new ArrayList<Comparator<?>>();
        // Sort completed last
        comparators.add(new CompletedComparator());
        if (m_sort == null) {
            m_sort = application.getDefaultSort();
        }
        if (m_sort.equals("sort_file_order")) {
            // no additional sorting
        } else if (m_sort.equals("sort_file_reversed")) {
            comparators.add(Collections.reverseOrder());
        } else if (m_sort.equals("sort_by_context")) {
            comparators.add(new ContextComparator());
            comparators.add(new AlphabeticalComparator());
        } else if (m_sort.equals("sort_by_project")) {
            comparators.add(new ProjectComparator());
            comparators.add(new AlphabeticalComparator());
        } else if (m_sort.equals("sort_alphabetical")) {
            comparators.add(new AlphabeticalComparator());
            comparators.add(new AlphabeticalComparator());
        } else if (m_sort.equals("sort_by_prio")) {
            comparators.add(new PriorityComparator());
            comparators.add(new AlphabeticalComparator());
        } else {
            Log.w(TAG, "Unknown sort: " + m_sort);
        }
        return (new MultiComparator(comparators));
    }

    private class AndFilter {
        private ArrayList<TaskFilter> filters = new ArrayList<TaskFilter>();

        public void addFilter(TaskFilter filter) {
            if (filter != null) {
                filters.add(filter);
            }
        }

        private boolean apply(Task input) {
            filters.clear();
            if (m_prios.size() > 0) {
                addFilter(new ByPriorityFilter(m_prios, m_priosNot));
            }

            if (m_contexts.size() > 0) {
                addFilter(new ByContextFilter(m_contexts, m_contextsNot));
            }
            if (m_projects.size() > 0) {
                addFilter(new ByProjectFilter(m_projects, m_projectsNot));
            }

            for (TaskFilter f : filters) {
                if (!f.apply(input)) {
                    return false;
                }
            }
            return true;
        }
    }

}

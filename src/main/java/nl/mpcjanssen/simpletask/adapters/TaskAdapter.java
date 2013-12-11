package nl.mpcjanssen.simpletask.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.SpannableString;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import nl.mpcjanssen.simpletask.ActiveFilter;
import nl.mpcjanssen.simpletask.R;
import nl.mpcjanssen.simpletask.Simpletask;
import nl.mpcjanssen.simpletask.TodoApplication;
import nl.mpcjanssen.simpletask.sort.MultiComparator;
import nl.mpcjanssen.simpletask.task.Task;
import nl.mpcjanssen.simpletask.task.TaskBag;
import nl.mpcjanssen.simpletask.util.Strings;
import nl.mpcjanssen.simpletask.util.Util;

/**
 * Created by a156712 on 11-12-13.
 */


public class TaskAdapter extends BaseAdapter implements ListAdapter,
        Filterable {
    Simpletask m_act;
    ActiveFilter m_filter;
    TaskBag m_taskBag;
    ArrayList<Task> visibleTasks = new ArrayList<Task>();
    Set<DataSetObserver> obs = new HashSet<DataSetObserver>();
    SparseArray<String> headerTitles = new SparseArray<String>();
    SparseIntArray positionToIndex = new SparseIntArray();
    SparseIntArray indexToPosition = new SparseIntArray();
    int size = 0;
    private LayoutInflater m_inflater;
    final static String TAG = TaskAdapter.class.getSimpleName();
    final TodoApplication m_app;

    private static class ViewHolder {
        private TextView tasktext;
        private TextView taskage;
        private TextView taskdue;
        private TextView taskthreshold;
        private CheckBox cbCompleted;
    }

    public TaskAdapter(Simpletask act, int textViewResourceId,
                       LayoutInflater inflater, ListView view, TaskBag taskbag) {
        this.m_act = act;
        this.m_inflater = inflater;
        this.m_taskBag = taskbag;
        this.m_app = (TodoApplication) m_act.getApplication();
    }

    public void setFilteredTasks(ActiveFilter filter, boolean reload) {
        m_filter = filter;
        Log.v(TAG, "setFilteredTasks called, reload: " + reload);
        if (reload) {
            m_taskBag.reload();
            // Update lists in side drawer
            // Set the adapter for the list view
        }

        visibleTasks.clear();
        visibleTasks.addAll(filter.apply(m_taskBag.getTasks()));
        ArrayList<String> sorts = filter.getSort();
        Collections.sort(visibleTasks, MultiComparator.create(sorts));
        positionToIndex.clear();
        indexToPosition.clear();
        headerTitles.clear();
        String header = "";
        String newHeader = "";
        int index = 0;
        int position = 0;
        int firstGroupSortIndex = 0;

        if (sorts.size() > 1 && sorts.get(0).contains("completed")
                || sorts.get(0).contains("future")) {
            firstGroupSortIndex++;
            if (sorts.size() > 2 && sorts.get(1).contains("completed")
                    || sorts.get(1).contains("future")) {
                firstGroupSortIndex++;
            }
        }
        String firstSort = sorts.get(firstGroupSortIndex);
        for (Task t : visibleTasks) {
            newHeader = t.getHeader(firstSort, m_act.getString(R.string.no_header));
            if (!header.equals(newHeader)) {
                header = newHeader;
                // Log.v(TAG, "Start of header: " + header +
                // " at position: " + position);
                headerTitles.put(position, header);
                positionToIndex.put(position, -1);
                position++;
            }

            positionToIndex.put(position, index);
            indexToPosition.put(index, position);
            index++;
            position++;
        }
        size = position;
        for (DataSetObserver ob : obs) {
            ob.onChanged();
        }
        m_act.updateFilterBar();
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        obs.add(observer);
    }

    /*
 ** Get the adapter position for task
 */
    public int getPosition (Task task) {
        int index = visibleTasks.indexOf(task);
        if  (index==-1 || indexToPosition.indexOfKey(index)==-1) {
            return index;
        }
        return indexToPosition.valueAt(indexToPosition.indexOfKey(index));
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        obs.remove(observer);
    }

    @Override
    public int getCount() {
        return size;
    }

    @Override
    public Task getItem(int position) {
        if (positionToIndex.get(position,-1) == -1) {
            return null;
        }
        return visibleTasks.get(positionToIndex.get(position));
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true; // To change body of implemented methods use File |
        // Settings | File Templates.
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (headerTitles.get(position,null) != null) {
            convertView = m_inflater.inflate(R.layout.list_header, null);
            TextView t = (TextView) convertView
                    .findViewById(R.id.list_header_title);
            t.setText(headerTitles.get(position));

        } else {
            final ViewHolder holder;
            if (convertView == null) {
                convertView = m_inflater.inflate(R.layout.list_item, null);
                holder = new ViewHolder();
                holder.tasktext = (TextView) convertView
                        .findViewById(R.id.tasktext);
                holder.taskage = (TextView) convertView
                        .findViewById(R.id.taskage);
                holder.taskdue = (TextView) convertView
                        .findViewById(R.id.taskdue);
                holder.taskthreshold = (TextView) convertView
                        .findViewById(R.id.taskthreshold);
                holder.cbCompleted = (CheckBox) convertView
                        .findViewById(R.id.checkBox);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            Task task;
            task = getItem(position);

            if (task != null) {
                SpannableString ss = new SpannableString(
                        task.datelessScreenFormat());

                ArrayList<String> colorizeStrings = new ArrayList<String>();
                for (String context : task.getLists()) {
                    colorizeStrings.add("@" + context);
                }
                Util.setColor(ss, Color.GRAY, colorizeStrings);
                colorizeStrings.clear();
                for (String project : task.getTags()) {
                    colorizeStrings.add("+" + project);
                }
                Util.setColor(ss, Color.GRAY, colorizeStrings);

                Resources res = m_act.getResources();
                int prioColor;
                switch (task.getPriority()) {
                    case A:
                        prioColor = res.getColor(android.R.color.holo_red_dark);
                        break;
                    case B:
                        prioColor = res.getColor(android.R.color.holo_orange_dark);
                        break;
                    case C:
                        prioColor = res.getColor(android.R.color.holo_green_dark);
                        break;
                    case D:
                        prioColor = res.getColor(android.R.color.holo_blue_dark);
                        break;
                    default:
                        prioColor = res.getColor(android.R.color.darker_gray);
                }
                Util.setColor(ss, prioColor, task.getPriority()
                        .inFileFormat());
                holder.tasktext.setText(ss);
                final ArrayList<Task> tasks = new ArrayList<Task>();
                tasks.add(task);
                if (task.isCompleted()) {
                    // Log.v(TAG, "Striking through " + task.getText());
                    holder.tasktext.setPaintFlags(holder.tasktext
                            .getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    holder.taskage.setPaintFlags(holder.taskage
                            .getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    holder.cbCompleted.setChecked(true);
                    holder.cbCompleted.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            m_act.undoCompleteTasks(tasks);
                            m_act.finishActionmode();
                        }
                    });
                } else {
                    holder.tasktext
                            .setPaintFlags(holder.tasktext.getPaintFlags()
                                    & ~Paint.STRIKE_THRU_TEXT_FLAG);
                    holder.taskage
                            .setPaintFlags(holder.taskage.getPaintFlags()
                                    & ~Paint.STRIKE_THRU_TEXT_FLAG);
                    holder.cbCompleted.setChecked(false);
                    holder.cbCompleted.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            m_act.completeTasks(tasks);
                            m_act.finishActionmode();
                        }
                    });
                }




                String relAge = task.getRelativeAge();
                SpannableString relDue = task.getRelativeDueDate(res, m_app.hasColorDueDates());
                String relThres = task.getRelativeThresholdDate();
                boolean anyDateShown = false;
                if (!Strings.isEmptyOrNull(relAge)) {
                    holder.taskage.setText(relAge);
                    anyDateShown = true;
                } else {
                    holder.taskage.setText("");
                }
                if (relDue!=null) {
                    anyDateShown = true;
                    holder.taskdue.setText(relDue);
                } else {
                    holder.taskdue.setText("");
                }
                if (!Strings.isEmptyOrNull(relThres)) {
                    anyDateShown = true;
                    holder.taskthreshold.setText(relThres);
                } else {
                    holder.taskthreshold.setText("");
                }
                LinearLayout datesBar = (LinearLayout)convertView
                        .findViewById(R.id.datebar);
                if (!anyDateShown || task.isCompleted()) {
                    datesBar.setVisibility(View.GONE);
                    holder.tasktext.setPadding(
                            holder.tasktext.getPaddingLeft(),
                            holder.tasktext.getPaddingTop(),
                            holder.tasktext.getPaddingRight(), 4);
                } else {
                    datesBar.setVisibility(View.VISIBLE);
                    holder.tasktext.setPadding(
                            holder.tasktext.getPaddingLeft(),
                            holder.tasktext.getPaddingTop(),
                            holder.tasktext.getPaddingRight(), 0);
                }
            }
        }
        return convertView;
    }

    @Override
    public int getItemViewType(int position) {
        if (headerTitles.get(position,null) != null) {
            return 0;
        } else {
            return 1;
        }
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public boolean isEmpty() {
        return visibleTasks.size() == 0;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return headerTitles.get(position, null) == null;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(
                    CharSequence charSequence) {
                m_filter.setSearch(charSequence.toString());
                //Log.v(TAG, "performFiltering: " + charSequence.toString());
                return null;
            }

            @Override
            protected void publishResults(CharSequence charSequence,
                                          FilterResults filterResults) {
                setFilteredTasks(m_filter, false);
            }
        };
    }
}
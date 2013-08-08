package nl.mpcjanssen.simpletask;

import android.content.Context;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.SpannableString;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import nl.mpcjanssen.simpletask.sort.MultiComparator;
import nl.mpcjanssen.simpletask.task.Task;
import nl.mpcjanssen.simpletask.task.TaskBag;
import nl.mpcjanssen.simpletask.util.Strings;
import nl.mpcjanssen.simpletask.util.Util;

public class TaskAdapter extends BaseAdapter implements ListAdapter,
        Filterable {

    private final TaskBag taskBag;
    private final ActiveFilter mFilter;
    private final Resources resources;
    private LayoutInflater m_inflater;
    ArrayList<Task> visibleTasks = new ArrayList<Task>();
    Set<DataSetObserver> obs = new HashSet<DataSetObserver>();
    SparseArray<String> headerTitles = new SparseArray<String>();
    SparseArray<Integer> positionToIndex = new SparseArray<Integer>();
    SparseArray<Integer> indexToPosition = new SparseArray<Integer>();
    int size = 0;

    public TaskAdapter(
            TaskBag taskBag, ActiveFilter mFilter,
                       LayoutInflater inflater, Resources res) {
        this.m_inflater = inflater;
        this.taskBag = taskBag;
        this.mFilter = mFilter;
        this.resources = res;
    }

    void setFilteredTasks(boolean reload) {
        if (reload) {
            taskBag.reload();
            // Update lists in side drawer
            // Set the adapter for the list view
        }

        visibleTasks.clear();
        visibleTasks.addAll(mFilter.apply(taskBag.getTasks(), true));
        ArrayList<String> sorts = mFilter.getSort();
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
            newHeader = t.getHeader(firstSort, resources.getString(R.string.no_header));
            if (!header.equals(newHeader)) {
                header = newHeader;
                // Log.v(TAG, "Start of header: " + header +
                // " at position: " + position);
                headerTitles.put(position, header);
                positionToIndex.put(position, null);
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
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        obs.add(observer);
        return;
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
        return;
    }

    @Override
    public int getCount() {
        return size;
    }

    @Override
    public Task getItem(int position) {
        if (positionToIndex.get(position) == null) {
            return null;
        }
        return visibleTasks.get(positionToIndex.get(position).intValue());
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
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            Task task;
            task = getItem(position);

            if (task != null) {
                SpannableString ss = new SpannableString(
                        task.inScreenFormat());

                ArrayList<String> colorizeStrings = new ArrayList<String>();
                for (String context : task.getContexts()) {
                    colorizeStrings.add("@" + context);
                }
                Util.setColor(ss, Color.GRAY, colorizeStrings);
                colorizeStrings.clear();
                for (String project : task.getProjects()) {
                    colorizeStrings.add("+" + project);
                }
                Util.setColor(ss, Color.GRAY, colorizeStrings);

                int prioColor;
                switch (task.getPriority()) {
                    case A:
                        prioColor = resources.getColor(R.color.green);
                        break;
                    case B:
                        prioColor = resources.getColor(R.color.blue);
                        break;
                    case C:
                        prioColor = resources.getColor(R.color.orange);
                        break;
                    case D:
                        prioColor = resources.getColor(R.color.gold);
                        break;
                    default:
                        prioColor = holder.tasktext.getCurrentTextColor();
                }
                Util.setColor(ss, prioColor, task.getPriority()
                        .inFileFormat());
                holder.tasktext.setText(ss);

                if (task.isCompleted()) {
                    // Log.v(TAG, "Striking through " + task.getText());
                    holder.tasktext.setPaintFlags(holder.tasktext
                            .getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    holder.taskage.setPaintFlags(holder.taskage
                            .getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    holder.tasktext
                            .setPaintFlags(holder.tasktext.getPaintFlags()
                                    & ~Paint.STRIKE_THRU_TEXT_FLAG);
                    holder.taskage
                            .setPaintFlags(holder.taskage.getPaintFlags()
                                    & ~Paint.STRIKE_THRU_TEXT_FLAG);
                }

                if (!Strings.isEmptyOrNull(task.getRelativeAge())) {
                    holder.taskage.setText(task.getRelativeAge());
                    holder.taskage.setVisibility(View.VISIBLE);
                } else {
                    holder.tasktext.setPadding(
                            holder.tasktext.getPaddingLeft(),
                            holder.tasktext.getPaddingTop(),
                            holder.tasktext.getPaddingRight(), 4);
                    holder.taskage.setText("");
                    holder.taskage.setVisibility(View.GONE);
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
        if (headerTitles.get(position,null) != null) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(
                    CharSequence charSequence) {
                mFilter.setSearch(charSequence.toString());
                //Log.v(TAG, "performFiltering: " + charSequence.toString());
                return null;
            }

            @Override
            protected void publishResults(CharSequence charSequence,
                                          FilterResults filterResults) {
                setFilteredTasks(false);
            }
        };
    }

    private static class ViewHolder {
        private TextView tasktext;
        private TextView taskage;
    }
}
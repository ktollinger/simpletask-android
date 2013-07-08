package nl.mpcjanssen.simpletask;

import android.app.Fragment;
import android.app.ListFragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.mobeta.android.dslv.DragSortListView;

import nl.mpcjanssen.simpletask.util.Strings;
import nl.mpcjanssen.todotxtholo.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FilterSortFragment extends ListFragment {
    
    private final static String STATE_SELECTED = "selectedItem";

    private ArrayList<String> originalItems;
    private ListView lv;
    SortItemAdapter adapter;
    ArrayList<String> directions = new ArrayList<String>();
    ArrayList<String> adapterList = new ArrayList<String>();

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        super.onCreateView(inflater,container,savedInstanceState);
        Bundle arguments = getArguments();
        if (savedInstanceState != null) {
            originalItems = savedInstanceState.getStringArrayList(STATE_SELECTED);
        } else {
            originalItems = arguments.getStringArrayList(Constants.ACTIVE_SORTS);
        }
        adapterList.clear();
        LinearLayout layout = (LinearLayout) inflater.inflate(R.layout.single_filter,
                container, false);

        String[] values = getResources().getStringArray(R.array.sort);
        String[] keys = getResources().getStringArray(R.array.sortKeys);
        for (String item : originalItems) {
            String[] parts =  item.split("!");
            String sortType ;
            String sortDirection;
            if (parts.length==1) {
               sortType = parts[0];
               sortDirection = Constants.NORMAL_SORT;
            } else {
                sortDirection = parts[0];
                sortType = parts[1];
                if (Strings.isEmptyOrNull(sortDirection) || !sortDirection.equals(Constants.REVERSED_SORT)) {
                    sortDirection = Constants.NORMAL_SORT;
                }
            }

            int index = Arrays.asList(keys).indexOf(sortType);
            if (index!=-1) {
                adapterList.add(values[index]);
                directions.add(sortDirection);
                values[index]=null;
            }
        }

        // Add sorts not already in the sortlist
        for (String item : values) {
            if (item!=null) {
                adapterList.add(item);
                directions.add(Constants.NORMAL_SORT);
            }
        }


        adapter = new SortItemAdapter(getActivity(), R.layout.list_item_direction, R.id.text, adapterList);
        setListAdapter(adapter);

        return layout;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        String direction = directions.get(position);
        if (direction.equals(Constants.REVERSED_SORT)) {
            direction = Constants.NORMAL_SORT;
        } else {
            direction = Constants.REVERSED_SORT;
        }
        directions.remove(position);
        directions.add(position, direction);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_SELECTED, getSelectedItem());
    }

    public ArrayList<String> getSelectedItem() {
        ArrayList<String> multiSort = new ArrayList<String>();
        if (lv != null) {
            for (int i = 0 ; i< adapter.getCount() ; i++) {
               multiSort.add(directions.get(i) + Constants.SORT_SEPARATOR + adapter.getSortType(i));
            }
        } else if (originalItems !=null ) {
            multiSort.addAll(originalItems);
        }
        return multiSort;
    }

    public class SortItemAdapter extends ArrayAdapter<String> {

        public SortItemAdapter(Context context, int resource, int textViewResourceId, List<String> objects) {
            super(context, resource, textViewResourceId, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = super.getView(position, convertView, parent);
            ImageButton reverseButton = (ImageButton)row.findViewById(R.id.reverse_button);
            if (directions.get(position).equals(Constants.REVERSED_SORT)) {
                reverseButton.setBackgroundResource(R.drawable.ic_action_sort_up);
            } else {
                reverseButton.setBackgroundResource(R.drawable.ic_action_sort_down);
            }
            return row;
        }

        public String getSortType(int position) {
            String[] values = getResources().getStringArray(R.array.sort);
            String[] keys = getResources().getStringArray(R.array.sortKeys);
            View row = this.getView(position, null, null);
            TextView text = (TextView)row.findViewById(R.id.text);
            int index = Arrays.asList(values).indexOf(text.getText().toString());
            return keys[index];
        }
    }

	public void defaultSort() {
		adapterList.clear();
		directions.clear();
		for (String value : getResources().getStringArray(R.array.sort)) {
			adapterList.add(value);
			directions.add(Constants.NORMAL_SORT);
		}
		adapter.notifyDataSetChanged();
		
	}
}

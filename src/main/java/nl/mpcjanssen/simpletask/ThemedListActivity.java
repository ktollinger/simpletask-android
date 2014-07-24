package nl.mpcjanssen.simpletask;

import android.app.ListActivity;
import android.os.Bundle;
import android.view.Window;
import android.widget.ImageView;

abstract class ThemedListActivity extends ListActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        TodoApplication app = (TodoApplication) getApplication();
        setTheme(app.getActiveTheme());
        setTheme(app.getActiveFont());
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setProgressBarIndeterminateVisibility(app.isSynching());
        super.onCreate(savedInstanceState);
        // Set landscape drawers
        if (app.hasLandscapeDrawers()) {
            setContentView(R.layout.main_landscape);
        } else {
            setContentView(R.layout.main);
        }

        // Replace drawables if the theme is dark
        if (app.isDarkTheme()) {
            ImageView actionBarIcon = (ImageView) findViewById(R.id.actionbar_icon);
            if (actionBarIcon != null) {
                actionBarIcon.setImageResource(R.drawable.labels);
            }
            ImageView actionBarClear = (ImageView) findViewById(R.id.actionbar_clear);
            if (actionBarClear != null) {
                actionBarClear.setImageResource(R.drawable.cancel);
            }
        }
    }
}

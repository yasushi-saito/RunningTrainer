package com.ysaito.runningtrainer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Activity that lists available game logs.
 *
 */
public class RecordListActivity extends ListActivity {
  private static final String TAG = "RecordList";
  private RecordManager mRecordManager;
  private Activity mActivity;
  private MyAdapter mAdapter;

  private class ListThread extends AsyncTask<Integer, Integer, ArrayList<RecordSummary>> {
	  /**
	   * @param mode not used
	   */
	  @Override
	  protected ArrayList<RecordSummary> doInBackground(Integer... mode) {
		  return mRecordManager.listRecords();
	  }

	    @Override
	    protected void onProgressUpdate(Integer... unused) {
	    }

	    @Override
	    protected void onPostExecute(ArrayList<RecordSummary> records) {
	      setProgressBarIndeterminateVisibility(false);
	      mAdapter.setRecords(records);
	    }
	  }

  private class MyAdapter extends BaseAdapter {
	  private final LayoutInflater mInflater;
	  private ArrayList<RecordSummary> mRecords = new ArrayList<RecordSummary>();

	  public MyAdapter(Context context) {
		  mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	  }

	  public int getCount() {
		  return mRecords.size();
	  }

	  public Object getItem(int position) {
		  if (position >= mRecords.size()) return null;
		  return mRecords.get(position);
	  }

	  public long getItemId(int position) {
		  return position;
	  }

	  public View getView(int position, View convertView, ViewGroup parent) {
		  TextView text;
		  if (convertView == null) {
			  text = (TextView)mInflater.inflate(android.R.layout.simple_list_item_1, parent, false);
			  text.setTextSize(18);
	      } else {
	    	  text = (TextView)convertView;
	      }
		  text.setHorizontallyScrolling(false);

		  Object obj = getItem(position);
		  if (obj != null) {
			  RecordSummary summary = (RecordSummary)obj;
			  text.setText("foo: " + summary.basename);
		  }
		  return text;
	  }

	  public void setRecords(ArrayList<RecordSummary> list) {
		  mRecords.clear();
		  for (RecordSummary r : list) mRecords.add(r);
	      notifyDataSetChanged();
	  }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mRecordManager = new RecordManager(this);
    mActivity = this;
    mAdapter = new MyAdapter(this);
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    setContentView(R.layout.record_list);

    setTitle("Record list");  // TODO: externalize

    // Use an existing ListAdapter that will map an array
    // of strings to TextViews
    setListAdapter(mAdapter);

    ListView listView = (ListView)findViewById(android.R.id.list);
    listView.setStackFromBottom(true);
    registerForContextMenu(listView);
    startListing();
  }

  private void startListing() {
	  ListThread thread = new ListThread();
	  setProgressBarIndeterminateVisibility(true);
	  thread.execute((Integer[])null);
  }

  private final GregorianCalendar mTmpCalendar = new GregorianCalendar();

  public String getListLabel(RecordSummary summary) {
    StringBuilder b = new StringBuilder();
    mTmpCalendar.setTimeInMillis(summary.startTime);
    b.append(String.format("%04d/%02d/%02d ",
    		mTmpCalendar.get(Calendar.YEAR),
    		mTmpCalendar.get(Calendar.MONTH) - Calendar.JANUARY + 1,
    		mTmpCalendar.get(Calendar.DAY_OF_MONTH)));
    return b.toString();
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    Log.d(TAG,"PICKED: " + position);
  }
/*
  @Override
  public void onCreateContextMenu(
      ContextMenu menu,
      View v,
      ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.game_log_list_context_menu, menu);
    GameLog log = getObjectAtPosition(((AdapterView.AdapterContextMenuInfo)menuInfo).position);

    if (log.path() != null) {
      menu.findItem(R.id.game_log_list_save_in_sdcard).setEnabled(false);
    }
  }*/

  private class DeleteRecordTask extends AsyncTask<RecordSummary, String, String> {
    @Override
    protected String doInBackground(RecordSummary... log) {
    	mRecordManager.deleteRecord(log[0]);
    	return null;
    }

    @Override
    protected void onPostExecute(String unused) {
    	startListing();
    }
  }

}

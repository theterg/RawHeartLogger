package theterg.helloworld01;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/**
 * Created by tergia on 6/22/14.
 */
public class UploadManager extends SQLiteOpenHelper implements Runnable {
    private final static String TAG = UploadManager.class.getSimpleName();

    private boolean running;
    private Thread managerThread;
    private SQLiteDatabase db;

    private static final String DB_NAME = "com.theterg.helloworld01.HISTORY_DB";
    private static final int DATABASE_VERSION = 1;
    // History table
    private static final String DB_HISTORY_NAME = "history";
    private static final String DB_HISTORY_KEY_TIME = "time";
    private static final String DB_HISTORY_KEY_HR = "hr";
    private static final String DB_HISTORY_KEY_RR = "rr";
    private static final String DB_HISTORY_CREATE =
            "CREATE TABLE "+ DB_HISTORY_NAME +"("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                DB_HISTORY_KEY_TIME+" INTEGER,"+
                DB_HISTORY_KEY_HR + " REAL,"+
                DB_HISTORY_KEY_RR + " REAL);";


    UploadManager(Context context) {
        super(context, DB_NAME, null, DATABASE_VERSION);
        db = getWritableDatabase();
        managerThread = new Thread(this);
        managerThread.start();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DB_HISTORY_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i2) {

    }

    public void addHistorySample(float hr, float rr) {
        int count = getHistorySize();
        ContentValues values = new ContentValues();
        long epoch = System.currentTimeMillis();

        values.put(DB_HISTORY_KEY_TIME, epoch);
        values.put(DB_HISTORY_KEY_HR, hr);
        values.put(DB_HISTORY_KEY_RR, rr);

        synchronized (db) {
            db.insert(DB_HISTORY_NAME, null, values);
        }

        synchronized (managerThread) {
            managerThread.notifyAll();
        }
    }

    public int getHistorySize() {
        int count = -1;
        synchronized (db) {
            Cursor curr = db.rawQuery("SELECT COUNT(*) from "+DB_HISTORY_NAME, null);
            curr.moveToFirst();
            count = curr.getInt(0);
        }
        return count;
    }

    public JSONArray getAllHistory() {
        JSONArray values = new JSONArray();
        synchronized (db) {
            Cursor curr = db.rawQuery("SELECT * FROM "+DB_HISTORY_NAME, null);
            if (!curr.moveToFirst()) {
                return values;
            }
            do {
                JSONArray row = new JSONArray();
                try {
                    row.put(curr.getLong(1)/1000.0);
                    row.put(curr.getFloat(2));
                    row.put(curr.getFloat(3));
                    values.put(row);
                } catch (JSONException e) {
                    continue;
                }
            } while(curr.moveToNext());
        }
        return values;
    }

    @Override
    public void run() {
        running = true;
        try {
            while(running) {
                synchronized (managerThread){
                    managerThread.wait(10000);
                }
                int count = getHistorySize();
                if (count > 10) {
                    Log.i(TAG, "Killing history");
                    JSONArray historyJSON = getAllHistory();
                    synchronized (db) {
                        db.delete(DB_HISTORY_NAME, null, null);
                    }
                    Log.d(TAG, historyJSON.toString());
                }
            }
        } catch (InterruptedException e) {
            running = false;
            return;
        }
    }
}

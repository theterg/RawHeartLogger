package theterg.helloworld01;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Created by tergia on 6/22/14.
 */
public class UploadManager extends SQLiteOpenHelper implements Runnable {
    private final static String TAG = UploadManager.class.getSimpleName();

    private boolean running;
    private Thread managerThread;
    private SQLiteDatabase db;
    private Context service;

    private String username = "";
    private String password = "";

    public final static String ACTION_UPLOAD_SUCCESSFUL =
            "com.theterg.helloworld01.ACTION_UPLOAD_SUCCESSFUL";
    public final static String ACTION_UPLOAD_FAILURE =
            "com.theterg.helloworld01.ACTION_UPLOAD_FAILURE";

    private static final int UPLOAD_POLL_RATE_MILLIS = 60000;

    private static final String BASE_URL = "http://flxtest.bodytrack.org";

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
        service = context;
        db = getWritableDatabase();
        managerThread = new Thread(this);
        managerThread.start();
    }

    protected void updateUploadStatus(String action) {
        final Intent intent = new Intent(action);
        service.sendBroadcast(intent);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DB_HISTORY_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i2) {

    }

    public void setUsername(String user) {
        username = user;
    }

    public void setPassword(String pass) {
        password = pass;
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
            curr.close();
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
                    row.put(curr.getFloat(3)/1000.0);
                    values.put(row);
                } catch (JSONException e) {
                    continue;
                }
            } while(curr.moveToNext());
            curr.close();
        }
        return values;
    }

    public boolean uploadData() {
        JSONArray historyJSON = getAllHistory();
        JSONObject body = new JSONObject();
        JSONArray channels = new JSONArray();

        try {
            channels.put("HeartRate");
            channels.put("BeatSpacing");
            body.put("channel_names", channels);
            body.put("data", historyJSON);
        } catch (JSONException e) {
            Log.e(TAG, "Couldn't create JSON for POST: ", e);
            return false;
        }

        HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(String.format("%s/api/bodytrack/jupload?dev_nickname=%s", BASE_URL, "PolarStrap"));
        try {
            post.setEntity(new StringEntity(body.toString()));
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Couldn't add JSON to POST: ", e);
            return false;
        }

        String credentials = username + ":" + password;
        String encoding = Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
        post.setHeader("Authorization", "Basic "+encoding);
        post.setHeader("Accept", "*/*");
        post.setHeader("Content-type", "application/json");
        post.setHeader("User-Agent", "curl/7.35.0");

        HttpResponse resp;
        String result_text;
        try {
            resp = client.execute(post);
            result_text = EntityUtils.toString(resp.getEntity());
        } catch (IOException e) {
            Log.e(TAG, "Unable to POST data: ", e);
            return false;
        }

        try {
            JSONObject result = new JSONObject(result_text);
            if (!result.has("result")) {
                Log.e(TAG, "Unrecognized POST response: "+result.toString());
                return false;
            }
            if (!result.getString("result").equals("OK")) {
                Log.e(TAG, "POST Failed: "+result.toString());
                return false;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Unable to decode POST response: ", e);
            return false;
        }

        return true;
    }

    @Override
    public void run() {
        long lastUpdate = System.currentTimeMillis();
        running = true;
        try {
            while(running) {
                synchronized (managerThread){
                    managerThread.wait(10000);
                }
                int count = getHistorySize();
                if ((count > 0)&&(System.currentTimeMillis() > lastUpdate + UPLOAD_POLL_RATE_MILLIS)) {
                    lastUpdate = System.currentTimeMillis();
                    if (uploadData()) {
                        Log.i(TAG, "Killing history");
                        synchronized (db) {
                            db.delete(DB_HISTORY_NAME, null, null);
                        }
                        updateUploadStatus(ACTION_UPLOAD_SUCCESSFUL);
                    } else {
                        updateUploadStatus(ACTION_UPLOAD_FAILURE);
                    }
                }
            }
        } catch (InterruptedException e) {
            running = false;
            return;
        }
    }
}

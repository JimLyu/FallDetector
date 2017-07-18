package jstudio.fallDetector;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.ByteBuffer;
import java.util.ArrayList;
/**
 * Created by LABUSE on 2016/12/7.
 */
class SQLiteClient{
    /*資料庫名稱 */
    private static final String DATABASE_NAME = "fallDetector.db";
    /*資料庫版本，資料結構改變的時候要更改這個數字，通常是加一*/
    private static final int VERSION = 1;
    /*資料表名稱*/
    private static final String TABLE_NAME = "FD_Data";	//
    /*編號表格欄位名稱，固定不變*/
    private static final String KEY_ID = "_id";
    /*其它表格欄位名稱*/
    private static final String TIME_COLUMN = "Time";
    private static final String AX_COLUMN = "Ax";
    private static final String AY_COLUMN = "Ay";
    private static final String AZ_COLUMN = "Az";
    private static final String GX_COLUMN = "Gx";
    private static final String GY_COLUMN = "Gy";
    private static final String GZ_COLUMN = "Gz";
    /*使用上面宣告的變數建立表格的SQL指令*/
    private static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    TIME_COLUMN + " INTEGER NOT NULL, " +
                    AX_COLUMN + " REAL NOT NULL, " +
                    AY_COLUMN + " REAL NOT NULL, " +
                    AZ_COLUMN + " REAL NOT NULL, " +
                    GX_COLUMN + " REAL NOT NULL, " +
                    GY_COLUMN + " REAL NOT NULL, " +
                    GZ_COLUMN + " REAL NOT NULL)";
    /*資料庫物件*/
    private SQLiteDatabase database;

    SQLiteClient(Context context){
        if (database == null || !database.isOpen()) {
            database = new SQLiteOpenHelper(context, DATABASE_NAME, null, VERSION){
                @Override
                public void onCreate(SQLiteDatabase db) {
                    db.execSQL(CREATE_TABLE);
                }
                @Override
                public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                    db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
                    onCreate(db);
                }
            }.getWritableDatabase();
        }
//        deleteAll();//暫時刪除全部 TODO 不刪除會LAG！？
    }

    void close() {
        database.close();
    }

    long insert(long  time, float[] ac, float[] gv){
        ContentValues cv = new ContentValues();
        cv.put(TIME_COLUMN, time);
        cv.put(AX_COLUMN, ac[0]);
        cv.put(AY_COLUMN, ac[1]);
        cv.put(AZ_COLUMN, ac[2]);
        cv.put(GX_COLUMN, gv[0]);
        cv.put(GY_COLUMN, gv[1]);
        cv.put(GZ_COLUMN, gv[2]);
        return database.insertOrThrow(TABLE_NAME, null, cv);
    }

    long insert(long  time, float ax, float ay, float az, float gx, float gy, float gz){
        ContentValues cv = new ContentValues();
        cv.put(TIME_COLUMN, time);
        cv.put(AX_COLUMN, ax);
        cv.put(AY_COLUMN, ay);
        cv.put(AZ_COLUMN, az);
        cv.put(GX_COLUMN, gx);
        cv.put(GY_COLUMN, gy);
        cv.put(GZ_COLUMN, gz);
        return database.insertOrThrow(TABLE_NAME, null, cv);
    }

    private boolean delete(String where){
        MainActivity.log("deleting " + where);
        return database.delete(TABLE_NAME, where , null) > 0;
    }

    boolean deleteAll(){   //刪除全部
        return delete(null);
    }

    boolean delete(int endId){
        String where = KEY_ID + "<=" + endId;
        return delete(where);
    }

    boolean delete(long startTime, long endTime){
        String con1 = (startTime == -1)? "" : TIME_COLUMN + " >= " + startTime;
        String con2 = (endTime == -1)? "" : TIME_COLUMN + " <= " + endTime;
        String and = (startTime == -1 || endTime == -1)? "" : " AND ";
        String where = con1 + and + con2;
        return delete(where);
    }

    private DataSheet query(String where){
        String order = KEY_ID + " ASC";
        DataSheet result = new DataSheet();
        Cursor cursor = database.query(TABLE_NAME, null, where, null, null, null, order, null);
        while (cursor.moveToNext()) {
            float[] ac, gv;
            ac = new float[3];
            ac[0] = cursor.getFloat(2);
            ac[1] = cursor.getFloat(3);
            ac[2] = cursor.getFloat(4);
            gv = new float[3];
            gv[0] = cursor.getFloat(5);
            gv[1] = cursor.getFloat(6);
            gv[2] = cursor.getFloat(7);
            result.add(cursor.getLong(1), ac, gv);
        }
        cursor.close();
        return result;
    }

    private DataSheet query(String where, long fallTime){
        String order = KEY_ID + " ASC";
        DataSheet result = new DataSheet(fallTime);
        Cursor cursor = database.query(TABLE_NAME, null, where, null, null, null, order, null);
        while (cursor.moveToNext()) {
            float[] ac, gv;
            ac = new float[3];
            ac[0] = cursor.getFloat(2);
            ac[1] = cursor.getFloat(3);
            ac[2] = cursor.getFloat(4);
            gv = new float[3];
            gv[0] = cursor.getFloat(5);
            gv[1] = cursor.getFloat(6);
            gv[2] = cursor.getFloat(7);
            result.add(cursor.getLong(1), ac, gv);
        }
        cursor.close();
        return result;
    }

    private DataSheet query(String where, int limit) {
        String order = KEY_ID + " ASC";
        DataSheet result = new DataSheet();
        Cursor cursor = database.query(TABLE_NAME, null, where, null, null, null, order, String.valueOf(limit));
        while (cursor.moveToNext()) {
            float[] ac, gv;
            ac = new float[3];
            ac[0] = cursor.getFloat(2);
            ac[1] = cursor.getFloat(3);
            ac[2] = cursor.getFloat(4);
            gv = new float[3];
            gv[0] = cursor.getFloat(5);
            gv[1] = cursor.getFloat(6);
            gv[2] = cursor.getFloat(7);
            result.add(cursor.getLong(1), ac, gv);
        }
        cursor.close();
        return result;
    }

    DataSheet getByTime(long startTime, long endTime){  //-1表無條件
        String con1 = (startTime == -1)? "" : TIME_COLUMN + " >= " + startTime;
        String con2 = (endTime == -1)? "" : TIME_COLUMN + " <= " + endTime;
        String and = (startTime == -1 || endTime == -1)? "" : " AND ";
        String where = con1 + and + con2;
        return query(where);
    }

    DataSheet getByTime(long startTime, long endTime, int limit){  //-1表無條件
        String con1 = (startTime == -1)? "" : TIME_COLUMN + " >= " + startTime;
        String con2 = (endTime == -1)? "" : TIME_COLUMN + " <= " + endTime;
        String and = (startTime == -1 || endTime == -1)? "" : " AND ";
        String where = con1 + and + con2;
        return query(where, limit);
    }

    DataSheet getByTime(long fallTime){  //fallTime前0.5秒到後面全部
        String where = TIME_COLUMN + " >= " + (fallTime-500);
        return query(where, fallTime);
    }

    DataSheet getByID(long startID, long endID){
        String where = KEY_ID  + " >= " + startID + " AND " + KEY_ID  + " <= " + endID;
        return query(where);
    }

    // 取得資料數量
    int getCount() {
        int result = 0;
        Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM " + TABLE_NAME, null);
        if (cursor.moveToNext()) {
            result = cursor.getInt(0);
        }
        return result;
    }

    //取得第一筆資料（最小時間）
    long getStart(){
        return getByTime(-1, -1, 1).getStart();
    }

    //DataBase路徑
    String getPath(){
        return database.getPath();
    }
}


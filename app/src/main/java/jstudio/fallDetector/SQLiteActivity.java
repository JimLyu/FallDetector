package jstudio.fallDetector;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Vector;

public class SQLiteActivity extends Activity {

    private SQLiteClient sqLiteClient;
    private TableLayout sqlTable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sqlite);
        sqLiteClient = new SQLiteClient(SQLiteActivity.this);
        sqlTable = (TableLayout) findViewById(R.id.sqlTable);
        sqlTable.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                new AlertDialog.Builder(SQLiteActivity.this)
                        .setTitle("刪除資料表")
                        .setMessage("確定刪除全部資料？")
                        .setIcon(android.R.drawable.ic_delete)
                        .setPositiveButton("確定",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        sqLiteClient.deleteAll();
                                        load();
                                    }
                                })
                        .setNegativeButton("取消",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {}
                                })
                        .show();
                return false;
            }
        });
        load();
    }

    private void load() {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("載入中...");
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Vector<DataSheet.Data> dataSheet = sqLiteClient.getByTime(-1, -1, 20).vector();
                final Vector<TableRow> rows = new Vector<>();
                for (DataSheet.Data data : dataSheet){
                    /*建立一個Row*/
                    final TableRow tableRow = new TableRow(SQLiteActivity.this);
                    tableRow.setGravity(Gravity.CENTER_HORIZONTAL);
                    tableRow.setBackgroundColor(Color.WHITE);
                    TableLayout.LayoutParams layoutParams =
                            new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT,TableLayout.LayoutParams.WRAP_CONTENT);
                    layoutParams.setMargins(1, 1, 1, 1);
                    tableRow.setLayoutParams(layoutParams);

                    /*開始讀資料*/
                    TextView time = new TextView(SQLiteActivity.this);
                    time.setText(new SimpleDateFormat("MM/dd HH:mm:ss.SSS").format(new Date(data.time)));
                    tableRow.addView(time);

                    float[] array = data.getArray();
                    TextView[] row = new TextView[6];
                    for(int i = 0; i < 6; i++){
                        row[i] = new TextView(SQLiteActivity.this);
                        row[i].setText(String.format("%.2f", array[i]));
                        row[i].setGravity(Gravity.CENTER_HORIZONTAL);
                        tableRow.addView(row[i]);
                    }
//                    if(data.acceleration > MainActivity.HIGH_THRESHOLD)
//                        tableRow.setBackgroundColor(Color.RED);
                    rows.addElement(tableRow);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(sqlTable.getChildCount() > 1)
                            sqlTable.removeViews(1, sqlTable.getChildCount()-1);
                        for (TableRow row : rows) {
                            sqlTable.addView(row);
                        }
                        sqlTable.postInvalidate();
                        dialog.dismiss();
                    }
                });
            }
        }).start();
    }

}

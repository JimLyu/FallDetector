package jstudio.fallDetector;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Vector;

/**
 * Created by LABUSE on 2017/6/5.
 */

public class DataSheet implements Serializable{

    private final Vector<Data> dataList;
    private long start = 0;
    private long end = 0;
    private int period = 0;

    private long fallTime = -1;

    DataSheet(){
        dataList = new Vector<>();
    }

    public DataSheet(long fallTime){
        dataList = new Vector<>();
        this.fallTime = fallTime;
    }

    void add(Data data){
        try{
            Data lastData = dataList.lastElement();
            if(data.time == lastData.time)  //移除一樣時間的
                dataList.remove(lastData);
            else
                period += data.time - lastData.time;
            dataList.addElement(data);
            end = data.time;
        }catch (NoSuchElementException n){  // lastElement() & firstElement() (Vector為空時）
            start = end = data.time;
            dataList.addElement(data);
        }
    }

    AbstractMap.SimpleEntry<Long, Float> add(long time, float[] ac, float[] gv){
        Data data = new Data(time, ac, gv);
        add(data);
        return new AbstractMap.SimpleEntry<>(data.time, data.acceleration);//TODO 檢查
    }

    public void add(long time, float ax, float ay, float az, float gx, float gy, float gz){
        float[] ac = new float[3];
        float[] gv = new float[3];
        ac[0] = ax;
        ac[1] = ay;
        ac[2] = az;
        gv[0] = gx;
        gv[1] = gy;
        gv[2] = gz;
        add(time, ac, gv);
    }

    public Iterator<Data> iterator(){
        return dataList.iterator();
    }

    public Data pollFirst(){
        try{
            Data firstData = dataList.firstElement();
            long removeTime = firstData.time;
            dataList.remove(firstData);
            try {
                start = dataList.firstElement().time;
                period -= start - removeTime;
            }catch (NoSuchElementException n1){ //dataList is empty
                start = end = period = 0;
            }
            return firstData;
        }catch (NoSuchElementException n) { //dataList 一開始就沒元件
            return null;
        }
    }

    public Vector<Data> vector(){
        return dataList;
    }

    public long period(){ return period; }

    public long getStart(){ return start; }

    public long getEnd(){ return end; }

    public int getSize(){ return dataList.size(); }

    public long getFallTime() { return fallTime; }

    public AbstractMap.SimpleEntry<Long, Float> get(int index){
        try{
            Data data = dataList.elementAt(index);
            return new AbstractMap.SimpleEntry<>(data.time, data.acceleration); //TODO BUG
        }catch (ArrayIndexOutOfBoundsException a){
            return null;
        }
    }

    public Data getData(int index){
        try{
            return new Data(dataList.elementAt(index)); //TODO BUG
        }catch (ArrayIndexOutOfBoundsException a){
            return null;
        }
    }

    public DataSheet setFall(Long t) {
        fallTime = t;
        return this;
    }

    public boolean isEmpty(){
        return (start==0);
    }

    public class Data implements Serializable{
        final double G = 9.81;
        final double G2 = 96.2361; //g^2
        public long time;
        public float ax, ay, az, gx, gy, gz;
        public float acceleration; //單位：g

        Data(long time, float[] ac, float[] gv){
            this.time = time;
            ax = ac[0];
            ay = ac[1];
            az = ac[2];
            gx = gv[0];
            gy = gv[1];
            gz = gv[2];
            acceleration = Double.valueOf((ac[0]*gv[0] + ac[1]*gv[1] + ac[2]*gv[2]) / G2).floatValue();
        }

        Data(Data data){
            this.time = data.time;
            this.ax = data.ax;
            this.ay = data.ay;
            this.az = data.az;
            this.gx = data.gx;
            this.gy = data.gy;
            this.gz = data.gz;
            this.acceleration = data.acceleration;
        }

        public float[] ac(){
            float[] ac = new float[3];
            ac[0] = ax;
            ac[1] = ay;
            ac[2] = az;
            return ac;
        }

        public float[] gv(){
            float[] gv = new float[3];
            gv[0] = gx;
            gv[1] = gy;
            gv[2] = gz;
            return gv;
        }

        public float[] getArray(){
            float[] array = new float[6];
            array[0] = ax;
            array[1] = ay;
            array[2] = az;
            array[3] = gx;
            array[4] = gy;
            array[5] = gz;
            return array;
        }

        public float getla(){//單位：g
            return Double.valueOf(Math.sqrt(Math.pow(ax, 2) + Math.pow(ay, 2) + Math.pow(az, 2))/G).floatValue();
        }

        public float getoA() {//單位：g
            return Double.valueOf(Math.sqrt(Math.pow(ax+gx, 2) + Math.pow(ay+gy, 2) + Math.pow(az+gz, 2))/G).floatValue();
        }
    }
}

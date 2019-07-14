package com.example.android.udpreceiverv1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    WifiManager wifimngr;
    TextView voltageTextview;
    TextView rssiView;
    TextView distView;
    String ssid="cctestAP";
    WifiConfiguration wifiConfig;
    WifiInfo wifiInfo;
    DatagramSocket s = null;
    recvTask recvTask1;
    int port=5001;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(null,"onCreate");
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);       //activity keeps screen on

        voltageTextview = (TextView) findViewById(R.id.voltag);
        rssiView=(TextView)findViewById(R.id.rssiview);
        distView=(TextView)findViewById(R.id.distview);
        wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = String.format("\"%s\"", ssid);
        //wifiConfig.preSharedKey = String.format("\"%s\"", key);       //this is an open network
    }

    @Override
    protected void onResume() {
        Log.d(null,"onResume");
        super.onResume();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        intentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        registerReceiver(wifistateReceiver, intentFilter);
        wifimngr = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifimngr.startScan();       //start a scan anyway on app start
/*        if (wifimngr.isWifiEnabled()) {

        wifiInfo = wifimngr.getConnectionInfo();
        ssid = wifiInfo.getSSID();
        if (ssid.replace("\"", "").equals("cctestAP"))
        {
            if (wifiInfo.getIpAddress() != 0) {       //check if ip has been obtained
                recvTask1= (recvTask) new recvTask().execute(5001);
            }

        }
        else{voltageTextview.setText("NOT CONNECTED TO CC3200");}
    }
        else{voltageTextview.setText("NO WIFI");}*/

}

        private BroadcastReceiver wifistateReceiver=new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                wifimngr = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                wifiInfo = wifimngr.getConnectionInfo();
                final String action = intent.getAction();
                if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                    if (intent.getExtras() != null) {
                        NetworkInfo ni = (NetworkInfo) intent.getExtras().get(WifiManager.EXTRA_NETWORK_INFO);
                        String APname=ni.getExtraInfo().replace("\"", "");
                        if (ni != null && ni.getState() == NetworkInfo.State.CONNECTED) {
                            Log.d("app", "Network " + ni.getTypeName() + " connected");
                            if (wifiInfo.getIpAddress() != 0 &&APname.equals(ssid)) {       //check if ip has been obtained and we are in correct network
                                if (recvTask1==null||recvTask1.isCancelled()){      //ensure only 1 task is running
                                recvTask1= (recvTask) new recvTask();
                                recvTask1.execute(port);}
                            }
                            else{
                                voltageTextview.setText("NOT CONNECTED TO CC3200");
                                rssiView.setText("");
                                distView.setText("");
                                if (recvTask1 != null&&!recvTask1.isCancelled()) {  //if recvtask has been instantiated AND is not cancelled,cancel it
                                    recvTask1.cancel(true);
                                    Log.d(null,"thread stopped in broadcast receiver/network state connected to other AP");
                                }
                            }
                        }
                        else{
                            if (recvTask1 != null&&!recvTask1.isCancelled()) {
                                recvTask1.cancel(true);
                                Log.d(null,"thread stopped in broadcast receiver/network state changed/disconnected");
                            }
                            voltageTextview.setText("NO WIFI");
                            rssiView.setText("");
                            distView.setText("");}
                    }
                }
                if (action.equals((WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))) {       //this is the receiver for scan results. if connected,scans can only be invoked by startscan method.
/*                    List<ScanResult> sr = ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE)).getScanResults();      //will this get the new results??
                    for (int i = 0; i < sr.size(); i++) {
                        String info = ((sr.get(i)).toString());
                        Log.d("app", "result"+i+":" + info);
                    }*/
                    if(!wifimngr.getConnectionInfo().getSSID().replace("\"", "").equals(ssid)){   //try to connect to the correct network
                    //remember id
                    int netId = wifimngr.addNetwork(wifiConfig);
                    wifimngr.disconnect();
                    wifimngr.enableNetwork(netId, true);
                    wifimngr.reconnect();}
                }
                if(action.equals(WifiManager.RSSI_CHANGED_ACTION)){
                    Log.d(null,"new rssi");
                    if (wifiInfo.getSSID().replace("\"", "").equals("cctestAP")) {
                        int rssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, -100);
                        rssiView.setText("RSSI: " + rssi + "dBm");      //Distance (km) = 10(Free Space Path Loss – 32.44 – 20log10(f))/20
                        float f = (float) DistCalc.calculateDistance(rssi, 2447);       //channel 8 = 2447mhz
                        distView.setText("distance ~" + String.format("%.2f", f)+"m");
                    }
                }
            }
        };

    public class recvTask extends AsyncTask<Integer, String, Void> {

        @Override
        protected Void doInBackground(Integer... server_port) {
            Log.d(null,"new Thread started");
            synchronized(this) {
//                while (!Thread.interrupted()) {       //we won't use this. Let task end and re-start it when connected. If reconnection takes too long,user may need to connect manually or restart app
                    String text;
                    byte[] message = new byte[1500];
                    DatagramPacket p = new DatagramPacket(message, message.length);
                    try {
                        s = new DatagramSocket(server_port[0]);
                        s.setSoTimeout(5000);       //5 sec
                        while (!Thread.interrupted()) {
                            s.receive(p);
                            text = new String(message, 0, p.getLength()).trim();
                            publishProgress(text);
                            Log.d(null, "RECVD PACKET");
                            Thread.sleep(100);        //delay is implemented on server side   should add for relieving load?????
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.d(null, "EXCEPTION:" + e.getMessage());
                    } finally {
                        Log.d(null, "Entered finally block");
                        if (s != null) {
                            s.close();
                            Log.d(null, "socket closed in finally block");
                        }
                    }
 /*                   try{
                    Thread.sleep(1000);}
                    catch (Exception e2){
                        Log.d(null, "EXCEPTION2 :" + e2.getMessage());
                    }
                }*/
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... text1) {
            voltageTextview.setText("Voltage= " + text1[0]);

        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Log.d(null,"onPostexecute");
            super.onPostExecute(aVoid);
            voltageTextview.setText("NOT CONNECTED TO CC3200");
            rssiView.setText("");
            distView.setText("");
            wifimngr.startScan();     //start a new scan??
        }

        @Override
        protected void onCancelled() {
            Log.d(null,"onCancelled");
            super.onCancelled();
/*          if (s != null) {
                s.close();
                Log.d(null,"closed in onCancelled");    //IS THIS NEEDED? SEEMS LIKE TASK THROWS INTERRUPTED-EXCEPTION ANYWAY..
            }*/
        }
    }

    @Override
    protected void onPause() {
        Log.d(null,"onPause");
        super.onPause();
        unregisterReceiver(wifistateReceiver);
        if (recvTask1 != null&&!recvTask1.isCancelled()) {
            recvTask1.cancel(true);
            Log.d(null,"thread stopped in onPause");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(null,"onDestroy");
/*        if (recvTask1 != null) {
            recvTask1.cancel(true);
            Log.d(null,"thread stopped in onDestroy");
        }*/
    }

    //this is for viewing a readable ip..
    public String getIpAddr() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = null;
        if (wifiManager != null) {
            wifiInfo = wifiManager.getConnectionInfo();
        }
        int ip = 0;
        if (wifiInfo != null) {
            ip = wifiInfo.getIpAddress();
        }

        String ipString = String.format(
                "%d.%d.%d.%d",
                (ip & 0xff),
                (ip >> 8 & 0xff),
                (ip >> 16 & 0xff),
                (ip >> 24 & 0xff));

        return ipString;
    }
}
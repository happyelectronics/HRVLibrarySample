package com.hecz.hrvlibrarysample;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.hecz.stresslocatorcommon.model.OximeterData.DataParser;
import com.hecz.stresslocatorcommon.model.OximeterData.PackageParser;
import com.hecz.stresslocatorcommon.sensors.BluetoothDeviceLocator;
import com.hecz.stresslocatorcommon.sensors.BluetoothLeHrService;
import com.hecz.stresslocatorcommon.sensors.BluetoothLeService;
import com.hecz.stresslocatorcommon.sensors.HrvControl;
import com.hecz.stresslocatorcommon.sensors.IBTStatus;
import com.hecz.stresslocatorcommon.sensors.IOxiObserver;
import com.hecz.stresslocatorcommon.sensors.IOxiViewSubscriber;
import com.hecz.stresslocatorcommon.sensors.OxiResponseHandler;
import com.hecz.stresslocatorcommon.sensors.OxiViewControl;
import com.hecz.stresslocatorcommon.sensors.SerialUSBService;
import com.hecz.stresslocatorhrv.model.HrvData;
import com.hecz.stresslocatorhrv.model.IHrvData;
import com.hecz.stresslocatorhrv.model.IOxiData;
import com.hecz.stresslocatorhrv.model.OxiBtData;
import com.hecz.stresslocatorhrv.model.OxiData;
import com.hecz.stresslocatorhrv.model.Settings;
import com.hecz.stresslocatorhrv.model.SourceType;

public class MainActivity extends AppCompatActivity implements IBTStatus, IOxiViewSubscriber, PackageParser.OnDataChangeListener {

    private boolean isCheckingThread = false;
    private boolean isStartEnabled = false;
    private OxiViewControl oxiViewControl = null;
    private boolean isQuickConnect = true;
    private TextView textStatus;
    private String coherence = "-";
    private boolean isGattServiceStarted = false;
    private int sourceType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Settings.appDirectory = "Spo2Logger";
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /*if(isStartEnabled) {
                    com.hecz.stresslocatorcommon.utils.Log.i(Global.APP_LOG_PREFIX, "sendRRData");

                    oxiViewControl.stopMeasure();

                    Tools.sendRRData(oxiViewControl.getOxiData(), MainActivity.this);
                }

                Snackbar.make(view, "Data saved", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                oxiViewControl.startMeasure();*/


                startUSBMeasure();
                //startGeneratorMeasure();
                //startGattMeasure(); //PolarH7

            }
        });

        //Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        //Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        filter = new IntentFilter(BluetoothLeHrService.ACTION_GATT_HR_FOUND);
        this.registerReceiver(mReceiver, filter);

        filter = new IntentFilter(BluetoothLeHrService.RR_DATA);
        this.registerReceiver(mReceiver, filter);

        textStatus = (TextView) findViewById(R.id.textStatus);
        textStatus.setText("Connecting...");

        //BluetoothDeviceLocator.getInstance().setMessageHandler(this);
        //showBluetoothState();

        Settings.setNAME("spo2");
        //Settings.NAME = "Gatt";
        sDevice = "USB";

        //if(sDevice.equals("Gatt")) {
        //    BluetoothLeHrService.start(this);
        //    isGattServiceStarted = true;
        //}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private String sDevice = "StressLocator";
    private double usbtime = 0;
    private long lastTime = 0;
    private int nTime = 0;
    private int readCounter = 0;
    private int usbpulse = 60;
    private int usbspo2 = 99;

    private DataParser mDataParser;

    private PackageParser mPackageParser;
    /**
     * The BroadcastReceiver that listens for discovered devices and changes the
     * title when discovery is finished
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothLeService.ACTION_SPO2_DATA_AVAILABLE.equals(action)) {
                //Log.d(Settings.APP_LOG_PREFIX + "pulse", "ACTION_SPO2_DATA_AVAILABLE = ");
                mDataParser.add(intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA));
            }

            if (BluetoothLeHrService.RR_DATA.equals(action)) {
                if (oxiViewControl != null) {
                    IOxiObserver eventSource = oxiViewControl.getEventSource();
                    if (eventSource == null) {
                        return;
                    }
                    int rr;
                    String rrd = intent
                            .getStringExtra(BluetoothLeHrService.RR_DATA_INT);
                    String[] rrda = rrd.split(";");
                    for (int i = 0; i < rrda.length; i++) {
                        if (rrda[i].length() > 0) {
                            rr = Integer.parseInt(rrda[i]);
                            eventSource.updateRR(rr, 0);
                        }
                    }
                }

                if (oxiViewControl != null) {
                    IOxiObserver eventSource = oxiViewControl.getEventSource();
                    if (eventSource == null) {
                        return;
                    }
                    //int ad = intent.getIntExtra(SerialUSBService.USB_RR_DATA_INT, 0);

                    int rr;
                    String rrd = intent
                            .getStringExtra(BluetoothLeHrService.RR_DATA_INT);
                    String[] rrda = rrd.split(";");
                    for (int i = 0; i < rrda.length; i++) {
                        if (rrda[i].length() > 0) {
                            rr = Integer.parseInt(rrda[i]);
                            eventSource.updateRR(rr, 60*rr);
                        }
                    }
                }
            }
            // When discovery finds a device
            if (BluetoothLeHrService.ACTION_GATT_HR_FOUND.equals(action)) {
                isStartEnabled = true;
                startGattMeasure(); //PolarH7
                showBluetoothState();
            } else if (SerialUSBService.USB_RR_DATA.equals(action)) {
                if (oxiViewControl != null) {
                    IOxiObserver eventSource = oxiViewControl.getEventSource();
                    if (eventSource == null) {
                        return;
                    }
                    int ad = intent.getIntExtra(SerialUSBService.USB_RR_DATA_INT, 0);

                    OxiBtData oxiBtData = new OxiBtData();
                    oxiBtData.signalStrenght = 0;
                    usbtime += 0.0197;;
                    /*if(lastTime == 0) {
                        lastTime = System.currentTimeMillis();
                    }*/
                    //nTime++;
                    //Log.d(Settings.APP_LOG_PREFIX + "ad", "time = " + ((double)(System.currentTimeMillis()-lastTime)/nTime));
                    oxiBtData.time = usbtime;
                    //drr = Math.abs(drr);
                    //if (drr > 10000) {
                    //    drr = 65536 - drr;
                    //}
                    oxiBtData.ad = ad;

                    //oxiBtData.sar = 0;
                    int ipulse = intent.getIntExtra(SerialUSBService.USB_PR_DATA_INT, -1);
                    int ispo2 = intent.getIntExtra(SerialUSBService.USB_SPO2_DATA_INT, -1);
                    if(ipulse > -1) {
                        usbpulse = ipulse;
                    }
                    if(ispo2 > -1) {
                        //Log.d(Settings.APP_LOG_PREFIX + "pulse", "pulse = " + usbpulse + ", spo2 = "+usbspo2);
                        usbspo2 = ispo2;
                    }
                    oxiBtData.pulse = usbpulse;
                    oxiBtData.spO2 = usbspo2;

                    oxiBtData.readCounter = readCounter++;

                    eventSource.updateOxiData(oxiBtData);
                }
            }

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already paired, skip it, because it's been listed
                // already

                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    if(device == null) {
                        return;
                    }
                    if(device.getName() == null) {
                        return;
                    }
                    // mNewDevicesArrayAdapter.add(device.getName() + "\n" +
                    // device.getAddress());
                    Log.i(Global.APP_LOG_PREFIX + "btsearch",
                            "unpaired device found: " + device.getName()
                                    + ", address: " + device.getAddress());
                    if (device.getName().equals(sDevice)) {
                        // BluetoothDeviceLocator.getInstance().scan(sDevice);
                        // TODO at si to sparuje
                    }
                } else {
                    Log.i(Global.APP_LOG_PREFIX + "btsearch",
                            "paired device found: " + device.getName()
                                    + ", address: " + device.getAddress());
                    if (device.getName().equals(sDevice)) {
                        BluetoothDeviceLocator.getInstance().scan(sDevice);
                        // TODO at si to sparuje
                    }
                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED
                    .equals(action)) {
                // setProgressBarIndeterminateVisibility(false);
                Log.i(Global.APP_LOG_PREFIX + "btsearch",
                        "ACTION_DISCOVERY_FINISHED");
                showBluetoothState();
            }
        }
    };

    @Override
    protected void onDestroy() {
        this.unregisterReceiver(mReceiver);

        if(isStartEnabled) {
            oxiViewControl.stopMeasure();
        }

        super.onDestroy();
    }

    private void startUSBMeasure() {
        stopBtChecking();
        isStartEnabled = true;

        SerialUSBService.startActionStart(this, "", "");

        sourceType = SourceType.USBOXI;

        textStatus.setText("Measuring...");
        Settings.isRRLog = true;
        Settings.length = 120;

        Log.i(Global.APP_LOG_PREFIX, "Register USBAPP");
                /*IntentFilter filter = new IntentFilter(
                        SerialUSBService.USB_RR_DATA);
                this.registerReceiver(mReceiver, filter);
*/
        mDataParser = new DataParser(DataParser.Protocol.AUTO, new DataParser.onPackageReceivedListener() {
            @Override
            public void onPackageReceived(int[] dat) {
                //Log.i(Settings.APP_LOG_PREFIX, "onPackageReceived: " + Arrays.toString(dat));
                if (mPackageParser == null) {
                    mPackageParser = new PackageParser(MainActivity.this);
                }

                //mPackageParser.parseIsrael(dat);
                //mPackageParser.parse(dat);
                mPackageParser.parseAuto(mDataParser, dat);
            }
        });
        mDataParser.start();

        com.hecz.stresslocatorcommon.utils.Log.d(Global.APP_LOG_PREFIX, "START with this parameters:"
                + " isRRLog = "+Settings.isRRLog
                + " length = "+Settings.length);

        IntentFilter filter = new IntentFilter(
                SerialUSBService.USB_RR_DATA);
        //this.registerReceiver(mReceiver, filter);
        this.registerReceiver(mReceiver, makeGattUpdateIntentFilter());

        IHrvData hrvData = new HrvData();
        IOxiData oxiData = new OxiData(hrvData, SourceType.USBOXI);

        OxiResponseHandler oxiResponseHandler = new OxiResponseHandler(
                oxiData, hrvData);
        HrvControl hrvControl = new HrvControl(oxiData, hrvData);
        oxiViewControl = new OxiViewControl(oxiData, hrvControl,
                oxiResponseHandler, BluetoothDeviceLocator.getInstance().getIOxiBt());
        oxiViewControl.prepareMeasure(SourceType.USBOXI);

        oxiViewControl.startMeasure();

        oxiViewControl.subscribeData(this);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_SPO2_DATA_AVAILABLE);
        return intentFilter;
    }

    private void startGattMeasure() {
        isStartEnabled = true;

        textStatus.setText("Measuring...");
        Settings.isRRLog = true;
        Settings.length = 120;

        com.hecz.stresslocatorcommon.utils.Log.d(Global.APP_LOG_PREFIX, "START with this parameters:"
                + " isRRLog = "+Settings.isRRLog
                + " length = "+Settings.length);

        IHrvData hrvData = new HrvData();
        IOxiData oxiData = new OxiData(hrvData, SourceType.GATT);

        OxiResponseHandler oxiResponseHandler = new OxiResponseHandler(
                oxiData, hrvData);
        HrvControl hrvControl = new HrvControl(oxiData, hrvData);
        oxiViewControl = new OxiViewControl(oxiData, hrvControl,
                oxiResponseHandler, BluetoothDeviceLocator.getInstance().getIOxiBt());
        oxiViewControl.prepareMeasure(SourceType.GATT);

        oxiViewControl.startMeasure();

        oxiViewControl.subscribeData(this);
    }

    private void startGeneratorMeasure() {
        stopBtChecking();
        isStartEnabled = true;

        textStatus.setText("Measuring...");
        Settings.isRRLog = true;
        Settings.length = 120;

        com.hecz.stresslocatorcommon.utils.Log.d(Global.APP_LOG_PREFIX, "START with this parameters:"
                + " isRRLog = "+Settings.isRRLog
                + " length = "+Settings.length);


        IHrvData hrvData = new HrvData();
        IOxiData oxiData = new OxiData(hrvData, SourceType.GENERATOR);

        OxiResponseHandler oxiResponseHandler = new OxiResponseHandler(
                oxiData, hrvData);
        HrvControl hrvControl = new HrvControl(oxiData, hrvData);
        oxiViewControl = new OxiViewControl(oxiData, hrvControl,
                oxiResponseHandler, BluetoothDeviceLocator.getInstance().getIOxiBt());
        oxiViewControl.prepareMeasure(SourceType.GENERATOR);

        oxiViewControl.startMeasure();

        oxiViewControl.subscribeData(this);
    }

    private void showBluetoothState() {
        com.hecz.stresslocatorcommon.utils.Log.i(Global.APP_LOG_PREFIX, "showBluetoothState");

        if (sDevice.equals("Gatt")) {
            // Log.i(Global.APP_LOG_PREFIX, "discovering Gatt");
            // BluetoothLeHrService.start(this);
            if(!isGattServiceStarted) {
                BluetoothLeHrService.start(this);
                isGattServiceStarted = true;
                isStartEnabled = false;
            }
            if (isStartEnabled) {

            }
        } else if (sDevice.equals("StressLocator")) {

            BluetoothLeHrService.stop(this);
            com.hecz.stresslocatorcommon.utils.Log.i(Global.APP_LOG_PREFIX, "showBluetoothState (2)"
                    + ", qConnect = ");
            if (BluetoothDeviceLocator.getInstance().isConnected(sDevice)) {
                //TODO start
                stopBtChecking();
                isStartEnabled = true;

                textStatus.setText("Measuring...");
                Settings.isRRLog = true;
                Settings.length = 120;

                com.hecz.stresslocatorcommon.utils.Log.d(Global.APP_LOG_PREFIX, "START with this parameters:"
                        + " isRRLog = "+Settings.isRRLog
                        + " length = "+Settings.length);


                IHrvData hrvData = new HrvData();
                IOxiData oxiData = new OxiData(hrvData, SourceType.BTOXI);

                OxiResponseHandler oxiResponseHandler = new OxiResponseHandler(
                        oxiData, hrvData);
                HrvControl hrvControl = new HrvControl(oxiData, hrvData);
                oxiViewControl = new OxiViewControl(oxiData, hrvControl,
                        oxiResponseHandler, BluetoothDeviceLocator.getInstance().getIOxiBt());
                oxiViewControl.prepareMeasure(SourceType.BTOXI);

                oxiViewControl.startMeasure();

                oxiViewControl.subscribeData(this);
            } else {

                isStartEnabled = false;

                BluetoothAdapter mBtAdapter = BluetoothAdapter
                        .getDefaultAdapter();

                if (BluetoothDeviceLocator.getInstance().isConnected(sDevice)
                        || isQuickConnect) {
                    BluetoothDeviceLocator.getInstance().scan(sDevice);
                } else {

                    // If we're already discovering, stop it
                    if (mBtAdapter.isDiscovering()) {
                        mBtAdapter.cancelDiscovery();
                    }
                    com.hecz.stresslocatorcommon.utils.Log.i(Global.APP_LOG_PREFIX, "startDiscovery (3)");
                    // Request discover from BluetoothAdapter
                    mBtAdapter.startDiscovery();
                }
                // BluetoothDeviceLocator.getInstance().scan(sDevice);
            }
            com.hecz.stresslocatorcommon.utils.Log.i(Global.APP_LOG_PREFIX, "showBluetoothState (3)");
        }
    }

    private void stopBtChecking() {
        if (!isCheckingThread) {
            isCheckingThread = true;
            new Thread() {
                public void run() {
                    while (isCheckingThread) {
                        boolean isConnected = BluetoothDeviceLocator
                                .getInstance().checkLiveDevice();
                        //Log.d(Global.APP_LOG_PREFIX + "BT","CheckingThread isConnected = "+ isConnected);
                        if (!isConnected) {
                            isCheckingThread = false;

                            showBtInThread();
                            ;
                        }
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                };
            }.start();
        }
    }

    public void showBtInThread() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                long time0 = System.currentTimeMillis();
                showBluetoothState();
                long time1 = System.currentTimeMillis();
                com.hecz.stresslocatorcommon.utils.Log.d(Global.APP_LOG_PREFIX, "TIME(8) = " + (time1 - time0));
            }
        });
    }

    @Override
    public void onConnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textStatus.setText("Connected");
            }
        });

        long time0 = System.currentTimeMillis();
        com.hecz.stresslocatorcommon.utils.Log.i(Global.APP_LOG_PREFIX, "CONNECTED SV2 "
                + BluetoothDeviceLocator.getInstance().isConnected(sDevice));
        // long time0 = System.currentTimeMillis();
        long time1 = System.currentTimeMillis();
        com.hecz.stresslocatorcommon.utils.Log.d(Global.APP_LOG_PREFIX, "TIME(0) = " + (time1 - time0));

        time0 = System.currentTimeMillis();
        showBtInThread();
        time1 = System.currentTimeMillis();
        com.hecz.stresslocatorcommon.utils.Log.d(Global.APP_LOG_PREFIX, "TIME(1) = " + (time1 - time0));
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textStatus.setText("Disconnected");
            }
        });

        com.hecz.stresslocatorcommon.utils.Log.i(Global.APP_LOG_PREFIX, "DISCONNECTED SV2");
        long time0 = System.currentTimeMillis();
        showBtInThread();
        long time1 = System.currentTimeMillis();
        com.hecz.stresslocatorcommon.utils.Log.d(Global.APP_LOG_PREFIX, "TIME(3) = " + (time1 - time0));
        //if (!isPause) {
            time0 = System.currentTimeMillis();
            BluetoothDeviceLocator.getInstance().scan(sDevice);
            time1 = System.currentTimeMillis();
            com.hecz.stresslocatorcommon.utils.Log.d(Global.APP_LOG_PREFIX, "TIME(2) = " + (time1 - time0));
        //}
    }

    @Override
    public void onMustBePaired() {

    }

    @Override
    public void showCurrentOxiData(long ad) {
        //msTime = oxiData.getRealTime();

        // ad is value for plethysmogram
        //getPlethChart().update(ad);
    }

    @Override
    public void showNextPulse(final IOxiData oxiData, final IHrvData hrvData) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textStatus.setText("Lines:"+oxiData.getRRLines()+", Pulse:"+oxiData.getPulse()+", Spo2:"+oxiData.getSpO2()+", Coherence = "+coherence);
            }
        });

    }

    @Override
    public void updateHrvCharts(HrvControl hrvControl) {
        if(hrvControl != null) {
            if(hrvControl.hrvData != null) {
                coherence = ""+hrvControl.getHrvResults().coherence;
            }


            /*
            HrvResults hrvResults = hrvControl.getHrvResults();

            pieAns.update(hrvResults);

            hrvChart.update(hrvResults.SDNN);

            //breathChart.update(hrvResults.SDNN);

            psdchart.update(hrvControl.getPowerSpectralDensity());

            hrvChart.update(hrvControl.getHrvArray(), hrvControl.getLasttime());

            //breathChart.update(hrvControl.getHrvArray(), hrvControl.getLasttime());

            poincareChart
                    .update(hrvControl.getHrvArray(), hrvControl.getLasttime());

            pulseChart.update(hrvControl.getHrvArray(), hrvControl.getLasttime());
            */
        }
    }

    @Override
    public void hasNextNN() {

    }

    @Override
    public void disconnect() {

    }

    @Override
    public void onSpO2ParamsChanged() {
        if (oxiViewControl != null) {
            IOxiObserver eventSource = oxiViewControl.getEventSource();
            if (eventSource == null) {
                return;
            }

            OxiBtData oxiBtData = new OxiBtData();
            oxiBtData.signalStrenght = 0;

            oxiBtData.rr = (double) mPackageParser.getOxiParams().rrInterval / 500.0;
            oxiBtData.pulse = mPackageParser.getOxiParams().getPulseRate();
            oxiBtData.spO2 = mPackageParser.getOxiParams().getSpo2();
            oxiBtData.readCounter = readCounter++;

            Log.d(Settings.APP_LOG_PREFIX + "ad", "onSpO2ParamsChanged - rr = " + oxiBtData.rr);

            //eventSource.updateRR(mPackageParser.getOxiParams().rrInterval*2, oxiBtData.pulse);

            eventSource.updateOxiData(oxiBtData);
        }
    }

    @Override
    public void onSpO2WaveChanged(long wave) {
        if (oxiViewControl != null) {
            IOxiObserver eventSource = oxiViewControl.getEventSource();
            if (eventSource == null) {
                return;
            }
            long ad = wave;

            OxiBtData oxiBtData = new OxiBtData();
            oxiBtData.signalStrenght = 0;
            if (sourceType == SourceType.USBOXI) {
                //usbtime += 0.0197;

                if (mDataParser.mCurProtocol == DataParser.Protocol.ISRAEL) {
                    //ISRAEL
                    usbtime += 0.005;
                } else {
                    usbtime += 0.0197;
                }
            } else {

                if (mDataParser.mCurProtocol == DataParser.Protocol.ISRAEL) {
                    //ISRAEL
                    usbtime += 0.005;
                } else {
                    usbtime += 0.01;
                }
            }
            ;
            if (lastTime == 0) {
                lastTime = System.currentTimeMillis();
            }
            nTime++;
            //Log.d(Settings.APP_LOG_PREFIX + "ad", "onSpO2WaveChanged - pulse = " + mPackageParser.getOxiParams().getPulseRate() + "time = " + ((double) (System.currentTimeMillis() - lastTime) / nTime));
            //usbtime = System.currentTimeMillis()-lastTime;
            oxiBtData.time = usbtime;///1000.0;
            //drr = Math.abs(drr);
            //if (drr > 10000) {
            //    drr = 65536 - drr;
            //}
            oxiBtData.ad = ad;

            //oxiBtData.sar = 0;
            oxiBtData.pulse = mPackageParser.getOxiParams().getPulseRate();
            oxiBtData.spO2 = mPackageParser.getOxiParams().getSpo2();
            oxiBtData.readCounter = readCounter++;

            //eventSource.updateRR(mPackageParser.getOxiParams().rrInterval*2, oxiBtData.pulse);

            eventSource.updateOxiData(oxiBtData);
        }
    }
}

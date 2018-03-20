package com.google.firebase.udacity.friendlychat;

/**
 * Created by TomiwaAdemidun on 2017-04-13.
 */

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static android.widget.Toast.LENGTH_LONG;
import static com.google.firebase.udacity.friendlychat.ChatListActivity.myLogPrint;
import static com.google.firebase.udacity.friendlychat.ChatListActivity.myLogPrintLN;


public class PhlockControlActivity extends AppCompatActivity {

    Button btnOn, btnOff, btnDis;
    SeekBar brightness;
    TextView lumn;
    String address = null;
    private ProgressDialog progress;
    private ProgressDialog progressInternalRead;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    protected boolean mQuickUnlock = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    static final int CHECK_BT_RESULT_CODE = 1;
    protected static String mKeyName, mKeyID, mKeyCode, mKeyOwner;
    private static final String LOG_TAG = "PhlockControlActivityDEBUG: ";
    private String mMajorKey;
    String internalCode;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //receive the address of the bluetooth device
        Intent intent = getIntent();
        address = intent.getStringExtra(PhlockListActivity.EXTRA_ADDRESS);

        if (intent!= null && intent.hasExtra("key_id") && intent.hasExtra("key_code")) {
            //Yes, I know this is redundant but just for testing purposes lets leave it.
            mKeyID = intent.getStringExtra("key_name");
            mKeyName = intent.getStringExtra("key_id");
            mKeyCode = intent.getStringExtra("key_code");
            mKeyOwner = intent.getStringExtra("key_owner");

            if(intent.hasExtra("quick_unlock")&&intent.hasExtra(PhlockListActivity.EXTRA_ADDRESS)){
                mQuickUnlock = intent.getBooleanExtra("quick_unlock",false);
                address = intent.getStringExtra(PhlockListActivity.EXTRA_ADDRESS);
            }
        }
        myLogPrint("Just started the Phlock Control Activity.");
        /*
            myLogPrint(LOG_TAG,"The keyid is: "+ mKeyID);
            internalCode = readKeyFromInternalStorage(mKeyID);

            if (internalCode != null && !internalCode.equals("")) {
                mMajorKey = internalCode;
                mMajorKey = "unlck456_"+mMajorKey;
                myLogPrintLN(LOG_TAG, "OG Code is: " + internalCode);
                myLogPrintLN(LOG_TAG, "OG mMajorKey 2: " + mMajorKey);

            } else {
                mMajorKey=mKeyCode;
                mMajorKey = "unlck456_"+mMajorKey;
                myLogPrintLN(LOG_TAG,"Backup Code is: "+mKeyCode);
                myLogPrintLN(LOG_TAG,"mMajorKey 3: "+mMajorKey);
        }*/

        //view of the ledControl layout
        setContentView(R.layout.activity_led_control);
        //call the widgets
        btnOn = (Button)findViewById(R.id.on_btn);
        btnOff = (Button)findViewById(R.id.off_btn);
        btnDis = (Button)findViewById(R.id.disconnect_btn);
        brightness = (SeekBar)findViewById(R.id.brightness_seekbar);
        lumn = (TextView)findViewById(R.id.brightness_seekbar_descr);

        if (mQuickUnlock) {
            //if the device has bluetooth
            checkBluetooth();
        }
        else {
            ConnectBTTask connectBT = new ConnectBTTask();
            connectBT.execute(); //Call the class to connect
        }

        //commands to be sent to bluetooth
        btnOn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                unlockPhlock();      //method to turn on
            }
        });

        btnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                lockPhlock();   //method to turn off
            }
        });

        btnDis.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Disconnect(); //close connection
            }
        });

        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser==true)
                {
                    lumn.setText(String.valueOf(progress));
                    try
                    {
                        btSocket.getOutputStream().write(String.valueOf(progress).getBytes());
                    }
                    catch (IOException e)
                    {

                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

    }

    private void quickUnlock() {

            if (mQuickUnlock) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            unlockPhlock();
                        }
                    }, 1000);
                mQuickUnlock=false;
                }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void unlockPhlock()
    {
        if (btSocket!=null)
        {   myLogPrint("Inside unlockPhlock()");
            try
            {
                if (mMajorKey==null) {
                    try {
                        mMajorKey = new ReadKeyFromInternalStorage().get(1000, TimeUnit.MILLISECONDS);
                    }catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                btSocket.getOutputStream().write(mMajorKey.getBytes());
            }
            catch (IOException e)
            {
                msgSnack("Error");
            }
        }
    }

    private void lockPhlock()
    {
        if (btSocket!=null)
        {
            try
            {
                btSocket.getOutputStream().write("lck789ab".getBytes());
            }
            catch (IOException e)
            {
                Snackbar.make(findViewById(R.id.brightness_seekbar_descr),"Error",Snackbar.LENGTH_LONG);
            }
        }
    }


    private void Disconnect()
    {
        if (btSocket!=null) //If the btSocket is busy
        {
            try
            {
                btSocket.close(); //close connection
            }
            catch (IOException e)
            { msg("Error");}
        }
        finish(); //return to the first layout
    }

    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    private void msgSnack(String s)
    {
        Snackbar.make(findViewById(R.id.brightness_seekbar_descr),s,Snackbar.LENGTH_LONG);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_device_list, menu);
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

    private class ConnectBTTask extends AsyncTask<Void, Void, Void> // UI thread
    {
        private boolean ConnectSuccess = true;
        @Override
        protected void onPreExecute()
        {
            progress = new ProgressDialog(PhlockControlActivity.this);
            progress.setTitle("Connecting to Bluetooth...");
            progress.setMessage("Please wait!!!");
            progress.setCancelable(true);
            progress.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    try {
                        progress.cancel();
                        myLogPrint("Connection took to long, user cancelled.");
                        if (btSocket!=null) {
                            myLogPrint("User cancelled, closing btSocket");
                            btSocket.close();
                            cancel(true);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            progress.show();

        }
        @Override
        protected Void doInBackground(Void... devices)
        {
            try
            {

                if(isBtConnected){
                    myLogPrint(String.format("BT is connected: %s",isBtConnected));
                }
                else{
                    myLogPrint(String.format("BT is NOT connected: %s",isBtConnected));
                }
                if (btSocket == null) {
                    myLogPrint("BT socket is null");
                }
                else{
                    myLogPrint("BT socket is NOT null");
                }
                if (btSocket == null || !isBtConnected)
                {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();
                    myLogPrint(String.format("Searching for BT device with key: %s",address));
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);
                    btSocket =
                            dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    myLogPrint("Just tried creating Socket about to try btSocket.connect()");
                    btSocket.connect();
                }
            }
            catch (IOException e)
            {
                try {
                    btSocket.close();
                    Thread.sleep(500);
                    myLogPrint(String.format("Fallback connection: %s", e.getMessage()));
                    //fallback connection method
                    btSocket = myBluetooth.getRemoteDevice(address).createInsecureRfcommSocketToServiceRecord(myUUID);
                    btSocket.connect();
                    //fallback connection
                    e.printStackTrace();
                    ConnectSuccess = false;
                } catch (IOException e2) {
                    myLogPrint(String.format("Fallback failed: %s", e2.getMessage()));
                    e2.printStackTrace();
                }catch (InterruptedException e1) {//for Thread.sleep(500);
                    Log.e("ChatListActivity:Intrpt", e1.getMessage(), e1);
                }
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result)
        {
            super.onPostExecute(result);
            if (!ConnectSuccess)
            {
                Toast.makeText(getApplicationContext(), "Connection Failed. Is it a SPP Bluetooth? Try again.",
                        LENGTH_LONG).show();
                //msg("Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            }
            else
            {
                Toast.makeText(getApplicationContext(), "Connected.",
                        LENGTH_LONG).show();

                //msg("Connected.");
                isBtConnected = true;
            }
            progress.dismiss();

            new ReadKeyFromInternalStorage().execute();
        }

        @Override
        protected void onCancelled() {
            finish();

            myLogPrint("User cancelled, finishing activity");
        }
    }

    private void checkBluetooth() {
        myLogPrint("Checking if bluetooth connection exists...");
        myBluetooth = BluetoothAdapter.getDefaultAdapter();
        if(myBluetooth == null)
        {
            //Show a message that the device has no bluetooth adapter
            Toast.makeText(getApplicationContext(), "Bluetooth Device Not Available", Toast.LENGTH_LONG).show();
            //finish apk
            finish();
        }
        //if the device has bluetooth but it has not been enabled
        else if(!myBluetooth.isEnabled())
        {
            //Ask to the user turn the bluetooth on
            Intent turnBTon = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnBTon,CHECK_BT_RESULT_CODE);
            /**check {@link #onActivityResult(int, int, Intent)} for the execution of connecting if bluetooth was enabled*/
        }
        else{
            ConnectBTTask connectBT = new ConnectBTTask();
            connectBT.execute(); //Call the class to connect
        }
    }

    //// TODO: 2017-05-14 Consider showing the progressdialog for reading from internal storage 
    //// TODO: 2017-05-14 Put the other key reads into AsyncTas as well 
    private class ReadKeyFromInternalStorage extends AsyncTask<String, Void, String>{
        /**
         * Check to see if the key code was pulled from the internal memory or from cloud.
         */
        boolean isInternalKey;
        @Override
        protected void onPreExecute() {
            myLogPrint(LOG_TAG,"The keyid is: "+ mKeyID);
            progressInternalRead = ProgressDialog.show(PhlockControlActivity.this, "Reading Internal Storage", "Please wait! Retrieving code...");
        }

        @Override
        protected String doInBackground(String... params) {

            internalCode = readKeyFromInternalStorage(mKeyID);

            isInternalKey = internalCode!=null;
            if (internalCode != null && !internalCode.equals("")) {
                mMajorKey = internalCode;
                mMajorKey = "unlck456_"+mMajorKey;

            } else {
                mMajorKey=mKeyCode;
                mMajorKey = "unlck456_"+mMajorKey;
            }

            return mMajorKey;
        }

        /**
         * Display in UI if the code was read from internal storage or from cloud {@link ReadKeyFromInternalStorage#isInternalKey}
         * @param result
         */
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            myLogPrintLN(LOG_TAG, "OG Code is: " + internalCode);
            if (isInternalKey) {
                myLogPrintLN(LOG_TAG, "OG mMajorKey FROM INTERNAL: " + mMajorKey);
            }
            else{
                myLogPrintLN(LOG_TAG, "OG mMajorKey FROM CLOUD: " + mMajorKey);
            }
            progressInternalRead.dismiss();
            quickUnlock();
        }
    }
    protected String readKeyFromInternalStorage(String fileContent) {

        //Now we read the key code from internal storage
        try {
            myLogPrint("Inside Second Trying the write to internal: Line 686");

            BufferedReader inputReader = new BufferedReader(new InputStreamReader(
                                                                                         openFileInput(fileContent)));
            String inputString;
            StringBuffer stringBuffer = new StringBuffer();

            while ((inputString = inputReader.readLine()) != null) {
                stringBuffer.append(inputString + "\n");
            }

            myLogPrintLN("The KeyCode in internal storage is: ", stringBuffer.toString());
            return stringBuffer.toString();

        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == CHECK_BT_RESULT_CODE) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                ConnectBTTask connectBT = new ConnectBTTask();
                connectBT.execute(); //Call the class to connect
            }
        }
    }
}


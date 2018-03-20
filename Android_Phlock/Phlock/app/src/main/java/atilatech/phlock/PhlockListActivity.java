package com.google.firebase.udacity.friendlychat;

/**
 * Created by TomiwaAdemidun on 2017-04-13.
 */


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;

import static com.google.firebase.udacity.friendlychat.ChatListActivity.myLogPrint;

public class PhlockListActivity extends AppCompatActivity {

    Button btnPaired;
    ListView devicelist;

    private BluetoothAdapter myBluetooth = null;
    private Set<BluetoothDevice> pairedDevices;
    private OutputStream outputStream = null;
    public static String EXTRA_ADDRESS = "device_address";
    protected static String mKeyName, mKeyID, mKeyCode, mKeyOwner, mKeyAddress;
    protected static boolean mQuickUnlock = false;
    protected boolean mIsCorrectAddress, mIsKeyRegistered;
    private String intent_mode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        btnPaired = (Button)findViewById(R.id.show_devices_btn);
        devicelist = (ListView)findViewById(R.id.show_devices_listview);


        Intent intent = this.getIntent();
        if (intent!=null && intent.hasExtra("intent_mode")) {
            intent_mode=intent.getStringExtra("intent_mode");
        }

        if (intent != null && intent.hasExtra("key_id") && intent.hasExtra("key_code")) {
            mKeyID = intent.getStringExtra("key_name");
            mKeyName = intent.getStringExtra("key_id");
            mKeyCode = intent.getStringExtra("key_code");
            mKeyOwner = intent.getStringExtra("key_owner");

            if(intent.hasExtra("quick_unlock")&&intent.hasExtra(PhlockListActivity.EXTRA_ADDRESS)){
                mQuickUnlock = intent.getBooleanExtra("quick_unlock",false);
                mKeyAddress = intent.getStringExtra(PhlockListActivity.EXTRA_ADDRESS);
            }
        }

        //if the device has bluetooth
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
            startActivityForResult(turnBTon,1);
        }

        btnPaired.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                pairedDevicesList(); //method that will be called
            }
        });

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab_add_chat);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        /**
         *  Repeats the code from {@link ChatListActivity#launchUnlockPhlockQuick(PhlockKey)}
         */
        if (mQuickUnlock) {

        myLogPrint(String.format("About to QUICKLY launch PhlockControl from Phlock list for key address: %s",mKeyAddress));
            Intent intent = new Intent(PhlockListActivity.this, PhlockControlActivity.class);
            //Change the activity.
            intent.putExtra(EXTRA_ADDRESS, mKeyAddress)
                    .putExtra("key_name",mKeyName)
                    .putExtra("key_code",mKeyCode)
                    .putExtra("key_id",mKeyID)
                    .putExtra("key_owner",mKeyOwner)
                    .putExtra("quick_unlock",mQuickUnlock);
            startActivity(intent);
            mQuickUnlock=false;
        }
    }

    /**
     * Populate listview with nearby bluetooth devices
     */
    private void pairedDevicesList() {
        pairedDevices = myBluetooth.getBondedDevices();
        ArrayList list = new ArrayList();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice bt : pairedDevices) {
                list.add(bt.getName() + "\n" + bt.getAddress()); //Get the device's name and the address

            }
        } else {
            Toast.makeText(getApplicationContext(), "No Paired Bluetooth Devices Found.",
                    Toast.LENGTH_LONG).show();
        }
        final ArrayAdapter adapter = new
                                             ArrayAdapter(this, android.R.layout.simple_list_item_1, list);
        devicelist.setAdapter(adapter);
        devicelist.setOnItemClickListener(myListClickListener);
        //Method called when the device from the list is clicked
    }
    private AdapterView.OnItemClickListener
            myListClickListener = new AdapterView.OnItemClickListener(){

                  public void onItemClick (AdapterView<?> av, View v, int arg2, long arg3)
                  {
                      // Get the device MAC address, the last 17 chars in the View
                      String info = ((TextView) v).getText().toString();
                      String address = info.substring(info.length() - 17);

                      if (intent_mode!=null && intent_mode.equals("CreateNewKey")) {
                          // Make an intent to start next activity.
                          Intent i = new Intent(PhlockListActivity.this, NewKeyActivity.class);
                          //Change the activity.
                          i.putExtra(EXTRA_ADDRESS, address);
                          startActivity(i);
                      }
                      else{
                          // Make an intent to start next activity.
                          if (!mKeyAddress.equals(address)) {//if the key address does not match up with
                              //the lock, go back to previous activity
                              View parentView = findViewById(R.id.phlock_list_activity_layout);
                              Snackbar.make(parentView,getString(R.string.wrong_key_address),Snackbar.LENGTH_SHORT)
                                      .setAction("Change Key", new View.OnClickListener() {
                                          @Override
                                          public void onClick(View view) {
                                              Intent intent = new Intent(PhlockListActivity.this, ChatListActivity.class);
                                              startActivity(intent);
                                          }
                                      });
                          }
                          Intent i = new Intent(PhlockListActivity.this, PhlockControlActivity.class);
                          //Change the activity.
                          i.putExtra(EXTRA_ADDRESS, address)
                                  .putExtra("key_name",mKeyName)
                                  .putExtra("key_code",mKeyCode)
                                  .putExtra("key_id",mKeyID)
                                  .putExtra("key_owner",mKeyOwner);
                          //this will be received at ledControl (class) Activity
                          startActivity(i);
                      }

                  }
              };




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
}


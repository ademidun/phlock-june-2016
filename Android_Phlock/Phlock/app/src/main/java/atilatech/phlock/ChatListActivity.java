package com.google.firebase.udacity.friendlychat;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewAnimator;

import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.firebase.udacity.friendlychat.ChatScreenActivity.ANONYMOUS;
import static com.google.firebase.udacity.friendlychat.ChatScreenActivity.GOOGLE_CONTACTS_API;
import static com.google.firebase.udacity.friendlychat.ChatScreenActivity.RC_SIGN_IN;


public class ChatListActivity extends AppCompatActivity implements PopupMenu.OnMenuItemClickListener{

    private static final String LOG_TAG = "ChatListActivityDEBUG: ";

    private String mUsername;
    protected static PhlockUser mPhlockUser;
    private ListView mUserListView;
    private UserListAdapter mUserAdapter;
    private PhlockKeyAdapter mKeyAdapter;
    String mChatKey;
    AlertDialog.Builder dialogBuilder;
    AlertDialog mAlertDialog;
    boolean isExistingChat = false;
    List<PhlockKey> sender_phlock_keys = new ArrayList<>();
    Set<String> sender_keys_map = new HashSet<>();
    Set<String> keyAdapterMap = new HashSet<>();
    ImageButton mCloseKeyListBtn;
    PopupMenu shareKeyPopupMenu;
    private ViewAnimator mViewAnim;
    boolean mIsKeyRegistered = false;
    private Context mContext;
    private float initialX;


    //Firebase instance variables
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mChatDatabaseReference;
    private DatabaseReference mUsersDatabaseReference;
    private DatabaseReference mKeysDatabaseReference;

    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private ChatScreenActivity chatScreenActivity;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_chat_list);
        mContext = this;
        mViewAnim = (ViewAnimator)findViewById(R.id.chat_list_view_anim);
        final Animation inAnim = AnimationUtils.loadAnimation(this,android.R.anim.slide_in_left);
        final Animation outAnim = AnimationUtils.loadAnimation(this,android.R.anim.slide_out_right);
        mViewAnim.setInAnimation(inAnim);
        mViewAnim.setOutAnimation(outAnim);


        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        chatScreenActivity = new ChatScreenActivity();

        mUsername = ANONYMOUS;
        //mUserListView = (ListView) findViewById(R.id.userListViewHome);

        //Initialize Firebase components
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();

        mChatDatabaseReference = mFirebaseDatabase.getReference().child("chats");
        mUsersDatabaseReference = mFirebaseDatabase.getReference().child("users");
        mKeysDatabaseReference = mFirebaseDatabase.getReference().child("keys");

        //Now that the user has signed in, we will synchronize CloudKeys with Local storage keys
        synchronizeLocalAndCloudKeys();

        //// TODO: 2017-05-21 Populate Listview if chats exist for this user
        //showExistingChats();

        FloatingActionButton fab_NewChatOrKey = (FloatingActionButton) findViewById(R.id.fab_add_chat);
        FloatingActionButton fabKeyList = (FloatingActionButton) findViewById(R.id.fab_key_list);
        fab_NewChatOrKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                showShareKeyPopup ();
            }
        });

        fabKeyList.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                if (mViewAnim.getDisplayedChild()==0) {//main screen
                    mViewAnim.setDisplayedChild(1);
                    showListOfKeys();
                }
                else{
                    mViewAnim.setDisplayedChild(0);
                }
            }

        });

        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user !=null){
                    //user is signed in
                    String msg = String.format("Hey %s, Welcome to Phlock.",user.getDisplayName());
                    Toast.makeText(ChatListActivity.this, msg, Toast.LENGTH_SHORT).show();
                    onSignedInInitialize(user.getDisplayName());
                }   else {
                    //user is signed out
                    //// TODO: 2017-05-20 We may want to create a custom sign in flow instead of FirebaseUI

                    onSignedOutCleanup();
                    AuthUI.IdpConfig googleIdp = new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER)
                                                         .setPermissions(Arrays.asList(GOOGLE_CONTACTS_API))
                                                         .build();
                    AuthUI.IdpConfig facebookIdp = new AuthUI.IdpConfig.Builder(AuthUI.FACEBOOK_PROVIDER)
                                                           .setPermissions(Arrays.asList("user_friends"))
                                                           .build();
                    //// TODO: 2017-05-20 Enable SmartLock
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setProviders(Arrays.asList(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                                            googleIdp,
                                            facebookIdp))
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };

    }

    private void showExistingChats() {

                //Now we populate the list of returned users
                // Initialize User ListView and its adapter
                /*List<PhlockUser> phlockUsers = SearchFirebaseForPhlockChats(mPhlockUser.getUid());

                mUserAdapter = new UserListAdapter(mViewAnim.getCurrentView().getContext(), R.layout.item_user, phlockUsers);

                myLogPrint("User Queried: " + userQuery);
                if(mUserListView==null){

                    mUserListView = (ListView) findViewById(R.id.userListView);
                }
                mUserListView.setAdapter(mUserAdapter);

                for (PhlockUser pu :phlockUsers) {
                    mUserAdapter.add(pu);
                }
                mUserListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        PhlockUser selectedUser = mUserAdapter.getItem(i);
                        launchNewChat(selectedUser);
                    }
                });*/

    }

    /**
     * VERY Simliar to {@link ChatScreenActivity getListOfKeys()}
     */
    private void synchronizeLocalAndCloudKeys() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();


        if(user ==null){
            return;
        }
        mUsersDatabaseReference.child(user.getUid()).child("keys_owned")
                    .addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if (dataSnapshot.exists()) {

                                myLogPrint(LOG_TAG, "Synchronizing the Local and Cloud keys: " + dataSnapshot.toString());
                                myLogPrint(LOG_TAG, "These are the sender's keys(dataSnapshot.toString()): " + dataSnapshot.toString());
                                myLogPrint(LOG_TAG, "These are the sender's keys: " + dataSnapshot.getValue().toString());
                                myLogPrint(LOG_TAG, "The Recipients key id(s?): " + dataSnapshot.getKey());
                                //The sender may have multiple chats, so we need to see which of the chats it shares with recipient
                                //so For each (chat d: that is one of sender_chats.getChildren)
                                queryChildrenForKeys(dataSnapshot,mKeysDatabaseReference,sender_keys_map,sender_phlock_keys);

                                //// TODO: 2017-05-22 Very innefficient, must be a better way to do this.
                                for ( PhlockKey pk : new ArrayList<>(sender_phlock_keys)) {
                                    if (sender_keys_map.add(pk.getKey_id())) {
                                        mKeyAdapter.add(pk);
                                    }
                                }

                            }
                            else{
                                myLogPrint("No key exists so I will endeavor to create one");
                                //If user wants a key, they should create one in NewKey item, not here
                                //createPhlockKey();
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });

        //Now do something similiar for keys shared
        mUsersDatabaseReference.child(user.getUid()).child("keys_shared")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {

                            myLogPrint(LOG_TAG, "Synchronizing the Local and Cloud keys: " + dataSnapshot.toString());
                            myLogPrint(LOG_TAG, "These are the sender's keys(dataSnapshot.toString()): " + dataSnapshot.toString());
                            myLogPrint(LOG_TAG, "These are the sender's keys: " + dataSnapshot.getValue().toString());
                            myLogPrint(LOG_TAG, "The Recipients key id(s?): " + dataSnapshot.getKey());
                            //The sender may have multiple chats, so we need to see which of the chats it shares with recipient
                            //so For each (chat d: that is one of sender_chats.getChildren)
                            queryChildrenForKeys(dataSnapshot,mKeysDatabaseReference,sender_keys_map,sender_phlock_keys);

                            //// TODO: 2017-05-22 Very innefficient, must be a better way to do this.
                            for ( PhlockKey pk : new ArrayList<>(sender_phlock_keys)) {
                                if (sender_keys_map.add(pk.getKey_id())) {
                                    mKeyAdapter.add(pk);
                                }
                            }
                        }
                        else{
                            myLogPrint("No key exists so I will endeavor to create one");
                            //If user wants a key, they should create one in NewKey item, not here
                            //createPhlockKey();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });

        for (PhlockKey pk : sender_phlock_keys) {
            //save each Key to internal storage
            chatScreenActivity.writeKeyToInternalStorage(pk.getKeyname(),pk.getKey_code());
        }
    }

    /**
     * Very similar logic to {@link #showCreateChatDialog() }
     */
    private void showListOfKeys() {

        /*setContentView(R.layout.create_new_chat);
        setRecreateActivityButton();
*/
        View currView = mViewAnim.getCurrentView();
        final EditText edt = (EditText) currView.findViewById(R.id.searchUserEditText);
        final Button searchBtn = (Button) mViewAnim.getCurrentView().findViewById(R.id.searchUserButton);
        final ImageButton closeViewBtn = (ImageButton) mViewAnim.getCurrentView().findViewById(R.id.close_KeyListBtn);
        final TextView textView = (TextView) mViewAnim.getCurrentView().findViewById(R.id.key_user_list_instruct);

        textView.setText(R.string.search_key_instructions);
        edt.setVisibility(View.INVISIBLE);
        mUserListView = (ListView) findViewById(R.id.userListView);

        if(sender_phlock_keys.isEmpty()){ //inCase the key list got cleared out
            synchronizeLocalAndCloudKeys();
        }
        mKeyAdapter = new PhlockKeyAdapter
                              (mUserListView.getContext(),R.layout.item_user,sender_phlock_keys);
        mUserListView.setAdapter(mKeyAdapter);
        mKeyAdapter.notifyDataSetChanged();
        closeViewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mViewAnim.showPrevious();
            }
        });
        //If I don't do this, I get a concurrent mod. exception, why?
        for ( PhlockKey pk :  new ArrayList<>(sender_phlock_keys)) {
            if (sender_keys_map.add(pk.getKey_id())) {
                mKeyAdapter.add(pk);
            }
        }
        if (!mUserListView.isShown()) {
            mUserListView.setVisibility(View.VISIBLE);
        }

        mUserListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                PhlockKey selectedKey = mKeyAdapter.getItem(i);
                launchUnlockPhlock(selectedKey);
            }
        });
        mUserListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                PhlockKey selectedKey = mKeyAdapter.getItem(i);
                launchUnlockPhlockQuick(selectedKey);
                return true;
            }
        });
    }

    private void launchUnlockPhlock(PhlockKey phlockKey, Boolean ... checkKeyReg) {
        myLogPrint(String.format("About to use the following key to unlock the door: %s",phlockKey.toString()));

        //// TODO: 2017-04-13 Consider using Parcelable for this.
        Intent intent = new Intent(this,PhlockListActivity.class)
                                .putExtra("key_name",phlockKey.getKeyname())//key value pair
                                .putExtra("key_id",phlockKey.getKey_id())//key value pair
                                .putExtra("key_code",phlockKey.getKey_code())
                                .putExtra("key_owner",phlockKey.getOwner());

        //basically just checks to see if the key has been registered yet
        boolean keyRegSet = (checkKeyReg.length >= 1);
        if (keyRegSet) {
            intent.putExtra("register_key",checkKeyReg[0]);
        }
        startActivity(intent);
    }
    //// TODO: 2017-05-12  launchUnlockPhlockQuick() might be inefficient and may have too much repetition
    private void launchUnlockPhlockQuick(PhlockKey phlockKey) {

        boolean isRegistered = true;

        TextView ownershipTextView = (TextView) mViewAnim.getCurrentView().findViewById(R.id.secondaryItemTextViewUser);
        String userID = mFirebaseAuth.getCurrentUser().getUid();
        if(!phlockKey.getOwner().equals(userID)){
            isRegistered = checkIfKeyRegistered(phlockKey);
        }

        if (!isRegistered) {
            launchUnlockPhlock(phlockKey,isRegistered);
        }
        else {

            myLogPrint(String.format("About to use the following key to QUICKLY unlock the door: %s",phlockKey.toString()));


            //// TODO: 2017-04-13 Consider using Parcelable for this.
            Intent intent = new Intent(this,PhlockListActivity.class)
                                    .putExtra("key_name",phlockKey.getKeyname())//key value pair
                                    .putExtra("key_id",phlockKey.getKey_id())//key value pair
                                    .putExtra("key_code",phlockKey.getKey_code())
                                    .putExtra("key_owner",phlockKey.getOwner())
                                    .putExtra(PhlockListActivity
                                                      .EXTRA_ADDRESS,phlockKey.getKey_address())
                                    .putExtra("quick_unlock",true);
            startActivity(intent);
        }
    }

    private boolean checkIfKeyRegistered(PhlockKey phlockKey) {

        String keyId = phlockKey.getKey_id();
        String userId = mFirebaseAuth.getCurrentUser().getUid();
        Query query = mUsersDatabaseReference.child(userId).child("keys_shared").child(keyId);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                myLogPrint(LOG_TAG,String.format("dataSnapshot.toString(): %s",dataSnapshot.toString()));
                myLogPrint(LOG_TAG,String.format("dataSnapshot.getValue(): %s",dataSnapshot.getValue()));
                mIsKeyRegistered = dataSnapshot.getValue(Boolean.class);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
        return mIsKeyRegistered;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mAuthStateListener!=null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        /*TODO: May be able remove the if statement below later
        getDetachDatabaseReadListner();
        mMessageAdapter.clear();
         */
        if(mUserAdapter!=null) {
            mUserAdapter.clear();
        }
        if(mKeyAdapter!=null) {
            mKeyAdapter.clear();
        }

        if( mAlertDialog!= null && mAlertDialog.isShowing()){
            mAlertDialog.dismiss();
        }

    }

    private void onSignedInInitialize(String username){
        mUsername = username;
        //attachDatabaseReadListener();

    }

    private void onSignedOutCleanup(){
        mUsername = ANONYMOUS;
        //mMessageAdapter.clear();
        //detachDatabaseReadListner();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.sign_out_menu:
                //sign out
                AuthUI.getInstance().signOut(this);
            default:
                return super.onOptionsItemSelected(item);
        }
    }



    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                saveNewUserToDatabase();
                Toast.makeText(this, "Signed in!", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Sign in cancelled.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void saveNewUserToDatabase() {
        final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        final String username = user.getDisplayName();
        final String uid = user.getUid();
        final String email = user.getEmail();

        mUsersDatabaseReference.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {

                DatabaseReference queryExisting = mUsersDatabaseReference.orderByChild("email")
                                      .equalTo(email).getRef();
                queryExisting.getKey();
                Log.d(LOG_TAG,"The query results: "+queryExisting.toString());
                if (snapshot.exists()) {
                    // TODO: handle the case where the data already exists
                    Log.d(LOG_TAG,"The node already exists: "+mUsersDatabaseReference.child(uid).toString());
                    return;
                }
                else {
                    // TODO: handle the case where the data does not yet exist
                    // TODO: 2017-04-08 It seems like the Phlock user date_created will only show up in database if we create it
                    //in this clas and not in the Firebase class
                    long dateNow = System.currentTimeMillis();
                    PhlockUser phlockUser = new PhlockUser(username,uid,email,dateNow);
                    mUsersDatabaseReference.child(uid).setValue(phlockUser);
                    Log.d(LOG_TAG,"The node was succesfully added: "+mUsersDatabaseReference.child(uid).toString());
                    Log.d(LOG_TAG,"The query results AFTER database addition: "+queryExisting.toString());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) { }
        });

    }


    private void showCreateChatDialog(){

/*        dialogBuilder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.create_new_chat, null);

        dialogBuilder.setTitle("Search for User");
        dialogBuilder.setMessage("Enter user to start a chat with");
        dialogBuilder.setView(dialogView);

        setContentView(R.layout.create_new_chat);
        setRecreateActivityButton();*/


        mViewAnim.setDisplayedChild(1);
        View currView = mViewAnim.getCurrentView();
        final EditText edt = (EditText) currView.findViewById(R.id.searchUserEditText);
        final Button searchBtn = (Button) mViewAnim.getCurrentView().findViewById(R.id.searchUserButton);
        final ImageButton closeViewBtn = (ImageButton) mViewAnim.getCurrentView().findViewById(R.id.close_KeyListBtn);
        final TextView textView = (TextView) mViewAnim.getCurrentView().findViewById(R.id.key_user_list_instruct);
        edt.setVisibility(View.VISIBLE);
        textView.setText(R.string.search_user_instructions);

        mUserListView = (ListView) findViewById(R.id.userListView);
        mUserListView.setAdapter(null);

        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //// TODO: 2017-05-21 Add Search by first or last name feature.
                String userQuery = edt.getText().toString();
                myLogPrint("User Queried: " + userQuery);

                //Now we populate the list of returned users
                // Initialize User ListView and its adapter
                List<PhlockUser> phlockUsers = SearchFirebaseForPhlockUsers(userQuery);

                mUserAdapter = new UserListAdapter(view.getContext(), R.layout.item_user, phlockUsers);
                mUserListView.setAdapter(mUserAdapter);
                myLogPrint("User Queried: " + userQuery);
                for (PhlockUser pu :phlockUsers) {
                    mUserAdapter.add(pu);
                }
                mUserAdapter.notifyDataSetChanged();
                mUserListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        PhlockUser selectedUser = mUserAdapter.getItem(i);
                        launchNewChat(selectedUser);
                    }
                });


            }
        });

        closeViewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mViewAnim.showPrevious();
            }
        });
/*
        dialogBuilder.setPositiveButton("Search", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                //TODO: Search Firebase Database
                //What ever you want to do with the value
                String userQuery = edt.getText().toString();
                myLogPrint("User Queried: " + userQuery);
            }
        });

        dialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.cancel();
            }
        });



        mAlertDialog = dialogBuilder.create();
        mAlertDialog.show();*/
    }

    private void launchNewChat(PhlockUser recipient){
        final String sender_uid = mFirebaseAuth.getCurrentUser().getUid();
        final String sender_name = mFirebaseAuth.getCurrentUser().getDisplayName();
        final String recipient_uid = recipient.getUid();
        final String recipient_name = recipient.getName();

        //We are moving all the commented code below to the new ChatScreenActivity
        /*//mChatKey = saveChatToDatabase(sender_uid, recipient_uid);
        String newChatKey=mChatDatabaseReference.push().getKey();
        saveChatToDatabase(sender_uid, recipient_uid, newChatKey);*/
        myLogPrint(String.format("After saveChatToDatabase(), mChatKey is: %s",mChatKey));


        Intent intent = new Intent(this,ChatScreenActivity.class)
                                .putExtra("sender_uid",sender_uid)//key value pair
                                .putExtra("recipient_uid",recipient_uid)
                                .putExtra("sender_name",sender_name)
                                .putExtra("recipient_name",recipient_name);
        startActivity(intent);

    }


    public void showShareKeyPopup (){

        //Only run this the first time popupmenu is created
        if(shareKeyPopupMenu==null) {
            //Initialize shareKeyPopupMenu Menu
            shareKeyPopupMenu = new PopupMenu(this, findViewById(R.id.fab_add_chat));
            MenuInflater inflater = shareKeyPopupMenu.getMenuInflater();
            /**
             * See {@link #onMenuItemClick(MenuItem)}
             */
            inflater.inflate(R.menu.chat_screen_sharekeys_menu, shareKeyPopupMenu.getMenu());
            shareKeyPopupMenu.setOnMenuItemClickListener(this);
            onPrepareOptionsMenu(shareKeyPopupMenu.getMenu());
            shareKeyPopupMenu.show();
        }
        else{
            shareKeyPopupMenu.show();
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {

        switch (item.getItemId()){

            case R.id.new_key:
                getPhlockKeyActivity();

            case R.id.new_chat:
                showCreateChatDialog();

            default:
                return true;
        }
    }

    private void getPhlockKeyActivity() {

        Intent intent = new Intent(this,PhlockListActivity.class)
                                .putExtra("intent_mode","CreateNewKey");//key value pair
        startActivity(intent);
    }


    /**
     *@see <a href="https://firebase.google.com/docs/database/android/read-and-write#update_specific_fields">
     *     Firebase Updating Fields Doc</a> This link is a useful reference.
     * @param userQuery
     * @return mChatKey
     *
     */
    private List<PhlockUser> SearchFirebaseForPhlockUsers(String userQuery) {
        final List<PhlockUser> phlockUsers = new ArrayList<PhlockUser>();
        mUsersDatabaseReference.orderByChild("email").equalTo(userQuery).addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot d: dataSnapshot.getChildren()) {
                    myLogPrint("The Key is: " + d.getKey());
                    PhlockUser pu = d.getValue(PhlockUser.class);
                    myLogPrint("After d.getValue(PhlockUser.class), before phlockUsers.add");
                    myLogPrint("d.getValue().toString(): " + d.getValue().toString());
                    phlockUsers.add(pu);
                    myLogPrint("The Phlock User Object is: "+pu.toString());
                    myLogPrint("Quadruple check date: " +pu.getDateCreated());
                    myLogPrint("Triple check date: " +pu.getDateCreatedString());

                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }


        });
        return phlockUsers;

    }

    /**
     * Very similiar to {@link #SearchFirebaseForPhlockUsers(String)}. Todo Combine the two methods, make it more reusable.
     * Should I use a factory pattern of class, PhlockObject with User and chats property or is this still good practice.
     * @param userID
     * @return
     */
    private List<PhlockUser> SearchFirebaseForPhlockChats(String userID) {
        final List<PhlockUser> phlockUsers = new ArrayList<PhlockUser>();
        mUsersDatabaseReference.equalTo(userID).orderByChild("chats").addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot d: dataSnapshot.getChildren()) {
                    myLogPrint("The Key is: " + d.getKey());
                    PhlockUser pu = d.getValue(PhlockUser.class);
                    myLogPrint("After d.getValue(PhlockUser.class), before phlockUsers.add");
                    phlockUsers.add(pu);
                    myLogPrint("The Phlock User Object is: "+pu.toString());
                    myLogPrint("Quadruple check date: " +pu.getDateCreated());
                    myLogPrint("Triple check date: " +pu.getDateCreatedString());

                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }


        });
        return phlockUsers;

    }


    /**
     * Helpher method. That queries the children of a given snapshot for kes.
     * @param dataSnapshot
     * @param mKeysDatabaseReference
     * @param sender_keys_map
     * @param sender_phlock_keys
     */
    protected  void queryChildrenForKeys(DataSnapshot dataSnapshot, DatabaseReference mKeysDatabaseReference, final Set<String> sender_keys_map, final List<PhlockKey> sender_phlock_keys) {

        sender_phlock_keys.clear();
        for (final DataSnapshot d : dataSnapshot.getChildren()) {
            Query query = mKeysDatabaseReference.child(d.getKey());
            query.addListenerForSingleValueEvent(new ValueEventListener() {

                @Override
                public void onDataChange(DataSnapshot innerD) {
                    myLogPrint(LOG_TAG, "EACH of the sender's keys are(d.getKey()): " + innerD.getKey());
                    myLogPrint(LOG_TAG, "EACH of the sender's keys are(d.getValue().toString()): "
                                                + innerD.getValue().toString());
                    PhlockKey k = innerD.getValue(PhlockKey.class);
                    sender_phlock_keys.add(k);
                    sender_keys_map.add(k.getKey_id());
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });

        }
    }

    static public void myLogPrint(String s){
        Log.d(LOG_TAG,s);
    }

    static public void myLogPrint(String t,String s){
        Log.d(t,s);
    }

    public static void myLogPrintLN(String s) {
        Log.d(LOG_TAG+Thread.currentThread().getStackTrace()[1].getLineNumber(),s);
    }
    public static void myLogPrintLN(String t,String s) {
        Log.d(t+Thread.currentThread().getStackTrace()[1].getLineNumber(),s);
    }

    public class ChatPreview{
        String chatName;
        String chatMessage;
        PhlockChat phlockChat;
        PhlockUser chatRecipient;
    }
}

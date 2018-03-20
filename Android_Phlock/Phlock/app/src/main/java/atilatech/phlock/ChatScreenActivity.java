/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.firebase.udacity.friendlychat.ChatListActivity.myLogPrint;
import static com.google.firebase.udacity.friendlychat.ChatListActivity.myLogPrintLN;

public class ChatScreenActivity extends AppCompatActivity implements PopupMenu.OnMenuItemClickListener {

    private static final String TAG = "ChatScreenActivityDEBUG";
    private static final String LOG_TAG = "ChatScreenActivityDEBUG";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final String MSG_LENGTH_KEY = "msg_length";
    public static final int RC_SIGN_IN = 1;
    private static final int RC_PHOTO_PICKER =  2;
    public static final String GOOGLE_CONTACTS_API = "https://www.googleapis.com/auth/contacts.readonly";


    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private ImageButton mSendKeyButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;
    private String mChatKey;
    private String mSharingKey;
    private String sender_uid;
    private String recipient_uid;
    private String sender_name;
    private String recipient_name;
    private boolean isExistingChat;
    List<String> sender_chats_id = new ArrayList<>();
    List<PhlockKey> sender_phlock_keys = new ArrayList<>();
    Set<String> sender_keys_map = new HashSet<>();
    PopupMenu shareKeyPopupMenu;

    ///Map the Resource KeyID to the FirebaseID
    //Uses BiMap from guava so we can go in use the value to get a key using inverse()
    BiMap<Integer, String> senderKeyIdMap = HashBiMap.create();

    //Track to see if the key has already been shared with the recipient
    Map<String, Boolean> alreadySharedKeyMap = new HashMap<>();

    //Firebase instance variables
    private FirebaseDatabase mFirebaseDatabse;
    private DatabaseReference mChatDatabaseReference;
    private DatabaseReference mMessagesDatabaseReference;
    private DatabaseReference mUsersDatabaseReference;
    private DatabaseReference mKeysDatabaseReference;
    private ChildEventListener mChildEventListener;
    //mValueEventListener listens for when the data in a child changes,
    // will work in conjuction with mChildEventListener
    private ValueEventListener mValueEventListener;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mChatPhotosStorageReference;
    private FirebaseRemoteConfig mFirebaseRemoteConfig;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_chat_screen);

        mUsername = ANONYMOUS;

        //Initialize Firebase components
        mFirebaseDatabse = FirebaseDatabase.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseStorage = FirebaseStorage.getInstance();
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        mChatDatabaseReference = mFirebaseDatabse.getReference().child("chats");
        mMessagesDatabaseReference = mFirebaseDatabse.getReference().child("messages");
        mUsersDatabaseReference = mFirebaseDatabse.getReference().child("users");
        mKeysDatabaseReference= mFirebaseDatabse.getReference().child("keys");
        mChatPhotosStorageReference = mFirebaseStorage.getReference().child("chat_photos");


        //check onStart() for code that used to be here

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mSendKeyButton = (ImageButton) findViewById(R.id.sendKeybutton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<PhlockMessage> phlockMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, phlockMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        //Setup Back button to go to HomeScreen
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // SendKeyButton sends the digital key to the user,
        // Will likely leverage the sendButton by calling sendButton's onClick function
        mSendKeyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Either generate a new key or send the current active key
                // TODO: HARDWARE:make the key dynamic and time-sensitive
                // TODO: Fire an intent to send the key

                //// TODO: 2017-03-29 Some debugging functionality for now.
                showShareKeyPopup ();

            }
        });

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click
                PhlockMessage phlockMessage =
                        new PhlockMessage(mMessageEditText.getText().toString(), mUsername, null, mChatKey);
                String newMessageKey = mMessagesDatabaseReference.push().getKey();

                ///Use data-fanout to update both the chats and message reference
                Map<String, Object> childUpdates = new HashMap<>();
                childUpdates.put("/messages/" + newMessageKey, phlockMessage);
                childUpdates.put("/chats/" + mChatKey + "/messages/" + newMessageKey, true);

                mFirebaseDatabse.getReference().updateChildren(childUpdates);
                // Clear input box
                mMessageEditText.setText("");
            }
        });



        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user!=null){
                    //user is signed in
                   onSignedInInitialize(user.getDisplayName());
                    /*if(mChatKey==null){
                        //There is no active chat so go back to chat list activity
                        NavUtils.navigateUpFromSameTask(ChatScreenActivity.this);
                    }*/
                }else {
                    //If user is not authenticated we should not be signing in here
                    // Instead, take them back to ChatList

                    //TODO: Add facebook as a sign in provider
                    //user is signed out
                    onSignedOutCleanup();
                    NavUtils.navigateUpFromSameTask(ChatScreenActivity.this);
                    //TODO: IF I move it to end of OnCreate, will that be the right place?
                    /*if(mChatKey==null){
                        //There is no active chat so go back to chat list activity
                        NavUtils.navigateUpFromSameTask(ChatScreenActivity.this);
                    }*/

                    //TODO: Add permissions request for contacts (it is already in manifest)
                    //Requesting additional scopes from the user

                    /*AuthUI.IdpConfig googleIdp = new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER)
                                                         .setPermissions(Arrays.asList(GOOGLE_CONTACTS_API))
                                                         .build();
                    AuthUI.IdpConfig facebookIdp = new AuthUI.IdpConfig.Builder(AuthUI.FACEBOOK_PROVIDER)
                                                           .setPermissions(Arrays.asList("user_friends"))
                                                           .build();
                    *//*startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setProviders(Arrays.asList(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                                            googleIdp,
                                            facebookIdp))
                                    .build(),
                            RC_SIGN_IN);*/
                }
            }
        };

        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
              .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build();
        mFirebaseRemoteConfig.setConfigSettings(configSettings);

        Map<String,Object> defaultConfigMap = new HashMap<>();
        defaultConfigMap.put(MSG_LENGTH_KEY,DEFAULT_MSG_LENGTH_LIMIT);
        mFirebaseRemoteConfig.setDefaults(defaultConfigMap);
        fetchConfig();

    }

    @Override
    protected void onStart(){
        super.onStart();

        Intent intent = this.getIntent();
        if (intent != null && intent.hasExtra("sender_uid") && intent.hasExtra("recipient_uid")) {
            sender_uid = intent.getStringExtra("sender_uid");//get chat key selected from ChatList
            recipient_uid = intent.getStringExtra("recipient_uid");
            sender_name = intent.getStringExtra("sender_name");
            recipient_name = intent.getStringExtra("recipient_name");
        }
        String newChatKey=mChatDatabaseReference.push().getKey();
        saveChatToDatabase(sender_uid, recipient_uid, newChatKey);
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
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
        }else if(requestCode==RC_PHOTO_PICKER && resultCode==RESULT_OK){
                Uri selectedImageUri= data.getData();
                StorageReference photoRef = mChatPhotosStorageReference
                                                    .child(selectedImageUri.getLastPathSegment());
                //if the file uri was content://local_images/foo/4 then the file name for the child would be 4
                photoRef.putFile(selectedImageUri).addOnSuccessListener
                   (this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                       @Override
                       public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                           @SuppressWarnings("VisibleForTests") Uri downloadUrl = taskSnapshot.getDownloadUrl();
                           PhlockMessage phlockMessage =
                                   new PhlockMessage(null, mUsername, downloadUrl.toString(),mChatKey);

                           String newMessageKey = mMessagesDatabaseReference.push().getKey();
                           Log.d(ChatScreenActivity.class.toString(),String.format("the Image url is: %s",downloadUrl.toString()));

                           ///Use data-fanout to update both the chats and message reference
                           Map<String, Object> childUpdates = new HashMap<>();
                           childUpdates.put("/messages/" + newMessageKey, phlockMessage);
                           childUpdates.put("/chats/" + mChatKey + "/messages/" + newMessageKey, true);

                           mFirebaseDatabse.getReference().updateChildren(childUpdates);
                       }
                   });
            }
        }

    private void saveNewUserToDatabase() {
        final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        final String username = user.getDisplayName();
        final String uid = user.getUid();
        final String email = user.getEmail();

        mUsersDatabaseReference.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // TODO: handle the case where the data already exists, add some useful info
                    Log.d(LOG_TAG,"The node already exists: "+mUsersDatabaseReference.child(uid).toString());
                    return;
                }
                else {
                    // TODO: handle the case where the data does not yet exist
                    PhlockUser phlockUser =
                            new PhlockUser(username,uid,email);
                    mUsersDatabaseReference.push().setValue(phlockUser);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Moved the code here to onStop()
    }

    @Override
    protected void onStop(){
        super.onStop();
        if(mAuthStateListener!=null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        detachDatabaseReadListner();
        mMessageAdapter.clear();

    }

    private void onSignedInInitialize(String username){
        mUsername = username;
    //  attachDatabaseReadListener();

    }

    //This is called each time, a new message (child of root in the Firebase database) is created
    //Assumes that every time a child is added, it will be a message.
    //Generally will be true most of the time, beware of edge cases.
    private void attachDatabaseReadListener() {
        if(mChildEventListener==null) {
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {

                    //deserializes into a plain PhlockMessage Object
                    PhlockMessage phlockMessage = dataSnapshot.getValue(PhlockMessage.class);


                    if(phlockMessage.getChat()!=null && phlockMessage.getChat().equals(mChatKey)) {
                        myLogPrint(LOG_TAG,"The attachDatabaseReadListener(), dsnapshot is "
                                                   +dataSnapshot.getValue().toString());
                        //Only add it to the display if the message belongs to the chat
                        myLogPrint(LOG_TAG,"The phlock Message?, phlockMessage is "+phlockMessage.toString());
                        myLogPrint(LOG_TAG,"The mChatKey is "+mChatKey);
                        mMessageAdapter.add(phlockMessage);
                    }
                }


                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            };

            mMessagesDatabaseReference.addChildEventListener(mChildEventListener);
        }

        //The mValueEventListener is for when the 'data' in a new child changes.
        //So if we send code to the database and server-side code changes it,
        //that will call the onDataChange() method
        if(mValueEventListener==null){
            mValueEventListener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    PhlockMessage phlockMessage = dataSnapshot.getValue(PhlockMessage.class);
                    /*TODO:This will cause the message to appear twice, we need to remove the first message
                    from the MessageAdapter and only show the updated message. May have to use the remove
                    method mMessageAdapter.remove() but we may have to customize its functionality.
                     */
                    //Only display the message if it belongs to the chat
                    if(phlockMessage.getChat()==mChatKey) {
                        mMessageAdapter.add(phlockMessage);
                        Log.d(TAG+Thread.currentThread().getStackTrace()[2].getLineNumber(), phlockMessage.phlockMessagePrint());
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            };

            mMessagesDatabaseReference.addValueEventListener(mValueEventListener);

        }
    }

    private void onSignedOutCleanup(){
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        detachDatabaseReadListner();
    }

    private void detachDatabaseReadListner(){
        if(mChildEventListener!=null) {
            mMessagesDatabaseReference.removeEventListener(mChildEventListener);
            mMessagesDatabaseReference.removeEventListener(mValueEventListener);
            mChildEventListener = null;
            mValueEventListener = null;
        }
    }

    //TODO: How can we make this method more modular
    private void saveChatToDatabase(final String sender_uid, final String recipient_uid, final String newChatKey) {
        //Search to see if they have a pre-existing chat,
        //If they do. Open it in the next activity, if they do not, create a new chat and then open the new activity

        mUsersDatabaseReference.child(sender_uid).child("chats").addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                if(dataSnapshot.exists()){

                    myLogPrint(LOG_TAG, "These are the sender's chats(dataSnapshot.toString()): "+  dataSnapshot.toString());
                    myLogPrint(LOG_TAG, "These are the sender's chats: "+  dataSnapshot.getValue().toString());
                    myLogPrint(LOG_TAG, "The Recipients chat key(s?): "+  dataSnapshot.getKey());

                    //The sender may have multiple chats, so we need to see which of the chats it shares with recipient
                    //so For each (chat d: that is one of sender_chats.getChildren)
                    for (final DataSnapshot d: dataSnapshot.getChildren()) {
                        sender_chats_id.add(d.getKey());
                        myLogPrint(LOG_TAG, "EACH of the sender's chats are(d.getKey()): "+  d.getKey());
                        myLogPrint(LOG_TAG, "EACH of the sender's chats are(d.getValue().toString()): "+  d.getValue().toString());

                    }
                }
                myLogPrint(LOG_TAG, "About to Query the following chats in the for loop: " + sender_chats_id);

                for(final String chat_id: sender_chats_id) {
                    searchChatsForExisting(chat_id, recipient_uid, newChatKey, sender_uid);
            }

                if(!isExistingChat){
                    createNewChat(sender_uid, recipient_uid, newChatKey);
                }

        }
            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        //TODO: Is this good coding practice?

    });

    myLogPrint(LOG_TAG, String
    .format("Chat Key just before the return: %s "
            , mChatKey));

    myLogPrint(LOG_TAG, "Is Existing Chat: "+ isExistingChat);

    }

    /**
     * Helper method for {@link #saveChatToDatabase(String, String, String) saveChatToDatabase()}.
     * Creates a new chat between sender_uid and recipient_uid.
     * @param sender_uid The id of the chat sender.
     * @param recipient_uid The id of the chat recipient.
     * @param newChatKey The id (key) of the newly created chat.
     */
    private void createNewChat(String sender_uid, String recipient_uid, String newChatKey) {
        //Create a new chat
        PhlockChat phlockChat = new PhlockChat(sender_uid,recipient_uid);
        //mChatKey = mChatDatabaseReference.push().getKey();
        myLogPrint(LOG_TAG, String
                                    .format("Just created a chat(%s) with. Sender: %s and Recipient: %s "
                                            ,newChatKey,sender_uid,recipient_uid));

        mChatDatabaseReference.child(newChatKey).setValue(phlockChat);

        //Also update the chats of each of the phlock users.
        Map<String, Object> childUpdates = new HashMap<>();
        HashMap<String, Boolean> mChatKeyMap = new HashMap<String, Boolean>();
        mChatKeyMap.put(newChatKey,true);//I don't think I actually do anything with this lol
        childUpdates.put("/users/" + sender_uid+"/chats/"+newChatKey, true);
        childUpdates.put("/users/" + recipient_uid+"/chats/"+newChatKey, true);

        mFirebaseDatabse.getReference().updateChildren(childUpdates);
        mChatKey = newChatKey;
        attachDatabaseReadListener();
    }

    /**
     * Helper method for {@link #saveChatToDatabase(String, String, String) saveChatToDatabase()}.
     * Searches the list of existing chats looking to see if the two users have a match.
     * @param chat_id
     * @param recipient_uid
     * @param newChatKey
     * @param sender_uid
     */
    private void searchChatsForExisting(final String chat_id, final String recipient_uid, final String newChatKey, final String sender_uid) {
        myLogPrint(LOG_TAG, "The First chat to query in the for loop: " + chat_id);

        Query query = mChatDatabaseReference.child(chat_id).child("users").child(recipient_uid);
        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot innerDataSnapshot) {
                if (innerDataSnapshot.exists()) {
                    myLogPrint(LOG_TAG, "The Sender and recipient already have a chat: " + innerDataSnapshot.getValue().toString());
                    myLogPrint(LOG_TAG, "innerDataSnapshot.getKey();: " + innerDataSnapshot.getKey());
                    mChatKey = mChatDatabaseReference.child(chat_id).getKey();
                    myLogPrintLN(LOG_TAG, "mChatKey: " + mChatKey);

                    isExistingChat = true;

                    //TODO: If existing chat exists, add the messages to the chatadapter
                    //delete the new chat I just made
                    //Note, whenever you make a change to chat you must use data fan-out
                    //How is this different from setting to null
                    mChatDatabaseReference.child(newChatKey).removeValue();
                    //Also update the chats of each of the phlock users.
                    Map<String, Object> childUpdates = new HashMap<>();
                    childUpdates.put("/users/" + sender_uid + "/chats/" + newChatKey, null);
                    childUpdates.put("/users/" + recipient_uid + "/chats/" + newChatKey, null);

                    mFirebaseDatabse.getReference().updateChildren(childUpdates);
                    attachDatabaseReadListener();
                }

            }


            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public void fetchConfig() {
        long cacheExpiration = 3600;

        if(mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()){
            cacheExpiration = 0;
        }
        mFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener(new OnSuccessListener<Void>(){
                    @Override
                    public void onSuccess(Void aVoid){
                        mFirebaseRemoteConfig.activateFetched();
                        applyRetrievedLengthLimit();
                    }
                })
                .addOnFailureListener(new OnFailureListener(){
                    @Override
                    public void onFailure(@NonNull Exception e){
                        Log.w(TAG, "Error fetching config", e);
                        applyRetrievedLengthLimit();
                    }
                });
    }

    /**
     * If the message length remote config was succesfully retrieved. Apply it to the settings.
     */
    private void applyRetrievedLengthLimit(){
        Long msg_length = mFirebaseRemoteConfig.getLong(MSG_LENGTH_KEY);
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(msg_length.intValue())});
        Log.d(TAG, MSG_LENGTH_KEY + " = " + msg_length);
    }

    /**
     * THis is a helper method to create the initial key. It will only run once, then it will be manually commented out.
     * Generate a random key_code and save it to database and local storage
     */
    private void createPhlockKey() {

        mKeysDatabaseReference.addListenerForSingleValueEvent(new ValueEventListener() {


            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                 mSharingKey = mKeysDatabaseReference.push().getKey();
                //see mSendKey onClickListener for the idea behind this line
                ///Use data-fanout to update both the chats and message reference
                String key_code = generatePhlockKeyCode();
                String timeStamp = new SimpleDateFormat("MMM d, h:mm a").format(new Date());

                PhlockKey pk = new PhlockKey(mSharingKey,String.format("%s's Key: %s",sender_name,timeStamp),sender_uid,key_code);
                myLogPrint("Just generated a key code: "+ key_code);
                Map<String, Object> childUpdates = new HashMap<>();
                childUpdates.put("/keys/" + mSharingKey, pk);
                //// TODO: 2017-04-08 Instead of having a List<string> owned_by; we add PhlockKey owners through pushing. What are the implications?
                childUpdates.put("/users/" + sender_uid + "/keys_owned/" + mSharingKey, true);

                mFirebaseDatabse.getReference().updateChildren(childUpdates);

                myLogPrint("About to write to internal: Line 667");

                //Now we save the key to internal storage
                //The filename is the key_id
                writeKeyToInternalStorage(mSharingKey, key_code);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    protected void writeKeyToInternalStorage(String fileName, String fileContent) {
        FileOutputStream outputStream;


        try {
            myLogPrint("Inside Trying the write to internal: Line 676");
            outputStream = openFileOutput(fileName, Context.MODE_PRIVATE);
            outputStream.write(fileContent.getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        readKeyFromInternalStorage(fileContent);


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

    //// TODO: 2017-04-21 This code is repeated word for word in ChatListActivity. How can I avoid repetition?
    public void showShareKeyPopup (){

        //Only run this the first time popupmenu is created
        if(shareKeyPopupMenu==null) {
            //Initialize shareKeyPopupMenu Menu
            shareKeyPopupMenu = new PopupMenu(this, findViewById(R.id.sendKeybutton));
            MenuInflater inflater = shareKeyPopupMenu.getMenuInflater();
            // This activity implements OnMenuItemClickListener
            inflater.inflate(R.menu.chat_screen_sharekeys_menu, shareKeyPopupMenu.getMenu());
            shareKeyPopupMenu.setOnMenuItemClickListener(this);
            onPrepareOptionsMenu(shareKeyPopupMenu.getMenu());
            shareKeyPopupMenu.show();
        }
        else{
            onPrepareOptionsMenu(shareKeyPopupMenu.getMenu());
            shareKeyPopupMenu.show();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu){
        Boolean isShared = false;//Has this key already been shared with the recipient
        List<PhlockKey> sender_keys = getListOfKeys();
        int i=0; //For incrementing the view ID

        for (PhlockKey k : sender_keys) {
            //For each key, get the list of people that are sharing this key and
            //See if the recipient is one of the people sharing this key.
            if(k.getShared_with()!=null) {

                for (String shared_with_id : k.getShared_with().keySet()) {

                    if (shared_with_id.equals(recipient_uid)) {
                        isShared = true;
                        Date date=new Date(k.getDate_created());
                        String timeStamp = new SimpleDateFormat("MMM d, h:mm a").format(date);
                        String keyName = timeStamp + "|"+k.getKeyname();

                        if(!senderKeyIdMap.containsValue(k.getKey_id())){
                            senderKeyIdMap.put(Menu.FIRST + i, k.getKey_id());
                            shareKeyPopupMenu.getMenu().add(Menu.CATEGORY_SECONDARY, Menu.FIRST + i, Menu.NONE, keyName).setIcon(R.drawable.ic_remove_key_circle);
                        }
                        else{
                            int menuID = senderKeyIdMap.inverse().get(k.getKey_id());
                            shareKeyPopupMenu.getMenu().getItem(menuID).setIcon(R.drawable.ic_remove_key_circle);
                        }
                        alreadySharedKeyMap.put(k.getKey_id(), true);

                        break;
                    }
                }
            }
            if(!isShared){
                Date date=new Date(k.getDate_created());
                String timeStamp = new SimpleDateFormat("MMM d, h:mm a").format(date);
                String keyName = timeStamp + "|"+k.getKeyname();

                //If the key(ID) is NOT already in the Map then we can add it to the menu
                //ELSE don't do anything
                if(!senderKeyIdMap.containsValue(k.getKey_id())){
                    senderKeyIdMap.put(Menu.FIRST+i,k.getKey_id());
                    shareKeyPopupMenu.getMenu().add(Menu.CATEGORY_SECONDARY,
                        Menu.FIRST+i,Menu.NONE,keyName).setIcon(R.drawable.ic_add_key_circle);
                }

                else{
                    int menuID = senderKeyIdMap.inverse().get(k.getKey_id());
                    shareKeyPopupMenu.getMenu().getItem(menuID).setIcon(R.drawable.ic_add_key_circle);
                }
                alreadySharedKeyMap.put(k.getKey_id(),false);
            }
            isShared=false;
            i++;
        }
        return true;
    }

    private List<PhlockKey> getListOfKeys() {

        mUsersDatabaseReference.child(sender_uid).child("keys_owned")
                .addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {

                    myLogPrint(LOG_TAG, "These are the sender's keys(dataSnapshot.toString()): " + dataSnapshot.toString());
                    myLogPrint(LOG_TAG, "These are the sender's keys: " + dataSnapshot.getValue().toString());
                    myLogPrint(LOG_TAG, "The Recipients key id(s?): " + dataSnapshot.getKey());
                    //The sender may have multiple chats, so we need to see which of the chats it shares with recipient
                    //so For each (chat d: that is one of sender_chats.getChildren)
                    queryChildrenForKeys(dataSnapshot, mKeysDatabaseReference, sender_keys_map, sender_phlock_keys);
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
        return sender_phlock_keys;
    }

    /**
     * Helpher method. That queries the children of a given snapshot for kes.
     * @param dataSnapshot
     * @param mKeysDatabaseReference
     * @param sender_keys_map
     * @param sender_phlock_keys
     */
    protected static void queryChildrenForKeys(DataSnapshot dataSnapshot, DatabaseReference mKeysDatabaseReference, final Set<String> sender_keys_map, final List<PhlockKey> sender_phlock_keys) {

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

                    myLogPrint(LOG_TAG, "k.getDate_created(): " + k.getDate_created());
                    sender_phlock_keys.add(k);
                    sender_keys_map.add(k.getKey_id());
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });

        }
    }


    private void shareKey( String sharing_key, Boolean isSharedAlready) {

        //When we first populated the sender's list of key's we already checked for already sent keys
        //Now we can just use data-fanout strings and update children to update the database

        if(isSharedAlready==null || !isSharedAlready){ //the key is not shared already
            Map<String, Object> childUpdates = new HashMap<>();
            childUpdates.put("/keys/" + sharing_key + "/shared_with/" + recipient_uid, true);
            childUpdates.put("/users/" + recipient_uid + "/keys_shared/" + sharing_key, false);//false is for isRegistered

            alreadySharedKeyMap.put(sharing_key,true);
            mFirebaseDatabse.getReference().updateChildren(childUpdates);
            //// TODO: 2017-03-29 Some debugging functionality for now.
            //// TODO: 2017-05-20 What is the value in showing the key image? 
            String messageText = String.format("%s has shared a key with %s.",sender_name,recipient_name);
            /*String keyPhotoUrl= "https://firebasestorage.googleapis.com/v0/b/phlock-1d1fb.appspot.com/o/" +
                                        "chat_photos%2F1691725719?alt=media" +
                                        "&token=a778bda9-bdc2-4941-a448-1d3248a740d2";*/
            sendPhlockMessage(messageText, null);
        }

        else if(isSharedAlready){
            removeKey(sharing_key);
            return;
        }
        if(sharing_key == null){
            String shareKeyStr = String.format("Error. %s does not have a key to share.",mUsername);
            Toast.makeText(this, shareKeyStr, Toast.LENGTH_SHORT).show();
            return;
        }


    }

    private void sendPhlockMessage(String message, String photoUrl) {

        Map<String, Object> childUpdates = new HashMap<>();

        PhlockMessage phlockMessage =
                new PhlockMessage(message, mUsername, photoUrl, mChatKey);
        String newMessageKey = mMessagesDatabaseReference.push().getKey();

        ///Use data-fanout to update both the chats and message reference
        childUpdates.put("/messages/" + newMessageKey, phlockMessage);
        childUpdates.put("/chats/" + mChatKey + "/messages/" + newMessageKey, true);


        mFirebaseDatabse.getReference().updateChildren(childUpdates);
        // Clear input box
        mMessageEditText.setText("");
    }

    private void removeKey(String sharing_key) {
        mKeysDatabaseReference.child(sharing_key).child("shared_with").child(recipient_uid).removeValue();

        Map<String, Object> childUpdates = new HashMap<>();
        childUpdates.put("/users/" + recipient_uid + "/keys_shared/" + sharing_key, null);
        mFirebaseDatabse.getReference().updateChildren(childUpdates);

        //Update the map which tracks shared keys
        alreadySharedKeyMap.put(sharing_key,false);

        String str = String.format("%s has removed a key from %s.",sender_name,recipient_name);
        sendPhlockMessage(str,null);


    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getGroupId()==Menu.CATEGORY_SECONDARY) {//A share key item was selected
            String selected_key_id = senderKeyIdMap.get(item.getItemId());

            //calls sharekey() method and lets the method know if the key has already been shared
            shareKey(selected_key_id, alreadySharedKeyMap.get(selected_key_id));
            return true;
        }
        switch (item.getItemId()){

            case R.id.new_key:
                //Generate a new PhlockKey
                createPhlockKey();

            default:
                return true;
        }
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

            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);

           default:
               return super.onOptionsItemSelected(item);
        }
    }

    protected static String generatePhlockKeyCode(){
        SecureRandom random = new SecureRandom();

        String nextSessionId = new BigInteger(130, random).toString(32);
        nextSessionId = nextSessionId.substring(0,8);
        ChatListActivity.myLogPrintLN("Generated KeyCode: ",nextSessionId);
        return nextSessionId;
    }
}

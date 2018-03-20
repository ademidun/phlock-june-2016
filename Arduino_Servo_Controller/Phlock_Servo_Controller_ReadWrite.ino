// Arduino Bluetooth LE Servo Controlled by iOS
#include <Servo.h>
#include <SoftwareSerial.h>
#include <EEPROM.h>
#define COMMON_ANODE
#define COMMON_ANODE
int lock = 8;          //pin 9 on Arduino
int LED_red = 6;
int LED_green = 5;
int LED_blue = 3;
String debug_msg = "incoming_byte Number ";




char user_input[18];         //Characters the Arduino will receive: admin_mode=8,seperator'_'=1.keycode=8, +1 for null terminator
char user_input_mode[9];  //keycode=8, +1 for null terminator
//strings must be terminated with a null character


//todo: Consider using an array of strings (or char arrays) to represent the different modes
char correct[] = {‘1’,’2’,’3’,’4’,’5’,’5’,’6’,’7’,’\0’};    //User-Defined Password
char admin_mode[] = {‘p’,’a’,’s’,’s’,’w’,’o’,’r’,’d’,’\0’};
char unlock_mode[] = {‘l’,’o’,’c’,’k’,’1’,’4’,’5’,’6’,’\0’};
char lock_mode[] = {‘l’,’o’,’c’,’k’,’8’,’9’,’a’,’b’,’\0’};
int pass_mode = 0;          //What mode are we in, 0=wrong key 1=correct key, 4=admin_key,5=manual unlock, 6 = lock
char incoming_byte;
int admin_tracker=0;
int eeAddress = 0;   //Location we want the data to be put.

boolean locked = true;
Servo myservo;
//SoftwareSerial(rxpin, txpin)
int BT_tx = 10; //HC-05 module tx is connected to Arduino pin 10
int BT_rx = 9;
int pwd_index =0;
SoftwareSerial BLE_Shield(BT_tx, BT_rx);//SPP is inverted, bluetooth rxd is arduino txd

void setup(){
  BLE_Shield.begin(9600);
  Serial.begin(9600);
  myservo.write(15);//15 = lock position


  pinMode(LED_red, OUTPUT);
  pinMode(LED_green, OUTPUT);
  pinMode(LED_blue, OUTPUT);
  
  eeAddress=0;
  for(int i=9;i<17;i++){
    //Read this device's memory
    correct[i-9]=EEPROM.read(eeAddress);
    Serial.print(F("Just Read from memory location "));
    Serial.print(eeAddress);
    Serial.print(F(": "));
    Serial.println(correct[i-9]);//Just read from memory location {eeAddress}: {correct[i-9]}
    eeAddress += 4;

  }
  delay(500);
  myservo.detach();

}

void setColor (int red=0, int green=0, int blue=0){
  
  /*#ifdef COMMON_ANODE
  red = 255 - red;
  green = 255 - green;
  blue = 255 - blue;
  #endif*/


  analogWrite(LED_red, red);
  analogWrite(LED_green, green);
  analogWrite(LED_blue, blue);
}

void loop()
{

  
while (BLE_Shield.available()) 
{
  incoming_byte=BLE_Shield.read();//read the value from the user
    //Serial.write(incoming_byte);'
   
    debug_msg= "";
    user_input[pwd_index]=incoming_byte;
    debug_msg =  debug_msg+"incoming_byte Number "  + (pwd_index+1)+ ": "+ incoming_byte;
    Serial.println(debug_msg);

    //first we must see what mode we are in
    if(pwd_index < 8){
        user_input_mode[pwd_index] = incoming_byte; debug_msg= "";
        debug_msg = debug_msg + "(pwd_index < 8)userInput " + (pwd_index+1)+ ": " + user_input_mode[pwd_index] + ":\n ";
      Serial.println(debug_msg);
    }

  pwd_index++;
  
}//endwhile 

//now we use string to process what mode we are in 
//todo: is the use of String(char[]) trading faster run time for less memory? Is this good practice?
//todo: make it a switch statement?

/*if(String(user_input_mode)==String(unlock_mode)){
  pass_mode=1;

}
*/
if(String(user_input_mode)==String(unlock_mode)){
  pass_mode=5;
}
else if(String(user_input_mode)==String(admin_mode)){
  pass_mode=3;

}
else if(String(user_input_mode)==String(lock_mode)){
  pass_mode=6;
}
else {
    pass_mode=0;
}

if(pass_mode>0){
Serial.println("String Operator Test\nuser_input_mode code:" + String(user_input_mode));
Serial.println("pass_mode Test2 :" + String(pass_mode)); 
}





if(pass_mode==1 || pass_mode==5){  //If all chars compared match, deactivate(unlock) the lock for 5 seconds

  for(int i=9;i<17;i++){
    if (correct[i-9]==user_input[i]){
         debug_msg= "";
           debug_msg = "Passed Test" + (i-8);
           debug_msg = debug_msg +  ": ";
           debug_msg = debug_msg + "user input: ";
           debug_msg = debug_msg + user_input[i];
           debug_msg = debug_msg  + ", correct input: ";
           debug_msg = debug_msg  + correct[i-9];
           Serial.println(debug_msg);
    }
    else{
      debug_msg= "";
           debug_msg = "Failed Test" + (i-8);
           debug_msg = debug_msg +  ": ";
           debug_msg = debug_msg + "user input: ";
           debug_msg = debug_msg + user_input[i];
           debug_msg = debug_msg  + ", correct input: ";
           debug_msg = debug_msg  + correct[i-9];
           Serial.println(debug_msg);
      pass_mode=2;break;
    }
  }

  if(pass_mode==1){

      Serial.println("Correct Password");
      unlockServo();
      Serial.println("UnLocked-by-BLE");
      myservo.detach();
      pass_mode = 0;  
  }
    if(pass_mode==5){
      Serial.println("Correct Password\npass_mode=5 ");
      unlockServo();
      Serial.println("UnLocked-by-BLE");
      myservo.detach();
      pass_mode = 0;  
  }
}


//see if we are in admin mode
/*else if(pass_mode==3)
{
  for (;admin_tracker<7;admin_tracker++){
    if (admin_mode[admin_tracker]==user_input[admin_tracker]){
        pass_mode=4;
        Serial.print("adminMode: ");
        Serial.println(pass_mode);
          debug_msg = "Passed Test" + (i-8) + ": ";
          debug_msg = debug_msg + "user input: " + user_input[i] + ", correct input: " + correct[i-9];
          Serial.println(debug_msg);
    }
    else{
      pass_mode=0;
      break;
    }
  }
}*/

else if (pass_mode==2)
{
  //setColor(0, 64, 64); // aqua
  delay(2500);
  setColor();
  Serial.println(F("Incorrect-Password"));
  myservo.detach();
  pass_mode = 0; 
}

else if (pass_mode==3){
    
    debug_msg = "Entire User input is:"+ (pwd_index+1);
    Serial.println(debug_msg);
    for (int i=0;i<17;i++){
    //Write the userinput to EEProm memory
    Serial.print(user_input[i]);
  }
  Serial.println(F(""));
  admin_modeA();
}

else if (pass_mode==6)
{
Serial.println("Inside{ else if (pass_mode==6)}"); 
  lockServo();
}
/*else{
  myservo.detach();
}*/


pwd_index =0;//Refresh to start listening to new values, should we put this in the while loop?
pass_mode=0;
user_input_mode[0] = '\0';
delay(200);
/*debug_msg = "SRAM used"+ freeRam();

  delay(2000);
    Serial.println(debug_msg);
debug_msg = "Resetting pass_mode:"+ (pass_mode);
    Serial.println(debug_msg);
    debug_msg = "Resetting pwd_index:"+ (pwd_index);
    Serial.println(debug_msg);
    */
}

void admin_modeA(){


  setColor(80, 0, 80); // purple
  delay(2500);

  admin_tracker=9;
  eeAddress=0;
  for (;admin_tracker<17;admin_tracker++){
    //Write the userinput to EEProm memory
    EEPROM.put(eeAddress, user_input[admin_tracker]);
    Serial.print(F("Just wrote to memory:"));
    Serial.println(user_input[admin_tracker]);
    eeAddress += 4;
  }

  setColor(0, 255, 0); // green
  delay(2500);
  setColor(0, 0, 0); // off
  myservo.detach();
  pass_mode=0;


}
void unlockServo(){
  
  setColor(0, 64, 64); // aqua /

  myservo.attach(8);
  delay(500);  
  myservo.write(105);

  if(pass_mode!=5){
    delay(2500);
    Serial.write("Locked-E");
    lockServo();
  }
  else{
  delay(2500);
    myservo.detach();
    setColor(0, 0, 0); // off
  }
}

void lockServo(){
  setColor(64, 0, 0); //red

   myservo.attach(8);
  delay(500);
  myservo.write(15);
  delay(1000);

  myservo.detach();
  setColor(0, 0, 0); // off
  delay(1500);

/*  setColor(255, 0, 0); // red
  delay(1000);
  setColor(0, 255, 0); // green
  delay(1000);
  setColor(0, 0, 255); // blue
  delay(1000);
  setColor(255, 255, 0);// yellow
  delay(1000);
  setColor(80, 0, 80); // purple
  delay(1000);
  setColor(0, 255);

    delay(1000);
  setColor(0, 0, 0); // off
  delay(1500);*/
}

/*int freeRam () 
{ 
  extern int __heap_start, *__brkval; 
  int v; 
  return (int) &v - (__brkval == 0 ? (int) &__heap_start : (int) __brkval); 
}*/

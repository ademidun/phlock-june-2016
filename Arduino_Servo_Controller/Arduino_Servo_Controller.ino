

// Arduino Bluetooth LE Servo Controlled by iOS
#include <Servo.h>
#include <SoftwareSerial.h>
#define COMMON_ANODE
int lock = 8;          //pin 9 on Arduino
int LED_red = 6;
int LED_green = 5;
int LED_blue = 3;

#define COMMON_ANODE



char final[4];         //Characters the Arduino will receive
char correct[4] = {'A','B','C','D'};    //User-Defined Password
int pass_correct = 0;          //Does Password match, 0=false 1=true
char incoming_byte;
boolean locked = true;
Servo myservo;
//SoftwareSerial(rxpin, txpin)
int BT_tx = 10;
int BT_rx = 9;

SoftwareSerial BLE_Shield(BT_tx, BT_rx);//SPP is inverted, bluetooth rxd is arduino txd

void setup()
{
myservo.attach(8);
BLE_Shield.begin(9600);
myservo.write(15);

pinMode(LED_red, OUTPUT);
pinMode(LED_green, OUTPUT);
pinMode(LED_blue, OUTPUT);
}

void setColor (int red=0, int green=0, int blue=0, int brightness=100){
  
  /*#ifdef COMMON_ANODE
  red = 255 - red;
  green = 255 - green;
  blue = 255 - blue;
  #endif*/
  
  brightness = brightness/100;
  red = red*brightness;
  green = green* brightness;
  blue = blue*brightness;
  analogWrite(LED_red, red);
  analogWrite(LED_green, green);
  analogWrite(LED_blue, blue);
}
void loop()
{
  
while (BLE_Shield.available()) 
{
  incoming_byte=BLE_Shield.read();//read the value from the user
  Serial.write(incoming_byte);
  if(incoming_byte=='1'){
   pass_correct= 1 ;
  }
  else if (incoming_byte=='2'){
   pass_correct=2; 
  }
  else{
    pass_correct=0;
  }
 /* for(int i=0; i<4; i++)   //While data is available read 4 bytes
  {
   final[i] = BLE_Shield.read();  //Read 4 bytes into the array labled "final"
  }

  for(int i=0; i<4; i++)
  {
   if(final[i]==correct[i]) //Compare each char received to each car in our password in order
   {
    pass_correct = 1;   //If we compare two chars and they match, set the pass_correct variable to true(1)
   }
   else
   {
    pass_correct = 0;  //if the two compared chars do NOT match, set pass_correct variable to false(0)
    break;   //End loop and stop comparing chars
   }
  }*/
  
  
}

if(pass_correct==1)  //If all chars compared match, deactivate(unlock) the lock for 5 seconds
{
  /*Serial.write("Unlocked");
  
  setColor(0, 255, 255); // aqua setColor(0, 255, 255)
  delay(500);  
  myservo.write(105);

  delay(2500);
  
  myservo.write(15);
  setColor(0, 0, 0); // off*/
  unlockServo();
  Serial.write("Locked");
  pass_correct = 0;  

}
else if (pass_correct==2)
{
  /*setColor(255, 0, 0); // red
  delay(2500);
  pass_correct==0;
  setColor();*/
  lockServo();
}

/* FOR TESTING
Serial.print(final[0]);Serial.print(final[1]);Serial.print(final[2]);Serial.print(final[3]);
Serial.print(" | ");
Serial.print(correct[0]);Serial.print(correct[1]);Serial.print(correct[2]);Serial.print(correct[3]);
Serial.print(" ");
Serial.print(pass_correct);
Serial.println("");
*/
delay(500);


}

void unlockServo(){
  
  setColor(0, 64, 64); // aqua setColor(0, 255, 255), 25% intensity
  delay(500);  
  myservo.write(105);

  delay(2500);
  
  lockServo();
  
}

void lockServo(){
  setColor(64, 0, 0); // red
  delay(500);
  myservo.write(15);
  delay(1000);
  setColor(0, 0, 0); // off
  
}

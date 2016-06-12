

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
int pass_correct = 0;//Does Password match, 0=false 1=true
int randNumber ;
char incoming_byte;
char command;
boolean locked = true;
boolean ledon = false;
String user_string;
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

randomSeed(analogRead(0)); //pin 0 is unconnected so we want analog noise to
//generate a truly random seed
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
if (BLE_Shield.available() > 0)
{user_string = "";}  
while (BLE_Shield.available()) 
{command = ((byte)Serial.read());
if (command ==':')
{
  break;
}
else{
  user_string +=command;
}
delay(1);
}


user_string.toUpperCase();

if(user_string == "LK")
{
lockServo();
ledon = true;
}
if(user_string =="UL")
{
unlockServo();
ledon = false;
Serial.println(user_string); //debug
}

if ((user_string.toInt()>=0)&&(user_string.toInt()<=255))
{
if (ledon==true)
{ 
int r =  (int)random(user_string.toInt());
int b =  (int)random(user_string.toInt());
int g =  (int)random(user_string.toInt());
setColor(r,b,g);
Serial.println(user_string); //debug
delay(10);
}
}
}



void unlockServo(){
  
  setColor(0, 255, 255); // aqua setColor(0, 255, 255)
  delay(500);  
  myservo.write(105);

  delay(2500);
  
  lockServo();
  
}

void lockServo(){
  setColor(255, 0, 0); // red
  delay(500);
  myservo.write(15);
  delay(1000);
  setColor(0, 0, 0); // off
  
}


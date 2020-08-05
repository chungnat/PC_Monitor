#include <SoftwareSerial.h>

SoftwareSerial ESPserial(2, 3); // RX | TX
const int relaySwitch = 7;
const int tempPin = A1;  // LM36
const int ldrPin = A0;

void setup() {
  Serial.begin(9600);    // Begins Bluetooth Communication
  ESPserial.begin(9600);
  pinMode(relaySwitch, OUTPUT); 
  digitalWrite(relaySwitch, LOW); //top pin connected
  Serial.println("");
  Serial.println("Remember to to set Both NL & CR in the serial monitor.");
  Serial.println("Ready");
  Serial.println("");
  Serial.println("Connecting to WiFi");
  
  delay(4000); //Allow ESP8266 to connect to wifi
  while (ESPserial.available()){
     String inData = ESPserial.readStringUntil('\n');
     Serial.println(inData);
  }  
  ESPserial.println("AT+CIPMUX=1");
  delay(30);
  while (ESPserial.available()){
     String inData = ESPserial.readStringUntil('\n');
     Serial.println("Configuring Server: " + inData);
  }  
  ESPserial.println("AT+CIPSERVER=1,80");
  delay(30);
  while (ESPserial.available()){
     String inData = ESPserial.readStringUntil('\n');
     Serial.println("Opening server at port 80: " + inData);
  }  
  Serial.println("IP and MAC Address:");
  ESPserial.println("AT+CIFSR");
  delay(30);
  while (ESPserial.available()){
     String inData = ESPserial.readStringUntil('\n');
     Serial.println(inData);
  } 
}

void loop() {

  while (ESPserial.available()){
    String inData = ESPserial.readStringUntil('\n');
    if(inData.startsWith("+IPD,0,17:ZOyMzWG9IJWa2xu6")) { // ESP was sent wake key, call sleepWake and close the connection
      Serial.println("WakeKey Matched!");
      //sleepWake();
      ESPserial.println("AT+CIPCLOSE=0");
      delay(30);
      while (ESPserial.available()){
         inData = ESPserial.readStringUntil('\n');
         Serial.println("Closing " + inData);
      } 
    }
    else if(inData.startsWith("+IPD,0,17:H83ENi9gzq8lEXxt")) { // updateKey sent, send over data
      Serial.println("UpdateKey Matched!");
      float temp = readTemp();
      int intTemp = round(temp);
      String toString = String(intTemp, DEC);
      String tempValue = toString + "ºC";
      String temperature = "Temperature: " + tempValue;
      int light = analogRead(A0);
      Serial.println(light);
      Serial.println(temperature);
      if(light > 30)  {
        sendData("Status: Awake");
      }
      else {
        sendData("Status: Sleeping");
      }
      delay(100);  
      sendData(temperature);
      delay(100);
      ESPserial.println("AT+CIPCLOSE=0");
      delay(30);
      while (ESPserial.available()){
         inData = ESPserial.readStringUntil('\n');
         Serial.println("Closing " + inData);
      }
    }
    else if(inData.startsWith("0,CONNECT")) {  // Arduino connected to phone
      Serial.println("Connected");
    }
    else {
      Serial.println(inData);
    }
  } 
  // listen for user input and send it to the ESP8266
  if (Serial.available()) {
    ESPserial.write(Serial.read()); 
  }
}

void sleepWake() {
    digitalWrite(relaySwitch, HIGH);
    delay(300);
    Serial.print("Sleep/Wake\n");
    digitalWrite(relaySwitch, LOW);
}

void sendData(String message) {
  String command = "AT+CIPSEND=0,";
  String fullCommand = command + message.length();
  ESPserial.println(fullCommand);
  delay(30);
  while (ESPserial.available()){
    String inData = ESPserial.readStringUntil('\n');
    Serial.println("Sending " + inData);
  }  
  delay(30);
  ESPserial.println(message);
  delay(200);
    while (ESPserial.available()){
    String inData = ESPserial.readStringUntil('\n');
    Serial.println("Sending " + inData);
  }
}

float readTemp() {
  int temp = analogRead(tempPin);
  delay(30);
  temp = analogRead(tempPin);
  float voltTemp = temp * ((float)5 / 1024);
  float actualTemp = (voltTemp - 0.5) * 100;
  return actualTemp;
}

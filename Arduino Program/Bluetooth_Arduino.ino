#include <LiquidCrystal_I2C.h>
#include <SoftwareSerial.h>
#include <dht.h>

// Set the LCD address to 0x27 for a 16 chars and 2 line display
LiquidCrystal_I2C lcd(0x27, 16, 2);

SoftwareSerial bluetooth(2, 4); // RX, TX

dht DHT;

const long RETURN_HOME_INTERVAL = 3000; // millis to return to home screen
unsigned long lastPrintTime = 0; // last time printed on LCD

boolean fanAutoActivated = false; // were the fan activated automatically?
const long REFRESH_INTERVAL_DHT11 = 5000; // millis to update DHT11
unsigned long lastRefreshDHT11 = -REFRESH_INTERVAL_DHT11; // last time DHT11 updated

boolean lightsAutoActivated = false; // were the lights activated automatically?
const long LIGHTS_AUTO_INTERVAL = 10000; // millis lights turned on
unsigned long lastAutoLights = 0; // last time lights auto-activated

boolean autoActivateLights = true; // lights will be activated automatically?
boolean autoActivateFan = true; // fan will be activated automatically?

int lastTemperature = -99;
int lastHumidity = -99;

boolean homeLCD = false; // LCD displaying home screen?

const int LIGHTS_PIN = 12;
const int FAN_PIN = 13;
const int DHT11_PIN = 6; // temp & humidity sensor
const int PIR_PIN = 7; // motion sensor

const char TEMP_THRESHOLD_MAX = 23;
const char TEMP_THRESHOLD_MIN = 20;

// Codes for Bluetooth reading
const char CONNECTED = '0';
const char LIGHTS_OFF = '1';
const char LIGHTS_ON = '2';
const char FAN_OFF = '3';
const char FAN_ON = '4';
const char AUTO_LIGHTS = '5'; // auto-activate lights
const char NO_AUTO_LIGHTS = '6'; // disable auto-activate lights
const char AUTO_FAN = '7'; // auto-activate fan
const char NO_AUTO_FAN = '8'; // disable auto-activate fan
const char NO_AUTO_LIGHTS_FAN = '9'; // disable auto-activate lights & fan

// Codes for Bluetooth writing
const String TEMP_AND_HUMIDITY = "TH"; // temperature & humidity
const String AUTO_LIGHTS_OFF = "0L";
const String AUTO_LIGHTS_ON = "1L";
const String AUTO_FAN_OFF = "0F";
const String AUTO_FAN_ON = "1F";

void setup() {
  // initialize the LCD
  lcd.begin();
  lcd.backlight();

  // LEDs
  pinMode(LIGHTS_PIN, OUTPUT);
  pinMode(FAN_PIN, OUTPUT);

  // PIR sensor
  pinMode(PIR_PIN, INPUT);

  // Bluetooth
  bluetooth.begin(9600); // start the bluetooth UART at 9600 (default)

  splashScreen();

  // inmediately send temperature & humidity values to Android
  refreshDHT11();
  btSendTempHumData(DHT.temperature, DHT.humidity);
}

void loop() {
  if (autoActivateLights && digitalRead(PIR_PIN) && !digitalRead(LIGHTS_PIN)) {
    bluetooth.print(AUTO_LIGHTS_ON);
    turnLights(HIGH);
    lightsAutoActivated = true;
    lastAutoLights = millis();
  } else if (autoActivateLights && millis() - lastAutoLights >
                      LIGHTS_AUTO_INTERVAL && lightsAutoActivated) {
    turnLights(LOW);
    bluetooth.print(AUTO_LIGHTS_OFF);
    lightsAutoActivated = false;
  }
  
  // DHT11 management
  if (millis() - lastRefreshDHT11 > REFRESH_INTERVAL_DHT11) {
    refreshDHT11(); // update DHT11 data
    // turn on/off fan automatically according to the threshold
    if (autoActivateFan && DHT.temperature > TEMP_THRESHOLD_MAX
                                          && !digitalRead(FAN_PIN)) {
      turnFan(HIGH);
      bluetooth.print(AUTO_FAN_ON);
      fanAutoActivated = true;
    } else if (autoActivateFan && DHT.temperature < TEMP_THRESHOLD_MIN
                                                  && digitalRead(FAN_PIN)) {
      turnFan(LOW);
      bluetooth.print(AUTO_FAN_OFF);
      fanAutoActivated = false;
    } else if (millis() - lastPrintTime > RETURN_HOME_INTERVAL &&
        (lastTemperature != DHT.temperature || lastHumidity != DHT.humidity)) {
      printHomeScreen(DHT.temperature, DHT.humidity);
      // send temperature & humidity data to Android
      btSendTempHumData(DHT.temperature, DHT.humidity);
    }
  }

  // Home screen
  if (!homeLCD && millis() - lastPrintTime > RETURN_HOME_INTERVAL) {
    printHomeScreen(DHT.temperature, DHT.humidity);
    btSendTempHumData(DHT.temperature, DHT.humidity);
  }

  // Bluetooth reading
  if (bluetooth.available()) { // check if anything in UART buffer
    homeLCD = false;
    switch (bluetooth.read()) {
      case LIGHTS_OFF:
        turnLights(LOW);
        lightsAutoActivated = false;
        break;
      case LIGHTS_ON:
        turnLights(HIGH);
        break;
      case FAN_OFF:
        turnFan(LOW);
        fanAutoActivated = false;
        break;
      case FAN_ON:
        turnFan(HIGH);
        break;
      case NO_AUTO_LIGHTS:
        autoActivateLights = false;
        lightsAutoActivated = false;
        break;
      case AUTO_LIGHTS:
        autoActivateLights = true;
        break;
      case NO_AUTO_FAN:
        autoActivateFan = false;
        break;
      case AUTO_FAN:
        autoActivateFan = true;
        break;
      case NO_AUTO_LIGHTS_FAN:
        autoActivateLights = false;
        autoActivateFan = false;
        lightsAutoActivated = false;
        break;
      case CONNECTED:
        // update data on Android
        btSendTempHumData(DHT.temperature, DHT.humidity);
        delay(200);
        if (lightsAutoActivated) {
          bluetooth.print(AUTO_LIGHTS_ON);
          delay(200);
        }
        if (fanAutoActivated) {
          bluetooth.print(AUTO_FAN_ON);
        }
        homeLCD = false;
        clearPrint("    ANDROID", "   connected");
        break;
    }
  }
}

void refreshDHT11() {
  lastTemperature = DHT.temperature;
  lastHumidity = DHT.humidity;
  DHT.read11(DHT11_PIN);
  lastRefreshDHT11 = millis();
}

void turnLights(int state) {
  homeLCD = false;
  if (state) {
    clearPrint("     Lights", "       ON");
    digitalWrite(LIGHTS_PIN, HIGH);
  } else {
    clearPrint("     Lights", "       OFF");
    digitalWrite(LIGHTS_PIN, LOW);
  }
}

void turnFan(int state) {
  homeLCD = false;
  if (state) {
    clearPrint("      Fan", "      ON");
    digitalWrite(FAN_PIN, HIGH);
  } else {
    clearPrint("      Fan", "      OFF");
    digitalWrite(FAN_PIN, LOW);
  }
}

void btSendTempHumData(double temperature, double humidity) {
  bluetooth.print(TEMP_AND_HUMIDITY + (String) ((int) temperature) + " "
                      + (String) ((int) humidity));
}

void clearPrint(String text) {
  lcd.clear();
  lcd.print(text);
  lastPrintTime = millis();
}


void clearPrint(String firstLine, String secondLine) {
  lcd.clear();
  lcd.print(firstLine);
  lcd.setCursor(0, 1);
  lcd.print(secondLine);
  lastPrintTime = millis();
}

void printHomeScreen(double temperature, double humidity) {
  lcd.clear();
  lcd.print("   Temp: ");
  lcd.print((int) temperature);
  lcd.print((char) 223);
  lcd.print("C");
  lcd.setCursor(0, 1);
  lcd.print("   Hum:  ");
  lcd.print((int) humidity);
  lcd.print(" %");
  lastPrintTime = millis();
  homeLCD = true;
}

void splashScreen() {
  lcd.clear();
  lcd.print("  ");
  lcd.print(" < HOMEY >");
  lcd.setCursor(6, 1);
  for (int i = 0; i < 3; i++) {
    for (int j = 0; j < 3; j++) {
      delay(400);
      lcd.print(".");
    }
    delay(400);
    lcd.setCursor(6, 1);
    lcd.print("   ");
    lcd.setCursor(6, 1);
  }
}


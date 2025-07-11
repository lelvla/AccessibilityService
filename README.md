## Environment
need to install Visual Studio Build Tools for npm install to build mic extension with C++

#### Visual Studio Build Tools (Windows)
- choose Desktop development with C++
- also check Windows 11 (or 10) SDK on the right side

#### Install the server environment
```
npm install
```

#### Network setting
The server IP is hardcoded in the Android service.
- Please **update the server IP and rebuild** the service before running.
- Ensure that you are **connected to the same WiFi network** for reliable communication between the cellphone and server (you can verify connectivity using ping).


## Execute the server
### Notice
Current program can only accept one connection at a time; if a connection(attack) ends, please restart the server for another test.
##### Personal Suggestion
Connect the headset to the computer running the server.
It is better to distinguish if the audio successfully transfer.
- from headset -> cellphone to server
- from cellphone's speaker -> server to cellphone

### Steps
1. Run the server first, then run the AccessibilityService on the phone
```
node server.js
```
2. After the AccessibilityService running, the console of server will log a cellphone connected.
3. Start calling and check if the sound of both channel is clear.
4. Close the AccessibilityService, the server will log the connection is ended.

Please restart the server before another test start.
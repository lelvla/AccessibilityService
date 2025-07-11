const express = require('express')
const WebSocket = require('ws');
const http = require('http');
const app = express()
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });
const Speaker = require('speaker');
const mic = require('mic');
const port = 3000

const audioConfig = {
	rate: '44100',
	channels: '2'
}

const speaker = new Speaker({
  channels: 2,
  bitDepth: 16,
  sampleRate: 44100,
  signed:     true,
  float:      false
});
const micInstance = mic({
	rate: audioConfig.rate,
	channels: audioConfig.channels,
	debug: false
});
wss.on('connection', ws => {
    var ip = ws._socket.remoteAddress;
    var sentPackets = 0, receivedPackets = 0;
    console.log('Connected to ' + ip);
	  micInstance.start();
    const micInputStream = micInstance.getAudioStream();
    micInputStream.on('data', (data) => {
        // sentPackets++;  // Increase the sent packets counter
        // console.log(`Sent packets: ${sentPackets}`);
		    ws.send(data);
    });
    ws.on('message', message => {
      // receivedPackets++;  // Increase the sent packets counter
      // console.log(`Received packets: ${receivedPackets}`);
      speaker.write(message);
    });
    ws.on('close', () => {
        console.log('Disconnected from ' + ip);
        speaker.end();
		    micInstance.stop();
    });
    
});

server.listen(port, '0.0.0.0',() => {
  console.log(`Example app listening on port ${port}`)
})
const http = require('http');
const fs = require('fs');
const path = require('path');
const { WebSocketServer } = require('ws');

const PORT = 8080;

// Serve the static HTML
const server = http.createServer((req, res) => {
  if (req.url === '/' || req.url === '/index.html') {
    res.writeHead(200, { 'Content-Type': 'text/html' });
    fs.createReadStream(path.join(__dirname, 'index.html')).pipe(res);
  } else {
    res.writeHead(404);
    res.end('Not found');
  }
});

const wss = new WebSocketServer({ server });

// Some plausible city coordinates for demo traffic
const cities = [
  { name: 'San Francisco', lat: 37.77, lng: -122.42 },
  { name: 'New York',     lat: 40.71, lng: -74.01 },
  { name: 'London',       lat: 51.51, lng: -0.13 },
  { name: 'Frankfurt',    lat: 50.11, lng: 8.68 },
  { name: 'Singapore',    lat: 1.35,  lng: 103.82 },
  { name: 'Tokyo',        lat: 35.68, lng: 139.69 },
  { name: 'Sydney',       lat: -33.87,lng: 151.21 },
  { name: 'São Paulo',    lat: -23.55,lng: -46.63 },
  { name: 'Mumbai',       lat: 19.08, lng: 72.88 },
  { name: 'Seoul',        lat: 37.57, lng: 126.98 },
  { name: 'Los Angeles',  lat: 34.05, lng: -118.24 },
  { name: 'Chicago',      lat: 41.88, lng: -87.63 },
  { name: 'Toronto',      lat: 43.65, lng: -79.38 },
  { name: 'Amsterdam',    lat: 52.37, lng: 4.90 },
  { name: 'Hong Kong',    lat: 22.32, lng: 114.17 },
];

function randomCity() {
  return cities[Math.floor(Math.random() * cities.length)];
}

function generateTraffic() {
  const arcs = [];
  const points = [];

  // 12–25 active connections
  const count = 12 + Math.floor(Math.random() * 14);

  for (let i = 0; i < count; i++) {
    const start = randomCity();
    let end = randomCity();
    while (end.name === start.name) end = randomCity();

    const color = Math.random() > 0.7
      ? ['#a78bfa', '#ffffff']   // occasional purple
      : ['#ff6b9d', '#ffffff'];  // main pink

    arcs.push({
      startLat: start.lat,
      startLng: start.lng,
      endLat: end.lat,
      endLng: end.lng,
      color
    });

    // also place points at both ends
    points.push({ lat: start.lat, lng: start.lng });
    points.push({ lat: end.lat, lng: end.lng });
  }

  return { arcs, points };
}

// Broadcast new traffic every 1.8 seconds
setInterval(() => {
  const payload = JSON.stringify(generateTraffic());
  wss.clients.forEach(client => {
    if (client.readyState === 1) client.send(payload);
  });
}, 1800);

// Also send immediately on new connection
wss.on('connection', (ws) => {
  console.log('Client connected');
  ws.send(JSON.stringify(generateTraffic()));
});

server.listen(PORT, () => {
  console.log(`\n  Live Network Globe running at:`);
  console.log(`  →  http://localhost:${PORT}\n`);
});

import { Server } from 'ws';
import { createServer } from 'http';
import Ubicare from './ubicare';
// import UbicareMulti from './ubicare.multi';

const wss = new Server({ port: 7000 });
const ubicare = new Ubicare();

wss.on('connection', ws => {
  ubicare.register(ws);
});
import { Server } from 'ws';
import { createServer } from 'http';
import Ubicare from './ubicare';

const wss = new Server({ port: 7000 });
const mm = new Ubicare();

wss.on('connection', ws => {
  console.log('connection x');
  mm.register(ws);
});
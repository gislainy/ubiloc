import { Server } from 'ws';
import Ubicare from './ubicare';

const wss = new Server({ port: 7000 });
const ubicare = new Ubicare();

wss.on('connection', ws => {
  ubicare.register(ws);
});
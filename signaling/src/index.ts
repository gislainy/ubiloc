import { Server } from 'ws';
import Ubicare from './ubicare.old';

const wss = new Server({ port: 7000 });
const ubicare = new Ubicare();

wss.on('connection', ws => {
  console.log("conectado")
  ubicare.register(ws);
});

// import { createServer, Server } from 'http';
// import * as express from 'express';
// import * as socketIO from 'socket.io';

// import UbicareMulti from './ubicare.multi';
// const ubicare = new UbicareMulti();

// export class CreateServer {
//   public static readonly PORT:number = 7000;
//   private app: express.Application;
//   private server: Server;
//   private io: SocketIO.Server;
//   private port: string | number;

//   constructor() {
//       console.log('criando')
//       this.createApp();
//       this.config();
//       this.createServer();
//       this.listen();
//       this.sockets();
//   }

//   private createApp(): void {
//       this.app = express();
//   }

//   private createServer(): void {
//       this.server = createServer(this.app);
//   }

//   private config(): void {
//       this.port = process.env.PORT || CreateServer.PORT;

//   }

//   private sockets(): void {
//       this.io = socketIO.listen(this.server);
//       this.io.on('connection', (socket) => {
//         ubicare.register(socket)
//       });
//   }

//   private listen(): void {
//       this.server.listen(this.port, () => {
//           console.log('Running server on port %s', this.port);
//       });
//   }

//   public getApp(): express.Application {
//       return this.app;
//   }
// }

// const app = new CreateServer().getApp();
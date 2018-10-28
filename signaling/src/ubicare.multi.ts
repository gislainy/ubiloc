import * as WebSocket from 'ws';
import * as uuidv4 from 'uuid/v4';
import { EventEmitter } from 'events';

enum MessageType {
  MATCHED = 'matched',
  SDP = 'sdp',
  ICE = 'ice',
  PEER_LEFT = 'peer-left',
  LOCATOR = 'locator',
  MONITOR = 'monitor'
}

enum SessionType {
  LOCATOR = 'locator',
  MONITOR = 'monitor'
  
}

type MatchMessage = {
  type: MessageType.MATCHED
  match: string
  offer: boolean
}

type SDPMessage = {
  type: MessageType.SDP
  sdp: string
}

type ICEMessage = {
  type: MessageType.ICE
  candidate: string,
  label: number,
  id: string
}

type PeerLeft = {
  type: MessageType.PEER_LEFT
}
type LocatorMessage = {
  type: MessageType.LOCATOR,
  permission: string,
}
type MonitorMessage = {
  type: MessageType.MONITOR,
  permission: string,
}

type ClientMessage
  = MatchMessage
  | SDPMessage
  | ICEMessage
  | PeerLeft
  | LocatorMessage
  | MonitorMessage

type Session = {
  id: string
  ws: WebSocket,
  type?: string,
  permission?: SessionType,
  peer?: Array<string>
}

export default class Ubicare {

  private sessions: Map<string, Session>;
  private locator: Array<string>;
  private monitor: Array<string>;

  constructor() {
    this.sessions = new Map();
    this.locator = [];
    this.monitor = [];
  }

  register(ws: WebSocket) {
    const id = uuidv4();
    const peer = [];
    const session = { id, ws, peer };

    this.sessions.set(id, session);
    //this.tryMatch(session);

    ws.on('close', () => this.unregister(id));
    ws.on('error', () => this.unregister(id));
    ws.on('message', (data: WebSocket.Data) => this.handleMessage(id, data.toString()));
  }

  private handleMessage(id: string, data: string) {
    try {
      const message = JSON.parse(data) as ClientMessage;
      console.log("handleMessage", message);
      const session = this.sessions.get(id);
      if (!session) { return console.error(`Can't find session for ${id}`); }
      switch (message.type) {
        case MessageType.LOCATOR:
          this.handleLocator(id, data);
          break;
        case MessageType.MONITOR:
          this.handleMonitor(id, data);
          break;
        case MessageType.SDP:
        case MessageType.ICE:
          session.peer.forEach(pId => {
            console.log({peerId: pId, message});
            const peer = this.sessions.get(pId);
            if (!peer) { return console.error(`Can't find session for peer of ${id}`); }
            this.send(peer, message);
          })
          break;
        default:
          console.error(`Unexpected message from ${id}: ${data}`);
          break;
      }
    } catch (err) {
      console.error(`Unexpected message from ${id}: ${data}`)
      console.error(`Erro: ${err}`);

    }
  }

  // private tryMatch(session: Session) {
  //   if (this.unmatched.length > 0) {
  //     const match = this.unmatched[0];
  //     const other = this.sessions.get(match);
  //     if (other) {
  //       session.peer.push(match);
  //       other.peer.push(session.id);
  //       this.send(session, { type: MessageType.MATCHED, match: other.id, offer: true });
  //       this.send(other, { type: MessageType.MATCHED, match: session.id, offer: true });
  //     }
  //   } else {
  //     this.unmatched.push(session.id);
  //   }
  // }

  private unregister(id: string) {
    const session = this.sessions.get(id);
    if (session && session.peer) {
      session.peer.forEach(pId => {
        const peer = this.sessions.get(pId);
        if (peer) this.send(peer, { type: MessageType.PEER_LEFT });
      })
    }
    this.monitor = this.monitor.filter(other => id !== other);
    this.locator = this.locator.filter(other => id !== other);
    this.sessions.delete(id);
  }

  private send(session: Session, payload: ClientMessage) {
    try {
      if (session.ws.readyState === WebSocket.OPEN) {
        session.ws.send(JSON.stringify(payload));
      }
    } catch (err) {
      console.error(`Error sending to ${session.id}`);
    }
  }

  private handleMonitor(id: string, data: string) {
    const message = JSON.parse(data);
    console.log('handleMonitor', arguments);
    const monitor = this.sessions.get(id);
    monitor.type = SessionType.LOCATOR;
    monitor.permission = message.permission;
    this.monitor.push(id);
    this.locator.forEach(mId => {
      const locator = this.sessions.get(mId);
      if(locator.permission == message.permission) {
        monitor.peer.push(mId);
        locator.peer.push(id);
        this.send(monitor, { type: MessageType.MATCHED, match: locator.id, offer: false });
        this.send(locator, { type: MessageType.MATCHED, match: monitor.id, offer: true });
      }
    });
  }
  private handleLocator(id: string, data: any) {
    const message = JSON.parse(data);
    console.log('handleLocator', arguments);
    const locator = this.sessions.get(id);
    locator.type = SessionType.LOCATOR;
    locator.permission = message.permission;
    this.locator.push(id);
  }
}
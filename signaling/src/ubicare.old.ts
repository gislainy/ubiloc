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
  id: string,
  ws: WebSocket
  peer: Array<string>,
  locator?: boolean,
  permission?: string,
}

export default class Ubicare {

  private sessions: Map<string, Session>;
  private unmatched: Array<string>;
  // private unmatched : Array<string>;
  private locator: Array<string>
  private monitor: Array<string>
  constructor() {
    this.sessions = new Map();
    this.unmatched = [];
    this.locator = [];
    this.monitor = [];
  }

  register(ws: WebSocket) {
    const id = uuidv4();
    const peer = [];
    const session = { id, ws, peer };

    this.sessions.set(id, session);
    this.tryMatch(session);

    ws.on('close', () => this.unregister(id));
    ws.on('error', () => this.unregister(id));
    ws.on('message', (data: WebSocket.Data) => this.handleMessage(id, data.toString()));
  }
  private handleLocator(id: string, data: string) {
    console.log('handleLocator', arguments);
    const locator = this.sessions.get(id);
    locator.locator = true;
    // session.permission = data.permission;
    this.locator.push(id);
    this.monitor.forEach(m => {
      const monitor = this.sessions.get(m);
      if (monitor.peer.length == 0) {
        monitor.peer.push(id);
        locator.peer.push(m);
        this.send(monitor, { type: MessageType.MATCHED, match: locator.id, offer: false });
        this.send(locator, { type: MessageType.MATCHED, match: monitor.id, offer: true });
      }
    });
  }
  private handleMonitor(id: string, data: string) {
    console.log('handleMonitor', arguments);
    const monitor = this.sessions.get(id);
    monitor.locator = false;
    const locatorId = this.locator[0];
    const locator = this.sessions.get(locatorId);
    this.monitor.push(id);
    if (locator) {
      monitor.peer.push(locator.id);
      locator.peer.push(id);
      this.send(monitor, { type: MessageType.MATCHED, match: locator.id, offer: true });
      this.send(locator, { type: MessageType.MATCHED, match: monitor.id, offer: false });
    }
  }
  private handleMessage(id: string, data: string) {
    try {
      const message = JSON.parse(data) as ClientMessage;
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
          if (session.peer && session.peer.length) {
            console.log('MessageType', message.type)
            const pId = session.peer[0]
            // session.peer.forEach(pId => {
              const peer = this.sessions.get(pId);
              if (!peer) { return console.error(`Can't find session for peer of id ${id} with pid ${pId}`); }
              if (peer.id != id) {
                console.log(['send de', id,'para', peer.id].join(' '));
                this.send(peer, message);
              }
            // });
            break;
          }
        default:
          console.error(`SWITCH Unexpected message from ${id}: ${data}`);
          break;
      }
    } catch (err) {
      console.error(`Unexpected message from ${id}: ${data}`);
      console.error(`Erro: ${err}`);
    }
  }

  private tryMatch(session: Session) {
    if (this.unmatched.length > 0) {
      // this.unmatched.forEach(match => {
        const match = this.unmatched[0];
        const other = this.sessions.get(match);
        if (other) {
          if (session.peer.every(p => p != match))
            session.peer.push(match);
          if (other.peer.every(p => p != session.id))
            other.peer.push(session.id);
          this.send(session, { type: MessageType.MATCHED, match: other.id, offer: true });
          this.send(other, { type: MessageType.MATCHED, match: session.id, offer: false });
        }
      // });
      // this.unmatched.push(session.id);
    } else {
      this.unmatched.push(session.id);
    }
  }

  private unregister(id: string) {
    console.log('unregister', arguments)
    const session = this.sessions.get(id);
    if (session && session.peer) {
      for (const p in session.peer) {
        const peer = this.sessions.get(p);
        if (peer) this.send(peer, { type: MessageType.PEER_LEFT })
      }
    }

    if (session && session.locator) {
      this.locator = this.locator.filter(l => l != id);
    } else {
      this.monitor = this.monitor.filter(m => m != id);
    }
    this.unmatched = this.unmatched.filter(m => m != id);
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

}
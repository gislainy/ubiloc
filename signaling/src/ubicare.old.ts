import * as WebSocket from 'ws';
import * as uuidv4 from 'uuid/v4';
import { EventEmitter } from 'events';

enum MessageType {
  MATCHED = 'matched',
  SDP = 'sdp',
  ICE = 'ice',
  PEER_LEFT = 'peer-left'
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

type ClientMessage
  = MatchMessage
  | SDPMessage
  | ICEMessage
  | PeerLeft

type Session = {
  id: string
  ws: WebSocket,

  peer?: Array<string>
}

export default class Roulette {

  private sessions: Map<string, Session>;
  private unmatched: Array<string>;

  constructor() {
    this.sessions = new Map();
    this.unmatched = [];
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

  private handleMessage(id: string, data: string) {
    try {
      const message = JSON.parse(data) as ClientMessage;
      const session = this.sessions.get(id);
      if (!session) { return console.error(`Can't find session for ${id}`); }
      switch (message.type) {
        case MessageType.SDP:
        case MessageType.ICE:
          session.peer.forEach(pId => {
            console.log('pId', pId)
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

  private tryMatch(session: Session) {
    if (this.unmatched.length > 0) {
      const match = this.unmatched[0];
      const other = this.sessions.get(match);
      console.log('session.id', session.id)
      console.log('match', match)
      if (other) {
        session.peer.push(match);
        other.peer.push(session.id);
        this.send(session, { type: MessageType.MATCHED, match: other.id, offer: true });
        this.send(other, { type: MessageType.MATCHED, match: session.id, offer: false });
      }
    } else {
      this.unmatched.push(session.id);
    }
  }

  private unregister(id: string) {
    console.log('unregister', arguments)

    const session = this.sessions.get(id);
    if (session && session.peer) {
      session.peer.forEach(pId => {
        const peer = this.sessions.get(pId);
        if (peer) this.send(peer, { type: MessageType.PEER_LEFT })
      })
    }
    this.unmatched = this.unmatched.filter(other => id !== other);
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
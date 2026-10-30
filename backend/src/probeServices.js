import { Client } from 'ssh2';

const conn = new Client();

conn.on('ready', () => {
  conn.exec('which nginx; systemctl is-active nginx; ufw status; pm2 list', (err, stream) => {
    let output = '';
    stream.on('close', (code) => {
      console.log('--- Services & Ports ---');
      console.log(output);
      conn.end();
    }).on('data', (data) => output += data).stderr.on('data', (data) => output += data);
  });
}).connect({
  host: '42.96.5.186',
  port: 22,
  username: 'root',
  password: 'Ngu76214@#!'
});


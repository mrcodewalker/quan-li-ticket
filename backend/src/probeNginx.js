import { Client } from 'ssh2';

const conn = new Client();

conn.on('ready', () => {
  conn.exec('cat /etc/nginx/sites-enabled/*; ss -tulpn', (err, stream) => {
    let output = '';
    stream.on('close', () => {
      console.log('--- Nginx & Listening Ports ---');
      console.log(output);
      conn.end();
    }).on('data', (d) => output += d).stderr.on('data', (d) => output += d);
  });
}).connect({
  host: '42.96.5.186',
  port: 22,
  username: 'root',
  password: 'Ngu76214@#!'
});


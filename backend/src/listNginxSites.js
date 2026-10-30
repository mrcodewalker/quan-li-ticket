import { Client } from 'ssh2';

const conn = new Client();
conn.on('ready', () => {
  conn.exec('ls -la /etc/nginx/sites-enabled/', (err, stream) => {
    stream.on('data', d => process.stdout.write(d.toString()))
          .on('close', () => conn.end());
  });
}).connect({
  host: '42.96.5.186',
  port: 22,
  username: 'root',
  password: 'Ngu76214@#!'
});


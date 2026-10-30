import { Client } from 'ssh2';

const conn = new Client();

console.log('Connecting to VPS 42.96.5.186...');

conn.on('ready', () => {
  console.log('SSH Connection established successfully!');

  conn.exec('uname -a; cat /etc/os-release; node -v; npm -v; which pm2; which ufw', (err, stream) => {
    if (err) {
      console.error('Exec error:', err);
      conn.end();
      return;
    }

    let output = '';
    stream.on('close', (code, signal) => {
      console.log('--- VPS Environment Info ---');
      console.log(output);
      console.log('Exit code:', code);
      conn.end();
    }).on('data', (data) => {
      output += data;
    }).stderr.on('data', (data) => {
      console.error('STDERR:', data.toString());
    });
  });
}).on('error', (err) => {
  console.error('SSH Connection error:', err.message);
}).connect({
  host: '42.96.5.186',
  port: 22,
  username: 'root',
  password: 'Ngu76214@#!'
});


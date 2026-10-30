import { Client } from 'ssh2';
import fs from 'fs';
import path from 'path';

const conn = new Client();
const archivePath = fs.existsSync('backend-deploy.tar.gz')
  ? path.resolve('backend-deploy.tar.gz')
  : path.resolve('backend/backend-deploy.tar.gz');

console.log('[Deploy] Connecting to VPS 42.96.5.186...');

conn.on('ready', () => {
  console.log('[Deploy] SSH connection ready. Starting SFTP upload...');

  conn.sftp((err, sftp) => {
    if (err) {
      console.error('[Deploy] SFTP error:', err);
      conn.end();
      return;
    }

    const readStream = fs.createReadStream(archivePath);
    const writeStream = sftp.createWriteStream('/tmp/backend-deploy.tar.gz');

    writeStream.on('close', () => {
      console.log('[Deploy] Uploaded backend-deploy.tar.gz to VPS /tmp successfully.');

      const remoteCmd = `
        set -e
        echo "=== [1] Creating directory /var/www/secureticket-backend ==="
        mkdir -p /var/www/secureticket-backend
        tar -xzf /tmp/backend-deploy.tar.gz -C /var/www/secureticket-backend
        cd /var/www/secureticket-backend

        echo "=== [2] Installing npm production dependencies ==="
        npm install --omit=dev

        echo "=== [3] Configuring UFW Firewall for port 5000 ==="
        ufw allow 5000/tcp || true

        echo "=== [4] Starting backend with PM2 ==="
        pm2 delete secureticket-backend || true
        pm2 start src/index.js --name secureticket-backend
        pm2 save

        echo "=== [5] Testing local health endpoint on VPS ==="
        sleep 2
        curl -s http://127.0.0.1:5000/health
      `;

      conn.exec(remoteCmd, (execErr, stream) => {
        if (execErr) {
          console.error('[Deploy] Exec error:', execErr);
          conn.end();
          return;
        }

        stream.on('close', (code) => {
          console.log(`[Deploy] Remote execution finished with exit code ${code}`);
          conn.end();
        }).on('data', (d) => {
          process.stdout.write(d.toString());
        }).stderr.on('data', (d) => {
          process.stderr.write(d.toString());
        });
      });
    });

    writeStream.on('error', (wErr) => {
      console.error('[Deploy] Write stream error:', wErr);
      conn.end();
    });

    readStream.pipe(writeStream);
  });
}).on('error', (err) => {
  console.error('[Deploy] SSH error:', err.message);
}).connect({
  host: '42.96.5.186',
  port: 22,
  username: 'root',
  password: 'Ngu76214@#!'
});


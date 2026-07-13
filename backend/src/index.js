import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import express from 'express';
import cors from 'cors';
import dataRouter from './routes/data.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const frontendDist = path.resolve(__dirname, '../../frontend/dist');

const app = express();
app.use(cors());
app.use(express.json());
app.use('/api', dataRouter);

// Sert le frontend buildé (npm run build côté frontend) s'il existe, pour
// pouvoir déployer front + back en un seul process/port. En dev, le
// frontend tourne séparément (npm run dev + proxy Vite) et ce dossier
// n'existe pas encore.
if (fs.existsSync(frontendDist)) {
  app.use(express.static(frontendDist));
  app.get('*', (req, res) => {
    res.sendFile(path.join(frontendDist, 'index.html'));
  });
}

const PORT = process.env.PORT || 3008;
app.listen(PORT, () => {
  console.log(`Backend démarré sur http://localhost:${PORT}`);
});

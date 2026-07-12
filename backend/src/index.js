import express from 'express';
import cors from 'cors';
import dataRouter from './routes/data.js';

const app = express();
app.use(cors());
app.use(express.json());
app.use('/api', dataRouter);

const PORT = process.env.PORT || 3008;
app.listen(PORT, () => {
  console.log(`Backend démarré sur http://localhost:${PORT}`);
});

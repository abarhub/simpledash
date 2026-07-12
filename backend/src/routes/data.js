import { Router } from 'express';
import { listProviders, getWidgets } from '../providers/index.js';

const router = Router();

router.get('/providers', (req, res) => {
  res.json(listProviders());
});

router.post('/data', async (req, res) => {
  const { ids } = req.body;
  if (!Array.isArray(ids) || ids.length === 0) {
    return res.status(400).json({ error: 'ids doit être un tableau non vide' });
  }
  const widgets = await getWidgets(ids);
  res.json({ widgets });
});

export default router;

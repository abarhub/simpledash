import { Router } from 'express';
import { listDomains, listResources, listExtractors, getWidgets } from '../domains/index.js';

const router = Router();

router.get('/domains', (req, res) => {
  res.json(listDomains());
});

router.get('/domains/:domainId/resources', (req, res) => {
  const result = listResources(req.params.domainId);
  if (!result) return res.status(404).json({ error: 'Domaine inconnu' });
  res.json(result);
});

router.get('/domains/:domainId/extractors', (req, res) => {
  const resourceIds = (req.query.resourceIds ?? '').split(',').filter(Boolean);
  const extractors = listExtractors(req.params.domainId, resourceIds);
  if (!extractors) return res.status(404).json({ error: 'Domaine inconnu' });
  res.json(extractors);
});

router.post('/data', async (req, res) => {
  const { domainId, resourceIds, extractorIds } = req.body;
  if (!domainId || !Array.isArray(resourceIds) || !Array.isArray(extractorIds)) {
    return res.status(400).json({ error: 'domainId, resourceIds et extractorIds sont requis' });
  }
  try {
    const widgets = await getWidgets(domainId, resourceIds, extractorIds);
    res.json({ widgets });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

export default router;

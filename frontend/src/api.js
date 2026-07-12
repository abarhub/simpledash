const API_URL = 'http://localhost:3008/api';

export async function fetchDomains() {
  const res = await fetch(`${API_URL}/domains`);
  return res.json();
}

export async function fetchResources(domainId) {
  const res = await fetch(`${API_URL}/domains/${domainId}/resources`);
  return res.json();
}

export async function fetchExtractors(domainId, resourceIds) {
  const params = new URLSearchParams({ resourceIds: resourceIds.join(',') });
  const res = await fetch(`${API_URL}/domains/${domainId}/extractors?${params}`);
  return res.json();
}

export async function fetchWidgets(domainId, resourceIds, extractorIds) {
  const res = await fetch(`${API_URL}/data`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ domainId, resourceIds, extractorIds }),
  });
  const json = await res.json();
  return json.widgets;
}

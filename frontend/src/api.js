const API_URL = 'http://localhost:3008/api';

export async function fetchProviders() {
  const res = await fetch(`${API_URL}/providers`);
  return res.json();
}

export async function fetchWidgets(ids) {
  const res = await fetch(`${API_URL}/data`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ids }),
  });
  const json = await res.json();
  return json.widgets;
}

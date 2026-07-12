import { useEffect, useState } from 'react';
import Card from './components/Card';
import { fetchProviders, fetchWidgets } from './api';
import './App.css';

function App() {
  const [providers, setProviders] = useState([]);
  const [selectedIds, setSelectedIds] = useState([]);
  const [widgets, setWidgets] = useState([]);
  const [loading, setLoading] = useState(false);
  const [refreshingId, setRefreshingId] = useState(null);

  useEffect(() => {
    fetchProviders().then(setProviders);
  }, []);

  function toggleProvider(id) {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    );
  }

  async function handleShow() {
    setLoading(true);
    try {
      setWidgets(await fetchWidgets(selectedIds));
    } finally {
      setLoading(false);
    }
  }

  async function handleRefresh(sourceId) {
    setRefreshingId(sourceId);
    try {
      const refreshed = await fetchWidgets([sourceId]);
      setWidgets((prev) => [
        ...prev.filter((w) => w.sourceId !== sourceId),
        ...refreshed,
      ]);
    } finally {
      setRefreshingId(null);
    }
  }

  return (
    <div className="app">
      <h1>SimpleDash</h1>

      <div className="selector">
        {providers.map((p) => (
          <label key={p.id} className="selector-item" title={p.description}>
            <input
              type="checkbox"
              checked={selectedIds.includes(p.id)}
              onChange={() => toggleProvider(p.id)}
            />
            {p.name}
          </label>
        ))}
        <button onClick={handleShow} disabled={selectedIds.length === 0 || loading}>
          {loading ? 'Chargement...' : 'Afficher'}
        </button>
      </div>

      <div className="cards">
        {widgets.map((w) => (
          <Card
            key={w.id}
            widget={w}
            refreshing={refreshingId === w.sourceId}
            onRefresh={() => handleRefresh(w.sourceId)}
          />
        ))}
      </div>
    </div>
  );
}

export default App;

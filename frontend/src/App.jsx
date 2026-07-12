import { useEffect, useState } from 'react';
import DomainTabs from './components/DomainTabs';
import ResourcePicker from './components/ResourcePicker';
import ExtractorPicker from './components/ExtractorPicker';
import Card from './components/Card';
import { fetchDomains, fetchResources, fetchExtractors, fetchWidgets } from './api';
import './App.css';

function App() {
  const [domains, setDomains] = useState([]);
  const [domainId, setDomainId] = useState(null);

  const [resources, setResources] = useState([]);
  const [groups, setGroups] = useState([]);
  const [selectedResourceIds, setSelectedResourceIds] = useState([]);

  const [extractors, setExtractors] = useState([]);
  const [selectedExtractorIds, setSelectedExtractorIds] = useState([]);

  const [widgets, setWidgets] = useState([]);
  const [loading, setLoading] = useState(false);
  const [refreshingKey, setRefreshingKey] = useState(null);

  useEffect(() => {
    fetchDomains().then((list) => {
      setDomains(list);
      if (list.length > 0) setDomainId(list[0].id);
    });
  }, []);

  useEffect(() => {
    if (!domainId) return;
    setSelectedResourceIds([]);
    setExtractors([]);
    setSelectedExtractorIds([]);
    setWidgets([]);
    fetchResources(domainId).then(({ resources, groups }) => {
      setResources(resources);
      setGroups(groups);
    });
  }, [domainId]);

  useEffect(() => {
    if (!domainId) return;
    if (selectedResourceIds.length === 0) {
      setExtractors([]);
      setSelectedExtractorIds([]);
      return;
    }
    fetchExtractors(domainId, selectedResourceIds).then((list) => {
      setExtractors(list);
      setSelectedExtractorIds((prev) => prev.filter((id) => list.some((e) => e.id === id)));
    });
  }, [domainId, selectedResourceIds]);

  function toggleResource(id) {
    setSelectedResourceIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    );
  }

  function toggleGroup(group) {
    const allSelected = group.resourceIds.every((id) => selectedResourceIds.includes(id));
    setSelectedResourceIds((prev) =>
      allSelected
        ? prev.filter((id) => !group.resourceIds.includes(id))
        : [...new Set([...prev, ...group.resourceIds])]
    );
  }

  function toggleExtractor(id) {
    setSelectedExtractorIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    );
  }

  async function handleShow() {
    setLoading(true);
    try {
      setWidgets(await fetchWidgets(domainId, selectedResourceIds, selectedExtractorIds));
    } finally {
      setLoading(false);
    }
  }

  async function handleRefresh(resourceId, extractorId) {
    const key = `${resourceId}-${extractorId}`;
    setRefreshingKey(key);
    try {
      const refreshed = await fetchWidgets(domainId, [resourceId], [extractorId]);
      setWidgets((prev) => [
        ...prev.filter((w) => !(w.resourceId === resourceId && w.extractorId === extractorId)),
        ...refreshed,
      ]);
    } finally {
      setRefreshingKey(null);
    }
  }

  return (
    <div className="app">
      <h1>SimpleDash</h1>

      <DomainTabs domains={domains} activeId={domainId} onSelect={setDomainId} />

      <ResourcePicker
        resources={resources}
        groups={groups}
        selectedIds={selectedResourceIds}
        onToggleResource={toggleResource}
        onToggleGroup={toggleGroup}
      />

      <ExtractorPicker
        extractors={extractors}
        selectedIds={selectedExtractorIds}
        onToggle={toggleExtractor}
      />

      <button
        className="show-btn"
        onClick={handleShow}
        disabled={selectedResourceIds.length === 0 || selectedExtractorIds.length === 0 || loading}
      >
        {loading ? 'Chargement...' : 'Afficher'}
      </button>

      <div className="cards">
        {widgets.map((w) => (
          <Card
            key={w.id}
            widget={w}
            refreshing={refreshingKey === `${w.resourceId}-${w.extractorId}`}
            onRefresh={() => handleRefresh(w.resourceId, w.extractorId)}
          />
        ))}
      </div>
    </div>
  );
}

export default App;

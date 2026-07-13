import { useEffect, useState } from 'react';
import DomainTabs from './components/DomainTabs';
import ResourcePicker from './components/ResourcePicker';
import ExtractorPicker from './components/ExtractorPicker';
import Card from './components/Card';
import { fetchDomains, fetchResources, fetchExtractors, fetchWidgets } from './api';
import './App.css';

function widgetKey(w) {
  return `${w.resourceId}-${w.extractorId}`;
}

// Fusionne des widgets nouvellement récupérés dans l'existant : une
// combinaison ressource+extracteur déjà affichée est remplacée en place,
// une nouvelle combinaison vient s'ajouter — jamais de remise à zéro.
function mergeWidgets(existing, incoming) {
  const incomingKeys = new Set(incoming.map(widgetKey));
  return [...existing.filter((w) => !incomingKeys.has(widgetKey(w))), ...incoming];
}

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
  const [panelCollapsed, setPanelCollapsed] = useState(false);

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

  async function handleAdd() {
    setLoading(true);
    try {
      const newWidgets = await fetchWidgets(domainId, selectedResourceIds, selectedExtractorIds);
      setWidgets((prev) => mergeWidgets(prev, newWidgets));
    } finally {
      setLoading(false);
    }
  }

  async function handleRefresh(resourceId, extractorId) {
    const key = `${resourceId}-${extractorId}`;
    setRefreshingKey(key);
    try {
      const refreshed = await fetchWidgets(domainId, [resourceId], [extractorId]);
      setWidgets((prev) => mergeWidgets(prev, refreshed));
    } finally {
      setRefreshingKey(null);
    }
  }

  function handleRemove(widgetId) {
    setWidgets((prev) => prev.filter((w) => w.id !== widgetId));
  }

  function handleClearAll() {
    setWidgets([]);
  }

  return (
    <div className="app">
      <h1>SimpleDash</h1>

      <div className="selection-panel">
        <button className="panel-toggle" onClick={() => setPanelCollapsed((c) => !c)}>
          {panelCollapsed ? 'Choisir des infos ▸' : 'Réduire ▾'}
        </button>

        {!panelCollapsed && (
          <>
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
              onClick={handleAdd}
              disabled={selectedResourceIds.length === 0 || selectedExtractorIds.length === 0 || loading}
            >
              {loading ? 'Chargement...' : 'Ajouter'}
            </button>
          </>
        )}
      </div>

      {widgets.length > 0 && (
        <div className="cards-toolbar">
          <button className="clear-btn" onClick={handleClearAll}>
            Tout effacer
          </button>
        </div>
      )}

      <div className="cards">
        {widgets.map((w) => (
          <Card
            key={w.id}
            widget={w}
            refreshing={refreshingKey === `${w.resourceId}-${w.extractorId}`}
            onRefresh={() => handleRefresh(w.resourceId, w.extractorId)}
            onRemove={() => handleRemove(w.id)}
          />
        ))}
      </div>
    </div>
  );
}

export default App;

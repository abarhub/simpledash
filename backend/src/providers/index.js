import datetime from './datetime.js';
import system from './system.js';
import joke from './joke.js';

// Chaque provider = une source d'info sélectionnable côté front.
// Un provider peut retourner plusieurs widgets (ex: "system" -> mémoire/CPU/uptime).
const providers = [datetime, system, joke];

export function listProviders() {
  return providers.map(({ id, name, description }) => ({ id, name, description }));
}

export async function getWidgets(ids) {
  const selected = providers.filter((p) => ids.includes(p.id));

  const results = await Promise.all(
    selected.map(async (provider) => {
      try {
        const widgets = await provider.fetch();
        return widgets.map((w) => ({ ...w, sourceId: provider.id, sourceName: provider.name }));
      } catch (err) {
        return [
          {
            id: `${provider.id}-error`,
            sourceId: provider.id,
            sourceName: provider.name,
            title: provider.name,
            error: err.message,
          },
        ];
      }
    })
  );

  return results.flat();
}

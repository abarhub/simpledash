import test from 'node:test';
import assert from 'node:assert/strict';

process.env.JIRA_BASE_URL = 'https://jira.example.local';
process.env.JIRA_TOKEN = 'fake-token';

const { default: issues } = await import('./issues.js');

function fakeResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() {
      return body;
    },
  };
}

async function withMockFetch(mockFetch, run) {
  const original = globalThis.fetch;
  globalThis.fetch = mockFetch;
  try {
    await run();
  } finally {
    globalThis.fetch = original;
  }
}

test('mappe les tickets en un widget table et construit la bonne requête', async () => {
  let capturedUrl;
  let capturedHeaders;

  await withMockFetch(
    async (url, options) => {
      capturedUrl = url;
      capturedHeaders = options.headers;
      return fakeResponse(200, {
        issues: [
          {
            key: 'PROJ-123',
            fields: {
              summary: 'Corriger le bug de login',
              status: { name: 'In Progress' },
              updated: '2026-07-10T14:32:00.000+0000',
            },
          },
          {
            key: 'PROJ-124',
            fields: {
              summary: 'Améliorer les perfs',
              status: { name: 'To Do' },
              updated: '2026-07-09T09:00:00.000+0000',
            },
          },
        ],
      });
    },
    async () => {
      const widgets = await issues.fetch({ jql: 'assignee = currentUser()', maxResults: 5 });

      assert.equal(widgets.length, 1);
      const [widget] = widgets;
      assert.equal(widget.title, 'Tickets');
      assert.deepEqual(widget.table.columns, ['Titre', 'ID', 'Statut', 'Mise à jour']);
      assert.equal(widget.table.rows.length, 2);
      assert.deepEqual(widget.table.rows[0].cells, [
        'Corriger le bug de login',
        'PROJ-123',
        'In Progress',
        new Date('2026-07-10T14:32:00.000+0000').toLocaleDateString('fr-FR'),
      ]);
      assert.equal(widget.table.rows[0].url, 'https://jira.example.local/browse/PROJ-123');

      assert.equal(capturedHeaders.Authorization, 'Bearer fake-token');
      assert.equal(capturedUrl.pathname, '/rest/api/2/search');
      assert.equal(capturedUrl.searchParams.get('jql'), 'assignee = currentUser()');
      assert.equal(capturedUrl.searchParams.get('maxResults'), '5');
    }
  );
});

test('maxResults vaut 5 par défaut si non précisé sur la ressource', async () => {
  let capturedUrl;

  await withMockFetch(
    async (url) => {
      capturedUrl = url;
      return fakeResponse(200, { issues: [] });
    },
    async () => {
      await issues.fetch({ jql: 'foo' });
      assert.equal(capturedUrl.searchParams.get('maxResults'), '5');
    }
  );
});

test('retombe sur un widget data si aucun ticket ne correspond', async () => {
  await withMockFetch(
    async () => fakeResponse(200, { issues: [] }),
    async () => {
      const widgets = await issues.fetch({ jql: 'foo' });
      assert.equal(widgets.length, 1);
      assert.equal(widgets[0].table, undefined);
      assert.deepEqual(widgets[0].data, { Tickets: 'aucun ticket trouvé' });
    }
  );
});

test('lève une erreur explicite si la réponse HTTP est en échec', async () => {
  await withMockFetch(
    async () => fakeResponse(401, {}),
    async () => {
      await assert.rejects(() => issues.fetch({ jql: 'foo' }), /Erreur Jira: 401/);
    }
  );
});

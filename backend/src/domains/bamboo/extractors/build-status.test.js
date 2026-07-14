import test from 'node:test';
import assert from 'node:assert/strict';

process.env.BAMBOO_BASE_URL = 'https://bamboo.example.local';
process.env.BAMBOO_TOKEN = 'fake-token';

const { default: buildStatus } = await import('./build-status.js');

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

function planResultsHandler(map) {
  return async (url, options) => {
    const handler = map[url.pathname];
    if (!handler) throw new Error(`URL inattendue: ${url}`);
    return handler(url, options);
  };
}

test('mappe le plan principal et ses branches en un widget table', async () => {
  let capturedHeaders;

  await withMockFetch(
    planResultsHandler({
      '/rest/api/latest/result/PROJ-PLAN.json': async (url, options) => {
        capturedHeaders = options.headers;
        return fakeResponse(200, {
          results: {
            result: [
              {
                state: 'Successful',
                lifeCycleState: 'Finished',
                buildStartedTime: '2026-07-10T10:00:00.000+02:00',
                vcsRevisionKey: 'abcdef1234567890',
              },
            ],
          },
        });
      },
      '/rest/api/latest/plan/PROJ-PLAN.json': async () =>
        fakeResponse(200, {
          branches: {
            branch: [
              { key: 'PROJ-PLAN-BR1', shortName: 'feature-x' },
              { key: 'PROJ-PLAN-BR2', shortName: 'feature-y' },
            ],
          },
        }),
      '/rest/api/latest/result/PROJ-PLAN-BR1.json': async () =>
        fakeResponse(200, {
          results: {
            result: [
              {
                state: 'Failed',
                lifeCycleState: 'Finished',
                buildStartedTime: '2026-07-09T09:00:00.000+02:00',
                vcsRevisionKey: '1111111111111111',
              },
            ],
          },
        }),
      '/rest/api/latest/result/PROJ-PLAN-BR2.json': async () => fakeResponse(200, { results: { result: [] } }),
    }),
    async () => {
      const widgets = await buildStatus.fetch({ name: 'Mon plan', planKey: 'PROJ-PLAN', maxBranches: 5 });

      assert.equal(widgets.length, 1);
      const [widget] = widgets;
      assert.equal(widget.title, 'Builds');
      assert.deepEqual(widget.table.columns, ['Branche', 'Statut', 'Résultat', 'Date', 'Commit']);
      assert.equal(widget.table.rows.length, 3);

      assert.deepEqual(widget.table.rows[0].cells, [
        'Mon plan',
        'Finished',
        'OK',
        new Date('2026-07-10T10:00:00.000+02:00').toLocaleString('fr-FR'),
        'abcdef12',
      ]);
      assert.equal(widget.table.rows[0].url, 'https://bamboo.example.local/browse/PROJ-PLAN');

      assert.deepEqual(widget.table.rows[1].cells, [
        'feature-x',
        'Finished',
        'Erreur',
        new Date('2026-07-09T09:00:00.000+02:00').toLocaleString('fr-FR'),
        '11111111',
      ]);
      assert.equal(widget.table.rows[1].url, 'https://bamboo.example.local/browse/PROJ-PLAN-BR1');

      // pas encore de build sur cette branche : repli propre sur "?"
      assert.deepEqual(widget.table.rows[2].cells, ['feature-y', '?', '?', '?', '?']);

      assert.equal(capturedHeaders.Authorization, 'Bearer fake-token');
    }
  );
});

test('limite le nombre de branches interrogées à maxBranches', async () => {
  const allBranches = Array.from({ length: 7 }, (_, i) => ({
    key: `PROJ-PLAN-BR${i}`,
    shortName: `branch-${i}`,
  }));

  await withMockFetch(
    planResultsHandler({
      '/rest/api/latest/result/PROJ-PLAN.json': async () => fakeResponse(200, { results: { result: [] } }),
      '/rest/api/latest/plan/PROJ-PLAN.json': async () => fakeResponse(200, { branches: { branch: allBranches } }),
      // seules les 2 premières branches doivent être interrogées
      '/rest/api/latest/result/PROJ-PLAN-BR0.json': async () => fakeResponse(200, { results: { result: [] } }),
      '/rest/api/latest/result/PROJ-PLAN-BR1.json': async () => fakeResponse(200, { results: { result: [] } }),
    }),
    async () => {
      const widgets = await buildStatus.fetch({ name: 'Mon plan', planKey: 'PROJ-PLAN', maxBranches: 2 });
      assert.equal(widgets[0].table.rows.length, 3); // plan + 2 branches
    }
  );
});

test('maxBranches vaut 5 par défaut si non précisé', async () => {
  const allBranches = Array.from({ length: 6 }, (_, i) => ({
    key: `PROJ-PLAN-BR${i}`,
    shortName: `branch-${i}`,
  }));

  await withMockFetch(
    planResultsHandler({
      '/rest/api/latest/result/PROJ-PLAN.json': async () => fakeResponse(200, { results: { result: [] } }),
      '/rest/api/latest/plan/PROJ-PLAN.json': async () => fakeResponse(200, { branches: { branch: allBranches } }),
      '/rest/api/latest/result/PROJ-PLAN-BR0.json': async () => fakeResponse(200, { results: { result: [] } }),
      '/rest/api/latest/result/PROJ-PLAN-BR1.json': async () => fakeResponse(200, { results: { result: [] } }),
      '/rest/api/latest/result/PROJ-PLAN-BR2.json': async () => fakeResponse(200, { results: { result: [] } }),
      '/rest/api/latest/result/PROJ-PLAN-BR3.json': async () => fakeResponse(200, { results: { result: [] } }),
      '/rest/api/latest/result/PROJ-PLAN-BR4.json': async () => fakeResponse(200, { results: { result: [] } }),
    }),
    async () => {
      const widgets = await buildStatus.fetch({ name: 'Mon plan', planKey: 'PROJ-PLAN' });
      assert.equal(widgets[0].table.rows.length, 6); // plan + 5 branches
    }
  );
});

test('lève une erreur explicite si la réponse HTTP est en échec', async () => {
  await withMockFetch(
    async () => fakeResponse(401, {}),
    async () => {
      await assert.rejects(
        () => buildStatus.fetch({ name: 'Mon plan', planKey: 'PROJ-PLAN' }),
        /Erreur Bamboo: 401/
      );
    }
  );
});

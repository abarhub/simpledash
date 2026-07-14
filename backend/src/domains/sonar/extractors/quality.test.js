import test from 'node:test';
import assert from 'node:assert/strict';

process.env.SONAR_BASE_URL = 'https://sonar.example.local';
process.env.SONAR_TOKEN = 'fake-token';

const { default: quality } = await import('./quality.js');

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

test('mappe les métriques en widget data, avec conversion des notes en lettres', async () => {
  const capturedHeaders = [];

  await withMockFetch(
    async (url, options) => {
      capturedHeaders.push(options.headers);
      if (url.pathname === '/api/measures/component') {
        assert.equal(url.searchParams.get('component'), 'my-project');
        return fakeResponse(200, {
          component: {
            measures: [
              { metric: 'reliability_rating', value: '1.0' },
              { metric: 'security_rating', value: '2.0' },
              { metric: 'sqale_rating', value: '3.0' },
              { metric: 'coverage', value: '78.3' },
              { metric: 'duplicated_lines_density', value: '4.2' },
              { metric: 'alert_status', value: 'OK' },
            ],
          },
        });
      }
      if (url.pathname === '/api/project_analyses/search') {
        return fakeResponse(200, {
          analyses: [{ date: '2026-07-10T10:00:00+0200', revision: 'abcdef1234567890' }],
        });
      }
      throw new Error(`URL inattendue: ${url}`);
    },
    async () => {
      const widgets = await quality.fetch({ projectKey: 'my-project' });

      assert.equal(widgets.length, 1);
      const [widget] = widgets;
      assert.equal(widget.title, 'Qualité');
      assert.deepEqual(widget.data, {
        Fiabilité: 'A',
        Sécurité: 'B',
        Maintenabilité: 'C',
        'Quality Gate': 'OK',
        Couverture: '78.3%',
        Duplication: '4.2%',
        Commit: 'abcdef12',
        Date: new Date('2026-07-10T10:00:00+0200').toLocaleString('fr-FR'),
      });
      assert.equal(widget.url, 'https://sonar.example.local/dashboard?id=my-project');

      const expectedAuth = `Basic ${Buffer.from('fake-token:').toString('base64')}`;
      for (const headers of capturedHeaders) {
        assert.equal(headers.Authorization, expectedAuth);
      }
    }
  );
});

test('gère un quality gate en échec et une révision absente', async () => {
  await withMockFetch(
    async (url) => {
      if (url.pathname === '/api/measures/component') {
        return fakeResponse(200, {
          component: {
            measures: [
              { metric: 'reliability_rating', value: '1.0' },
              { metric: 'security_rating', value: '1.0' },
              { metric: 'sqale_rating', value: '1.0' },
              { metric: 'coverage', value: '50.0' },
              { metric: 'duplicated_lines_density', value: '0.0' },
              { metric: 'alert_status', value: 'ERROR' },
            ],
          },
        });
      }
      return fakeResponse(200, { analyses: [{ date: '2026-07-10T10:00:00+0200' }] });
    },
    async () => {
      const [widget] = await quality.fetch({ projectKey: 'my-project' });
      assert.equal(widget.data['Quality Gate'], 'ERROR');
      assert.equal(widget.data.Commit, '?');
    }
  );
});

test('lève une erreur explicite si la réponse HTTP est en échec', async () => {
  await withMockFetch(
    async () => fakeResponse(403, {}),
    async () => {
      await assert.rejects(() => quality.fetch({ projectKey: 'my-project' }), /Erreur SonarQube: 403/);
    }
  );
});

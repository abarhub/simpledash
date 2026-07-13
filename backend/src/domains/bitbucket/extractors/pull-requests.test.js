import test from 'node:test';
import assert from 'node:assert/strict';

process.env.BITBUCKET_BASE_URL = 'https://bitbucket.example.local';
process.env.BITBUCKET_TOKEN = 'fake-token';
process.env.BITBUCKET_USERNAME = 'jdoe';

const { default: pullRequests } = await import('./pull-requests.js');

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

test('mappe les PR en un widget table, avec "à moi" et "validée par moi"', async () => {
  let capturedUrl;
  let capturedHeaders;

  await withMockFetch(
    async (url, options) => {
      capturedUrl = url;
      capturedHeaders = options.headers;
      return fakeResponse(200, {
        values: [
          {
            id: 42,
            title: 'Add feature X',
            createdDate: 1752144000000,
            author: { user: { slug: 'jdoe', displayName: 'John Doe' } },
            reviewers: [{ user: { slug: 'asmith', displayName: 'Alice Smith' }, approved: true }],
            links: {
              self: [{ href: 'https://bitbucket.example.local/projects/PROJ/repos/repo/pull-requests/42' }],
            },
          },
          {
            id: 43,
            title: 'Fix bug Y',
            createdDate: 1752057600000,
            author: { user: { slug: 'asmith', displayName: 'Alice Smith' } },
            reviewers: [{ user: { slug: 'jdoe', displayName: 'John Doe' }, approved: false }],
            links: {
              self: [{ href: 'https://bitbucket.example.local/projects/PROJ/repos/repo/pull-requests/43' }],
            },
          },
        ],
      });
    },
    async () => {
      const widgets = await pullRequests.fetch({ project: 'PROJ', repo: 'repo' });

      assert.equal(widgets.length, 1);
      const [widget] = widgets;
      assert.deepEqual(widget.table.columns, ['Titre', 'Auteur', 'À moi', 'Validée', 'Date']);
      assert.equal(widget.table.rows.length, 2);

      assert.deepEqual(widget.table.rows[0].cells, [
        'Add feature X',
        'John Doe',
        'Oui',
        'Non',
        new Date(1752144000000).toLocaleDateString('fr-FR'),
      ]);
      assert.equal(
        widget.table.rows[0].url,
        'https://bitbucket.example.local/projects/PROJ/repos/repo/pull-requests/42'
      );

      assert.deepEqual(widget.table.rows[1].cells, [
        'Fix bug Y',
        'Alice Smith',
        'Non',
        'Non',
        new Date(1752057600000).toLocaleDateString('fr-FR'),
      ]);

      assert.equal(capturedHeaders.Authorization, 'Bearer fake-token');
      assert.equal(capturedUrl.pathname, '/rest/api/1.0/projects/PROJ/repos/repo/pull-requests');
      assert.equal(capturedUrl.searchParams.get('state'), 'OPEN');
    }
  );
});

test('"Validée" passe à Oui quand je suis reviewer et que j\'ai approuvé', async () => {
  await withMockFetch(
    async () =>
      fakeResponse(200, {
        values: [
          {
            id: 1,
            title: 'PR',
            createdDate: 1752144000000,
            author: { user: { slug: 'someone-else', displayName: 'Someone' } },
            reviewers: [{ user: { slug: 'jdoe', displayName: 'John Doe' }, approved: true }],
            links: { self: [{ href: 'https://bitbucket.example.local/x' }] },
          },
        ],
      }),
    async () => {
      const [widget] = await pullRequests.fetch({ project: 'PROJ', repo: 'repo' });
      const [, , , validee] = widget.table.rows[0].cells;
      assert.equal(validee, 'Oui');
    }
  );
});

test('retombe sur un widget data si aucune PR ouverte', async () => {
  await withMockFetch(
    async () => fakeResponse(200, { values: [] }),
    async () => {
      const widgets = await pullRequests.fetch({ project: 'PROJ', repo: 'repo' });
      assert.equal(widgets.length, 1);
      assert.equal(widgets[0].table, undefined);
      assert.deepEqual(widgets[0].data, { 'Pull requests': 'aucune PR ouverte' });
    }
  );
});

test('lève une erreur explicite si la réponse HTTP est en échec', async () => {
  await withMockFetch(
    async () => fakeResponse(403, {}),
    async () => {
      await assert.rejects(
        () => pullRequests.fetch({ project: 'PROJ', repo: 'repo' }),
        /Erreur Bitbucket: 403/
      );
    }
  );
});

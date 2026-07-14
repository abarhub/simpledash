import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);

async function git(args, cwd) {
  const { stdout } = await execFileAsync('git', args, { cwd });
  return stdout.trim();
}

// git@github.com:owner/repo.git -> https://github.com/owner/repo
// https://github.com/owner/repo.git -> https://github.com/owner/repo
function toWebUrl(remoteUrl) {
  if (!remoteUrl) return null;
  const sshMatch = remoteUrl.match(/^git@([^:]+):(.+?)(\.git)?$/);
  if (sshMatch) {
    return `https://${sshMatch[1]}/${sshMatch[2]}`;
  }
  return remoteUrl.replace(/\.git$/, '');
}

export default {
  id: 'git-info',
  name: 'Git',
  description: 'Dernier commit, branche courante, statut du dépôt, avance/retard sur le remote',
  compatibleTypes: ['git'],

  async fetch(resource) {
    const [shortHash, commitDateRaw, commitMessage, branch, status, containingBranchesRaw, remoteUrl] =
      await Promise.all([
        git(['rev-parse', '--short', 'HEAD'], resource.path),
        git(['log', '-1', '--format=%cI'], resource.path),
        git(['log', '-1', '--format=%s'], resource.path),
        git(['branch', '--show-current'], resource.path),
        git(['status', '--porcelain'], resource.path),
        git(['branch', '--contains', 'HEAD', '--format=%(refname:short)'], resource.path),
        git(['remote', 'get-url', 'origin'], resource.path).catch(() => ''),
      ]);

    let vsRemote = 'pas de remote suivi';
    try {
      const counts = await git(['rev-list', '--left-right', '--count', 'HEAD...@{u}'], resource.path);
      const [ahead, behind] = counts.split(/\s+/);
      vsRemote = `${ahead} en avance, ${behind} en retard`;
    } catch {
      // pas de branche upstream configurée pour la branche courante
    }

    const branches = containingBranchesRaw.split('\n').filter(Boolean);

    return [
      {
        id: 'git',
        title: 'Git',
        url: toWebUrl(remoteUrl) ?? undefined,
        data: {
          'Dernier commit': shortHash,
          Message: commitMessage,
          Date: commitDateRaw ? new Date(commitDateRaw).toLocaleString('fr-FR') : '?',
          Branche: branch || '(detached)',
          'Modifs non commitées': status ? 'Oui' : 'Non',
          'Vs remote': vsRemote,
          'Branches contenant ce commit': branches.join(', ') || '?',
        },
      },
    ];
  },
};

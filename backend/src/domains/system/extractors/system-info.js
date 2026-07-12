import os from 'node:os';

function formatBytes(bytes) {
  return `${(bytes / 1024 ** 3).toFixed(2)} Go`;
}

function formatDuration(seconds) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  return `${h}h ${m}min`;
}

export default {
  id: 'system-info',
  name: 'Infos système',
  description: 'Mémoire, CPU et uptime (une ressource => plusieurs cards)',
  compatibleTypes: ['local'],

  async fetch() {
    const totalMem = os.totalmem();
    const freeMem = os.freemem();
    const cpus = os.cpus();

    return [
      {
        id: 'memory',
        title: 'Mémoire',
        data: {
          Totale: formatBytes(totalMem),
          Utilisée: formatBytes(totalMem - freeMem),
          Libre: formatBytes(freeMem),
        },
      },
      {
        id: 'cpu',
        title: 'CPU',
        data: {
          Modèle: cpus[0]?.model ?? 'inconnu',
          Cœurs: cpus.length,
          'Charge (1 min)': os.loadavg()[0].toFixed(2),
        },
      },
      {
        id: 'uptime',
        title: 'Uptime',
        data: {
          Système: formatDuration(os.uptime()),
          Processus: formatDuration(process.uptime()),
        },
      },
    ];
  },
};

export interface SierraModule {
  readonly title: string;
  readonly path: string;
  readonly assetPath: string;
  readonly blurb: string;
}

export interface SierraSection {
  readonly label: string;
  readonly modules: readonly SierraModule[];
}

function mod(title: string, path: string, blurb: string): SierraModule {
  return { title, path, assetPath: `/work-projects/sierra/${path}`, blurb };
}

export const SIERRA_CATALOG: readonly SierraSection[] = [
  {
    label: 'Start',
    modules: [
      mod('Catalog', 'index.md', 'Module map for the Sierra learning library'),
      mod('Sources', 'sources.md', 'Where the seed briefings came from'),
    ],
  },
  {
    label: 'Product',
    modules: [
      mod('Overview', 'product/overview.md', 'Company, category, what “your AI agent” means'),
      mod('Pricing and outcomes', 'product/pricing-and-outcomes.md', 'Outcome-based commercial model'),
      mod('Channels', 'product/channels.md', 'Chat, voice, email, SMS, WhatsApp, ChatGPT, contact center'),
    ],
  },
  {
    label: 'Architecture',
    modules: [
      mod('Agent OS', 'architecture/agent-os.md', 'Runtime: one agent, many surfaces'),
      mod('Agent Data Platform', 'architecture/agent-data-platform.md', 'Memory, unified customer context, decisioning'),
      mod(
        'Models and constellation',
        'architecture/models-and-constellation.md',
        'Multi-model stack, failover, supervisor layers',
      ),
    ],
  },
  {
    label: 'Agents',
    modules: [
      mod('Journeys and skills', 'agents/journeys-and-skills.md', 'Goals, guardrails, composable skills, tuning'),
      mod('Agent SDK', 'agents/agent-sdk.md', 'Journeys-as-code, traces, developer workflow'),
      mod('Ghostwriter', 'agents/ghostwriter.md', 'Agent that builds, tests, and proposes agent changes'),
    ],
  },
  {
    label: 'Studio',
    modules: [
      mod('Agent Studio', 'studio/agent-studio.md', 'No-code authoring, Workspaces, traces'),
      mod('Knowledge', 'studio/knowledge.md', 'Help Center, gaps, Expert Answers'),
      mod('Branding and controls', 'studio/branding-and-controls.md', 'Voice, look, dynamic updates, multimodality'),
    ],
  },
  {
    label: 'Integrations',
    modules: [
      mod('Systems and actions', 'integrations/systems-and-actions.md', '40+ connectors, custom integrations, real actions'),
      mod(
        'Contact center and handoff',
        'integrations/contact-center-and-handoff.md',
        'Escalation, summaries, Live Assist',
      ),
    ],
  },
  {
    label: 'Operations',
    modules: [
      mod('Insights and monitoring', 'operations/insights-and-monitoring.md', 'Metrics, Explorer, tagging, alerts'),
      mod('Testing and release', 'operations/testing-and-release.md', 'Simulations, Voice Sims, regression, workspaces'),
      mod('Trust and compliance', 'operations/trust-and-compliance.md', 'SOC 2, HIPAA, PCI isolation, data rules'),
    ],
  },
  {
    label: 'Journal',
    modules: [
      mod('How to journal', 'journal/README.md', 'Dated personal notes, one file per observation'),
      mod('2026-09-29 library started', 'journal/2026-09-29-library-started.md', 'First journal entry'),
    ],
  },
];

export function findSierraModule(path: string): SierraModule | undefined {
  const normalized = normalizeSierraPath(path);
  for (const section of SIERRA_CATALOG) {
    for (const module of section.modules) {
      if (normalizeSierraPath(module.path) === normalized) {
        return module;
      }
    }
  }
  return undefined;
}

export function resolveSierraHref(fromPath: string, href: string): string | null {
  const raw = href.trim();
  if (!raw || raw.startsWith('#') || /^[a-z][a-z0-9+.-]*:/i.test(raw)) {
    return null;
  }
  const noHash = raw.split('#')[0] ?? raw;
  if (!noHash) {
    return null;
  }
  const fromDir = fromPath.includes('/') ? fromPath.slice(0, fromPath.lastIndexOf('/')) : '';
  const joined = joinSierraPath(fromDir, noHash);
  if (joined.endsWith('/')) {
    return `${joined}README.md`;
  }
  if (!joined.endsWith('.md')) {
    return `${joined}.md`;
  }
  return joined;
}

function joinSierraPath(fromDir: string, rel: string): string {
  const parts = (fromDir ? `${fromDir}/${rel}` : rel).split('/');
  const out: string[] = [];
  for (const part of parts) {
    if (!part || part === '.') {
      continue;
    }
    if (part === '..') {
      out.pop();
      continue;
    }
    out.push(part);
  }
  return out.join('/');
}

function normalizeSierraPath(path: string): string {
  return path.replace(/^\.\//, '').replace(/\/+/g, '/');
}
